package com.daywalker91.parfumsammlung.data.gemini

/** Von Gemini aus einem Foto (+ optional EAN als Zusatzkontext) erkannte Parfum-Daten. */
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
    /** Per Google Search Grounding gefundenes Produktbild, falls vorhanden. */
    val stockBildUrl: String? = null,
)
