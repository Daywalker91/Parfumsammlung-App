'use strict';

const crypto = require('crypto');

// AES-256-GCM zum Ablegen der echten Anthropic-Keys in der DB (siehe Plan
// "Anthropic-Key verschlüsselt statt im Klartext"). Der Schlüssel selbst
// kommt aus GATEWAY_ENC_KEY (k8s Secret), liegt nie in der DB.
const ALGORITHMUS = 'aes-256-gcm';
const IV_LAENGE = 12;
const AUTH_TAG_LAENGE = 16;

function schluessel() {
  const hex = process.env.GATEWAY_ENC_KEY;
  if (!hex || hex.length !== 64) {
    throw new Error('GATEWAY_ENC_KEY fehlt oder hat nicht die erwartete Länge (32 Byte als Hex, 64 Zeichen).');
  }
  return Buffer.from(hex, 'hex');
}

/** Gibt IV + Ciphertext + AuthTag als ein zusammenhängendes Buffer zurück (so wie es in api_keys.key_verschluesselt landet). */
function verschluesseln(klartext) {
  const iv = crypto.randomBytes(IV_LAENGE);
  const cipher = crypto.createCipheriv(ALGORITHMUS, schluessel(), iv);
  const ciphertext = Buffer.concat([cipher.update(klartext, 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return Buffer.concat([iv, ciphertext, authTag]);
}

function entschluesseln(buffer) {
  const iv = buffer.subarray(0, IV_LAENGE);
  const authTag = buffer.subarray(buffer.length - AUTH_TAG_LAENGE);
  const ciphertext = buffer.subarray(IV_LAENGE, buffer.length - AUTH_TAG_LAENGE);
  const decipher = crypto.createDecipheriv(ALGORITHMUS, schluessel(), iv);
  decipher.setAuthTag(authTag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
}

module.exports = { verschluesseln, entschluesseln };
