package com.daywalker91.parfumsammlung.data.batch

import com.daywalker91.parfumsammlung.data.model.PerfumeSuggestion

/** Ein Ergebnis aus dem Batch-Import (Feature 6) — [vorschlag] ist null bei fehlgeschlagener Erkennung. */
data class BatchEintrag(
    val bildPfadEigen: String,
    val vorschlag: PerfumeSuggestion?,
    val bildPfadStock: String?,
    val fehler: String?,
)
