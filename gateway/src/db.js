'use strict';

const mysql = require('mysql2/promise');

// Verbindungspool statt Einzelverbindung — überlebt kurze Netzwerk-/DB-
// Reconnects, ohne den ganzen Pod abstürzen zu lassen (siehe Plan).
const pool = mysql.createPool({
  host: process.env.DB_HOST,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  waitForConnections: true,
  connectionLimit: 5,
  queueLimit: 0,
});

/** Heutiges Datum als YYYY-MM-DD (UTC, konsistent unabhängig von der Server-Locale). */
function heute() {
  return new Date().toISOString().slice(0, 10);
}

async function findeAccessCode(code) {
  const [rows] = await pool.query(
    'SELECT id, code, label, aktiv, tageslimit_microcent, api_key_id FROM access_codes WHERE code = ? LIMIT 1',
    [code],
  );
  return rows[0] || null;
}

async function heutigeNutzung(codeId) {
  const [rows] = await pool.query(
    'SELECT anzahl, kosten_microcent FROM nutzung WHERE code_id = ? AND datum = ?',
    [codeId, heute()],
  );
  return rows[0] || { anzahl: 0, kosten_microcent: 0 };
}

/** Wird erst NACH einer erfolgreichen (2xx) Anthropic-Antwort aufgerufen (siehe Plan). */
async function nutzungHochzaehlen(codeId, kostenMicrocent) {
  await pool.query(
    `INSERT INTO nutzung (code_id, datum, anzahl, kosten_microcent)
     VALUES (?, ?, 1, ?)
     ON DUPLICATE KEY UPDATE anzahl = anzahl + 1, kosten_microcent = kosten_microcent + VALUES(kosten_microcent)`,
    [codeId, heute(), kostenMicrocent],
  );
}

async function findeApiKey(id) {
  const [rows] = await pool.query(
    'SELECT id, key_verschluesselt, label, aktiv, ist_standard FROM api_keys WHERE id = ? LIMIT 1',
    [id],
  );
  return rows[0] || null;
}

async function findeStandardApiKey() {
  const [rows] = await pool.query(
    'SELECT id, key_verschluesselt, label, aktiv, ist_standard FROM api_keys WHERE ist_standard = 1 AND aktiv = 1 LIMIT 1',
  );
  return rows[0] || null;
}

async function alleApiKeys() {
  const [rows] = await pool.query(
    'SELECT id, label, aktiv, ist_standard, erstellt_am FROM api_keys ORDER BY erstellt_am DESC',
  );
  return rows;
}

async function alleAccessCodes() {
  const [rows] = await pool.query(
    `SELECT ac.id, ac.code, ac.label, ac.aktiv, ac.tageslimit_microcent, ac.api_key_id,
            ak.label AS api_key_label
     FROM access_codes ac
     LEFT JOIN api_keys ak ON ak.id = ac.api_key_id
     ORDER BY ac.erstellt_am DESC`,
  );
  return rows;
}

/**
 * Löscht einen API-Key endgültig. access_codes.api_key_id verweist mit
 * ON DELETE SET NULL darauf -- betroffene Codes fallen danach automatisch auf
 * den Standard-Key zurück (siehe /v1/messages Schritt 2), kein manuelles
 * Aufräumen nötig.
 */
async function loescheApiKey(id) {
  await pool.query('DELETE FROM api_keys WHERE id = ?', [id]);
}

/** Zentraler Spenden-Link (z. B. PayPal.me), über /admin gepflegt -- siehe /v1/status. */
async function holeSpendenLink() {
  const [rows] = await pool.query('SELECT spenden_link FROM einstellungen WHERE id = 1');
  return rows[0]?.spenden_link || null;
}

async function setzeSpendenLink(link) {
  await pool.query(
    `INSERT INTO einstellungen (id, spenden_link) VALUES (1, ?)
     ON DUPLICATE KEY UPDATE spenden_link = VALUES(spenden_link)`,
    [link || null],
  );
}

module.exports = {
  pool,
  heute,
  findeAccessCode,
  heutigeNutzung,
  nutzungHochzaehlen,
  findeApiKey,
  findeStandardApiKey,
  alleApiKeys,
  alleAccessCodes,
  loescheApiKey,
  holeSpendenLink,
  setzeSpendenLink,
};
