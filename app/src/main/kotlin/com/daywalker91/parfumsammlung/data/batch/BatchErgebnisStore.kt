package com.daywalker91.parfumsammlung.data.batch

import android.content.Context
import com.daywalker91.parfumsammlung.data.model.PerfumeSuggestion
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zwischenspeicher für einen Batch-Import-Lauf (Feature 6) — anders als
 * VergleichLogger (append-only JSONL-Protokoll) wird hier die komplette
 * Ergebnisliste einmal am Ende von PerfumeBatchWorker.doWork() geschrieben
 * und von BatchReviewScreen einmalig komplett zurückgelesen, danach wieder
 * geleert. Kein Protokoll, reine Übergabe zwischen Worker und Review-UI.
 */
class BatchErgebnisStore(context: Context) {
    private val datei = File(context.filesDir, "batch_ergebnisse.json")

    fun schreiben(eintraege: List<BatchEintrag>) {
        val array = JSONArray()
        eintraege.forEach { array.put(it.toJson()) }
        datei.writeText(array.toString())
    }

    fun lesen(): List<BatchEintrag> {
        if (!datei.exists()) return emptyList()
        return try {
            val array = JSONArray(datei.readText())
            (0 until array.length()).map { i -> array.getJSONObject(i).toBatchEintrag() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun leeren() {
        datei.delete()
    }

    private fun BatchEintrag.toJson(): JSONObject = JSONObject().apply {
        put("bildPfadEigen", bildPfadEigen)
        put("bildPfadStock", bildPfadStock ?: JSONObject.NULL)
        put("fehler", fehler ?: JSONObject.NULL)
        put(
            "vorschlag",
            if (vorschlag == null) {
                JSONObject.NULL
            } else {
                JSONObject().apply {
                    put("name", vorschlag.name ?: JSONObject.NULL)
                    put("marke", vorschlag.marke ?: JSONObject.NULL)
                    put("beschreibung", vorschlag.beschreibung ?: JSONObject.NULL)
                    put("uvp", vorschlag.uvp ?: JSONObject.NULL)
                    put("flakongroesse", vorschlag.flakongroesse ?: JSONObject.NULL)
                    put("verfuegbareGroessen", vorschlag.verfuegbareGroessen ?: JSONObject.NULL)
                    put("notenKopf", JSONArray(vorschlag.notenKopf))
                    put("notenHerz", JSONArray(vorschlag.notenHerz))
                    put("notenBasis", JSONArray(vorschlag.notenBasis))
                    put("stockBildUrl", vorschlag.stockBildUrl ?: JSONObject.NULL)
                    put("saison", vorschlag.saison ?: JSONObject.NULL)
                }
            },
        )
    }

    private fun JSONObject.toBatchEintrag(): BatchEintrag = BatchEintrag(
        bildPfadEigen = getString("bildPfadEigen"),
        bildPfadStock = optStringOrNull("bildPfadStock"),
        fehler = optStringOrNull("fehler"),
        vorschlag = optJSONObject("vorschlag")?.let { v ->
            PerfumeSuggestion(
                name = v.optStringOrNull("name"),
                marke = v.optStringOrNull("marke"),
                beschreibung = v.optStringOrNull("beschreibung"),
                uvp = if (v.has("uvp") && !v.isNull("uvp")) v.optDouble("uvp") else null,
                flakongroesse = v.optStringOrNull("flakongroesse"),
                verfuegbareGroessen = v.optStringOrNull("verfuegbareGroessen"),
                notenKopf = v.optStringListe("notenKopf"),
                notenHerz = v.optStringListe("notenHerz"),
                notenBasis = v.optStringListe("notenBasis"),
                stockBildUrl = v.optStringOrNull("stockBildUrl"),
                saison = v.optStringOrNull("saison"),
            )
        },
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optStringListe(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    }
}
