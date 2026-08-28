'use strict';

const express = require('express');
const db = require('./src/db');
const { entschluesseln } = require('./src/crypto');
const { kostenMicrocent } = require('./src/pricing');
const rateCapacity = require('./src/rateCapacity');
const adminRouter = require('./src/admin');

const app = express();
// Fotos sind base64 im Body (siehe ClaudeService.baueBildRequestBody), großzügiges Limit.
app.use(express.json({ limit: '15mb' }));

const PORT = process.env.PORT || 8080;
const ANTHROPIC_ENDPOINT = 'https://api.anthropic.com/v1/messages';
const ANTHROPIC_VERSION = '2023-06-01';

app.get('/healthz', (req, res) => res.status(200).send('ok'));

app.use('/admin', adminRouter);

app.get('/v1/status', async (req, res) => {
  try {
    const code = req.get('X-Access-Code');
    if (!code) return res.status(400).json({ fehler: 'Kein Zugangs-Code angegeben.' });
    const eintrag = await db.findeAccessCode(code);
    if (!eintrag || !eintrag.aktiv) return res.json({ gueltig: false, verbleibendHeute: 0 });
    const nutzung = await db.heutigeNutzung(eintrag.id);
    const verbleibendMicrocent = Math.max(0, eintrag.tageslimit_microcent - nutzung.kosten_microcent);
    // Grobe Anfragen-Schätzung nur für die Anzeige (5 Cent/Anfrage Richtwert) — das eigentliche Limit ist €-basiert.
    const verbleibendHeute = Math.floor(verbleibendMicrocent / 5_000_000);
    // Zentral über /admin gepflegter Spenden-Link (siehe Plan) — Betrag hängt
    // die App selbst an (kennt nur sie, "seit Zahlung" ist rein lokaler Zähler).
    const spendenLink = await db.holeSpendenLink();
    res.json({ gueltig: true, verbleibendHeute, ...(spendenLink ? { spendenLink } : {}) });
  } catch (e) {
    console.error('Fehler in /v1/status:', e);
    res.status(500).json({ gueltig: false, verbleibendHeute: 0 });
  }
});

app.post('/v1/messages', async (req, res) => {
  try {
    const code = req.get('X-Access-Code');
    if (!code) return res.status(403).json({ fehler: 'Kein Zugangs-Code angegeben.' });

    const eintrag = await db.findeAccessCode(code);
    if (!eintrag || !eintrag.aktiv) {
      return res.status(403).json({ fehler: 'Dieser Zugangscode ist ungültig oder gesperrt.' });
    }

    // Schritt 1: Pro-Code-Fairness — Tagesbudget in € (nicht rohe Anfragen-Zahl, siehe Plan).
    const nutzung = await db.heutigeNutzung(eintrag.id);
    if (nutzung.kosten_microcent >= eintrag.tageslimit_microcent) {
      return res.status(429).json({ fehler: 'Dieser Zugang ist für heute aufgebraucht — versuch es morgen wieder.' });
    }

    // Schritt 2: zugehörigen API-Key ermitteln (zugewiesen -> Standard -> Fehler).
    let apiKeyEintrag = null;
    if (eintrag.api_key_id) {
      const zugewiesen = await db.findeApiKey(eintrag.api_key_id);
      if (zugewiesen && zugewiesen.aktiv) apiKeyEintrag = zugewiesen;
    }
    if (!apiKeyEintrag) apiKeyEintrag = await db.findeStandardApiKey();
    if (!apiKeyEintrag) {
      console.error(`Kein gültiger API-Key für Zugangs-Code "${eintrag.label || eintrag.id}" ermittelbar.`);
      return res.status(500).json({ fehler: 'Serverseitig kein gültiger API-Key hinterlegt.' });
    }

    // Schritt 3: Anthropics eigene Kapazität für GENAU DIESEN Key, live mitgelesen (siehe rateCapacity.js).
    if (rateCapacity.istErschoepft(apiKeyEintrag.id)) {
      const retryAfter = rateCapacity.retryAfterSekunden(apiKeyEintrag.id);
      if (retryAfter) res.set('Retry-After', String(retryAfter));
      return res.status(429).json({ fehler: 'Gerade ausgelastet, bitte in Kürze erneut versuchen.' });
    }

    const echterKey = entschluesseln(apiKeyEintrag.key_verschluesselt);

    const antwort = await fetch(ANTHROPIC_ENDPOINT, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-api-key': echterKey,
        'anthropic-version': ANTHROPIC_VERSION,
      },
      body: JSON.stringify(req.body),
    });

    rateCapacity.aktualisiere(apiKeyEintrag.id, antwort.headers);

    const text = await antwort.text();
    // Zählen passiert bewusst erst NACH einer erfolgreichen Antwort (siehe Plan)
    // — ein Netzwerkfehler/5xx darf niemandem sein Tageslimit auffressen.
    if (antwort.ok) {
      try {
        const json = JSON.parse(text);
        const usage = json.usage;
        if (usage) {
          const kosten = kostenMicrocent(usage.input_tokens || 0, usage.output_tokens || 0);
          await db.nutzungHochzaehlen(eintrag.id, kosten);
        }
      } catch (e) {
        console.error('Antwort von Anthropic konnte nicht geparst werden (Nutzung wird nicht gezählt):', e);
      }
    }

    res.status(antwort.status).set('content-type', 'application/json').send(text);
  } catch (e) {
    console.error('Fehler in /v1/messages:', e);
    res.status(502).json({ fehler: 'Gateway-Fehler: ' + e.message });
  }
});

app.listen(PORT, () => console.log(`Gateway lauscht auf Port ${PORT}`));
