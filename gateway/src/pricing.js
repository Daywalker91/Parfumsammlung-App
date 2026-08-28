'use strict';

// Gleiche Formel/Preise wie UsageCounterStore in der App (Haiku 4.5), damit
// Tageslimit und die tatsächlich in der App angezeigten Kosten übereinstimmen.
const PREIS_INPUT_MICROCENT_PRO_TOKEN = 100;
const PREIS_OUTPUT_MICROCENT_PRO_TOKEN = 500;

function kostenMicrocent(inputTokens, outputTokens) {
  return inputTokens * PREIS_INPUT_MICROCENT_PRO_TOKEN + outputTokens * PREIS_OUTPUT_MICROCENT_PRO_TOKEN;
}

module.exports = { kostenMicrocent };
