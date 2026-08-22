'use strict';

// Live mitgelesene Anthropic-Kapazität PRO API-Key (nicht global) — siehe Plan
// "Account-Kapazität dieses konkreten Keys, live mitgelesen statt geschätzt".
// Bewusst in-memory, kein DB-Bedarf: ein Pod-Neustart verliert den Stand,
// die nächste Anthropic-Antwort aktualisiert ihn sofort wieder.
const stand = new Map(); // apiKeyId -> { remaining, resetAt }

function aktualisiere(apiKeyId, headers) {
  const remaining = headers.get('anthropic-ratelimit-requests-remaining');
  const resetAt = headers.get('anthropic-ratelimit-requests-reset');
  if (remaining === null) return;
  stand.set(apiKeyId, {
    remaining: Number.parseInt(remaining, 10),
    resetAt: resetAt || null,
  });
}

/** true = Anthropic hat für diesen Key zuletzt praktisch nichts mehr übrig gemeldet. */
function istErschoepft(apiKeyId) {
  const eintrag = stand.get(apiKeyId);
  if (!eintrag) return false;
  return eintrag.remaining <= 0;
}

function retryAfterSekunden(apiKeyId) {
  const eintrag = stand.get(apiKeyId);
  if (!eintrag || !eintrag.resetAt) return null;
  const reset = new Date(eintrag.resetAt).getTime();
  if (Number.isNaN(reset)) return null;
  return Math.max(1, Math.round((reset - Date.now()) / 1000));
}

module.exports = { aktualisiere, istErschoepft, retryAfterSekunden };
