package com.daywalker91.parfumsammlung.data.model

/** Ein Treffer aus der Namenssuche (Phase 8b) — noch nicht angereichert, nur zur Auswahl/Bestätigung. */
data class PerfumeKandidat(
    val name: String,
    val marke: String,
    val kurzhinweis: String? = null,
)
