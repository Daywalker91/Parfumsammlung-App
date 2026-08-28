'use strict';

const crypto = require('crypto');
const express = require('express');
const basicAuth = require('basic-auth');
const rateLimit = require('express-rate-limit');
const db = require('./db');
const { verschluesseln } = require('./crypto');

const router = express.Router();

// Brute-Force-Schutz fürs Login — /admin hängt am selben öffentlichen Host
// wie /v1/* (siehe Plan). 5 Fehlversuche pro IP -> 15 Minuten Sperre.
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  skipSuccessfulRequests: true,
});

function authMiddleware(req, res, next) {
  const eingabe = basicAuth(req);
  const nutzer = process.env.ADMIN_USER;
  const passwort = process.env.ADMIN_PASSWORD;
  if (!eingabe || eingabe.name !== nutzer || eingabe.pass !== passwort) {
    res.set('WWW-Authenticate', 'Basic realm="Gateway Admin"');
    return res.status(401).send('Zugriff verweigert.');
  }
  next();
}

router.use(loginLimiter, authMiddleware, express.urlencoded({ extended: false }));

// Express 4 fängt Rejections aus async-Handlern NICHT automatisch ab — ohne
// diesen Wrapper würde z. B. ein kurzer DB-Ausfall den ganzen Prozess crashen
// (unhandled rejection) und /v1/messages gleich mit reißen.
function asyncHandler(fn) {
  return (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
}

function euro(microcent) {
  return (microcent / 100_000_000).toFixed(2).replace('.', ',');
}

/**
 * Parst ein €-Tageslimit-Formularfeld. Leer -> null (unlimitiert, siehe Plan
 * "Was passiert bei 0,0? Ist das unlimitiert?"). Akzeptiert Komma als
 * Dezimaltrenner (deutsche Eingabe). Gibt bei ungültiger (nicht-leerer)
 * Eingabe undefined zurück, statt still einen Default zu setzen -- 0 selbst
 * ist ein gültiger Wert (bedeutet "sofort gesperrt", nicht unlimitiert).
 */
function parseTageslimitMicrocent(raw) {
  const text = (raw || '').trim();
  if (text === '') return null;
  const euroWert = Number.parseFloat(text.replace(',', '.'));
  if (!Number.isFinite(euroWert) || euroWert < 0) return undefined;
  return Math.round(euroWert * 100_000_000);
}

async function seite(req, res, hinweis) {
  const codes = await db.alleAccessCodes();
  const keys = await db.alleApiKeys();
  const spendenLink = await db.holeSpendenLink();

  const keyOptionen = (ausgewaehlt) =>
    ['<option value="">— Standard —</option>']
      .concat(keys.map((k) => `<option value="${k.id}" ${k.id === ausgewaehlt ? 'selected' : ''}>${escapeHtml(k.label)}</option>`))
      .join('');

  const codeZeilen = codes
    .map(
      (c) => `
    <tr>
      <td><code>${escapeHtml(c.code)}</code></td>
      <td>${escapeHtml(c.label || '')}</td>
      <td>${c.aktiv ? 'aktiv' : 'gesperrt'}</td>
      <td>
        <form method="post" action="/admin/codes/${c.id}/limit" style="display:inline">
          <input name="tageslimitEuro" placeholder="leer = unlimitiert" size="8"
                 value="${c.tageslimit_microcent === null ? '' : euro(c.tageslimit_microcent)}">
          <button type="submit">Setzen</button>
        </form>
        ${c.tageslimit_microcent === null ? '<br><small>unlimitiert</small>' : ''}
      </td>
      <td>
        <form method="post" action="/admin/codes/${c.id}/key" style="display:inline">
          <select name="apiKeyId" onchange="this.form.submit()">${keyOptionen(c.api_key_id)}</select>
        </form>
      </td>
      <td>
        <form method="post" action="/admin/codes/${c.id}/toggle" style="display:inline">
          <button type="submit">${c.aktiv ? 'Sperren' : 'Aktivieren'}</button>
        </form>
        <form method="post" action="/admin/codes/${c.id}/delete" style="display:inline"
              onsubmit="return confirm('Diesen Zugangs-Code endgültig löschen? Damit wird auch dessen Nutzungs-Historie entfernt.');">
          <button type="submit">Löschen</button>
        </form>
      </td>
    </tr>`,
    )
    .join('');

  const keyZeilen = keys
    .map(
      (k) => `
    <tr>
      <td>${escapeHtml(k.label)}</td>
      <td>${k.aktiv ? 'aktiv' : 'deaktiviert'}</td>
      <td>${k.ist_standard ? 'Standard' : ''}</td>
      <td>
        <form method="post" action="/admin/keys/${k.id}/toggle" style="display:inline">
          <button type="submit">${k.aktiv ? 'Deaktivieren' : 'Aktivieren'}</button>
        </form>
        <form method="post" action="/admin/keys/${k.id}/standard" style="display:inline">
          <button type="submit" ${k.ist_standard ? 'disabled' : ''}>Als Standard</button>
        </form>
        <form method="post" action="/admin/keys/${k.id}/delete" style="display:inline"
              onsubmit="return confirm('Diesen API-Key endgültig löschen? Codes, die ihn nutzen, fallen danach auf den Standard-Key zurück.');">
          <button type="submit">Löschen</button>
        </form>
      </td>
    </tr>`,
    )
    .join('');

  res.set('Content-Type', 'text/html; charset=utf-8').send(`<!doctype html>
<html lang="de"><head><meta charset="utf-8"><title>Gateway Admin</title>
<style>
body { font-family: system-ui, sans-serif; max-width: 900px; margin: 2rem auto; padding: 0 1rem; }
table { border-collapse: collapse; width: 100%; margin-bottom: 2rem; }
td, th { border: 1px solid #ccc; padding: 0.4rem 0.6rem; text-align: left; }
form.inline { display: inline-block; margin-bottom: 1.5rem; }
input, select { padding: 0.3rem; margin-right: 0.4rem; }
.hinweis { background: #eef; padding: 0.6rem; margin-bottom: 1rem; }
</style></head>
<body>
<h1>Lizenz-Gateway — Admin</h1>
${hinweis ? `<div class="hinweis">${escapeHtml(hinweis)}</div>` : ''}

<h2>Zugangs-Codes</h2>
<table>
<tr><th>Code</th><th>Label</th><th>Status</th><th>Tageslimit</th><th>API-Key</th><th>Aktion</th></tr>
${codeZeilen}
</table>
<form class="inline" method="post" action="/admin/codes">
  <input name="label" placeholder="Label (z. B. Bruder)">
  <input name="tageslimitEuro" placeholder="Tageslimit in € (leer = unlimitiert)" value="5.00">
  <button type="submit">Neuen Code erzeugen</button>
</form>

<h2>API-Keys</h2>
<table>
<tr><th>Label</th><th>Status</th><th>Standard</th><th>Aktion</th></tr>
${keyZeilen}
</table>
<form class="inline" method="post" action="/admin/keys">
  <input name="label" placeholder="Label (z. B. Haupt-Key)">
  <input name="key" placeholder="sk-ant-..." size="40">
  <button type="submit">Key eintragen</button>
</form>

<h2>Einstellungen</h2>
<form class="inline" method="post" action="/admin/einstellungen">
  <input name="spendenLink" placeholder="https://paypal.me/DeinName" value="${escapeHtml(spendenLink || '')}" size="40">
  <button type="submit">Spenden-Link speichern</button>
</form>
<p style="color:#666; font-size:0.9em; max-width:600px;">
  Wird per /v1/status an Apps ausgeliefert, die über einen Lizenzschlüssel laufen (nicht bei eigenem API-Key).
  Die App hängt selbst den offenen Betrag an (z. B. .../DeinName/4.20EUR) — hier nur der Basis-Link ohne Betrag eintragen.
  Leer lassen und speichern entfernt den Link wieder.
</p>

</body></html>`);
}

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

router.get('/', asyncHandler(async (req, res) => {
  await seite(req, res, null);
}));

router.post('/codes', asyncHandler(async (req, res) => {
  const label = (req.body.label || '').trim();
  let tageslimitMicrocent = parseTageslimitMicrocent(req.body.tageslimitEuro);
  if (tageslimitMicrocent === undefined) tageslimitMicrocent = 500_000_000; // ungueltige Eingabe -> Default 5,00 €, nicht unlimitiert
  const code = crypto.randomBytes(16).toString('hex'); // 128 Bit Entropie — nicht brute-forcebar.
  await db.pool.query(
    'INSERT INTO access_codes (code, label, tageslimit_microcent) VALUES (?, ?, ?)',
    [code, label || null, tageslimitMicrocent],
  );
  await seite(req, res, `Neuer Code erzeugt: ${code} — jetzt persönlich weitergeben.`);
}));

router.post('/codes/:id/toggle', asyncHandler(async (req, res) => {
  await db.pool.query('UPDATE access_codes SET aktiv = NOT aktiv WHERE id = ?', [req.params.id]);
  res.redirect('/admin');
}));

router.post('/codes/:id/limit', asyncHandler(async (req, res) => {
  const tageslimitMicrocent = parseTageslimitMicrocent(req.body.tageslimitEuro);
  if (tageslimitMicrocent === undefined) return seite(req, res, 'Ungültiger Betrag — leer lassen für unlimitiert.');
  await db.pool.query('UPDATE access_codes SET tageslimit_microcent = ? WHERE id = ?', [tageslimitMicrocent, req.params.id]);
  res.redirect('/admin');
}));

router.post('/codes/:id/delete', asyncHandler(async (req, res) => {
  await db.loescheAccessCode(req.params.id);
  await seite(req, res, 'Zugangs-Code gelöscht.');
}));

router.post('/codes/:id/key', asyncHandler(async (req, res) => {
  const apiKeyId = req.body.apiKeyId ? Number.parseInt(req.body.apiKeyId, 10) : null;
  await db.pool.query('UPDATE access_codes SET api_key_id = ? WHERE id = ?', [apiKeyId, req.params.id]);
  res.redirect('/admin');
}));

router.post('/keys', asyncHandler(async (req, res) => {
  const label = (req.body.label || '').trim() || 'Unbenannt';
  const key = (req.body.key || '').trim();
  if (!key) return seite(req, res, 'Kein Key eingegeben.');
  await db.pool.query('INSERT INTO api_keys (key_verschluesselt, label) VALUES (?, ?)', [verschluesseln(key), label]);
  await seite(req, res, `Key "${label}" gespeichert (verschlüsselt).`);
}));

router.post('/keys/:id/toggle', asyncHandler(async (req, res) => {
  await db.pool.query('UPDATE api_keys SET aktiv = NOT aktiv WHERE id = ?', [req.params.id]);
  res.redirect('/admin');
}));

router.post('/keys/:id/delete', asyncHandler(async (req, res) => {
  await db.loescheApiKey(req.params.id);
  await seite(req, res, 'Key gelöscht.');
}));

router.post('/einstellungen', asyncHandler(async (req, res) => {
  const link = (req.body.spendenLink || '').trim();
  await db.setzeSpendenLink(link || null);
  await seite(req, res, link ? `Spenden-Link gespeichert: ${link}` : 'Spenden-Link entfernt.');
}));

router.post('/keys/:id/standard', asyncHandler(async (req, res) => {
  const conn = await db.pool.getConnection();
  try {
    await conn.beginTransaction();
    await conn.query('UPDATE api_keys SET ist_standard = 0');
    await conn.query('UPDATE api_keys SET ist_standard = 1, aktiv = 1 WHERE id = ?', [req.params.id]);
    await conn.commit();
  } catch (e) {
    await conn.rollback();
    throw e;
  } finally {
    conn.release();
  }
  res.redirect('/admin');
}));

// Fängt alles ab, was in den obigen Handlern durchrutscht (z. B. ein DB-
// Ausfall) — verhindert einen Prozess-Crash, zeigt stattdessen eine simple
// Fehlerseite statt des ganzen Dashboards.
router.use((err, req, res, next) => {
  console.error('Fehler im Admin-Bereich:', err);
  res.status(500).send('Interner Fehler — siehe Server-Logs (z. B. Datenbank kurz nicht erreichbar).');
});

module.exports = router;
