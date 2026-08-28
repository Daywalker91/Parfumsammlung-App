package com.daywalker91.parfumsammlung.data.model

/** Ergebnis einer KI-Erkennung (Foto oder Namenssuche) — anbieter-neutral, unabhängig davon, welcher KI-Dienst antwortet. */
data class PerfumeSuggestion(
    val name: String?,
    val marke: String?,
    val beschreibung: String?,
    val uvp: Double?,
    val flakongroesse: String?,
    val verfuegbareGroessen: String?,
    val notenKopf: List<String> = emptyList(),
    val notenHerz: List<String> = emptyList(),
    val notenBasis: List<String> = emptyList(),
    /** Per Websuche gefundenes Produktbild, falls vorhanden. */
    val stockBildUrl: String? = null,
    /** Eines der drei Saison.label()-Werte als Freitext (siehe Perfume.kt), oder null falls nicht auffindbar. */
    val saison: String? = null,
)
