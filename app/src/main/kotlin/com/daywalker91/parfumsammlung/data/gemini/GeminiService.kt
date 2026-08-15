package com.daywalker91.parfumsammlung.data.gemini

import android.util.Base64
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Ergebnis eines Erkennungsversuchs — bewusst kein einfaches Result<T>, da die
 * App zwischen "kein Netz", "Gemini fand nichts" und echten Fehlern unterscheiden
 * muss (siehe Plan, Kapitel "Offline-Verhalten" / "Unzureichende Erkennung"). */
sealed interface GeminiErgebnis {
    data class Erfolg(val vorschlag: PerfumeSuggestion) : GeminiErgebnis
    data object NichtGenugDaten : GeminiErgebnis
    data object Offline : GeminiErgebnis
    data class Fehler(val nachricht: String) : GeminiErgebnis
}

/**
 * Direkter REST-Client für die Gemini-API — bewusst kein Google-AI-SDK, um
 * keine weitere Dependency-Versionsfront neben AGP/Kotlin/Compose aufzumachen
 * (siehe app/build.gradle.kts) und um volle Kontrolle über Prompt/Parsing zu
 * behalten.
 *
 * Structured Output (responseSchema) wird absichtlich NICHT zusammen mit dem
 * google_search-Tool genutzt — die Kombination ist je nach API-/Modellversion
 * uneinheitlich dokumentiert. Stattdessen wird Gemini per Prompt zu reinem
 * JSON angewiesen und die Antwort robust geparst (Markdown-Codefences etc.
 * werden toleriert).
 */
class GeminiService(private val httpClient: OkHttpClient = OkHttpClient()) {

    suspend fun erkennePerfum(apiKey: String, bildBytes: ByteArray, ean: String?): GeminiErgebnis =
        withContext(Dispatchers.IO) {
            try {
                val requestJson = baueRequestBody(bildBytes, ean)
                val request = Request.Builder()
                    .url("$ENDPOINT_BASIS/$MODELL:generateContent?key=$apiKey")
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val rohantwort = response.body.string()
                    if (!response.isSuccessful) {
                        return@withContext GeminiErgebnis.Fehler(
                            "Gemini-API-Fehler (${response.code}): ${fehlermeldungAus(rohantwort)}",
                        )
                    }
                    val text = extrahiereText(rohantwort)
                        ?: return@withContext GeminiErgebnis.Fehler("Keine verwertbare Antwort von Gemini erhalten.")
                    parseSuggestion(text)
                }
            } catch (e: UnknownHostException) {
                GeminiErgebnis.Offline
            } catch (e: IOException) {
                GeminiErgebnis.Fehler(e.message ?: "Netzwerkfehler")
            }
        }

    /** Lädt ein per Websuche gefundenes Stock-Bild herunter (für ImageStorage.speichereVonBytes). */
    suspend fun ladeBild(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body.bytes()
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun baueRequestBody(bildBytes: ByteArray, ean: String?): JSONObject {
        val base64Bild = Base64.encodeToString(bildBytes, Base64.NO_WRAP)
        val eanHinweis = if (!ean.isNullOrBlank()) {
            "\nDer Barcode (EAN) des Produkts lautet: $ean — nutze das als zusätzlichen Hinweis zur Identifikation."
        } else {
            ""
        }

        val parts = JSONArray()
            .put(JSONObject().put("text", PROMPT_VORLAGE + eanHinweis))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "image/jpeg").put("data", base64Bild),
                ),
            )

        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        val tools = JSONArray().put(JSONObject().put("google_search", JSONObject()))

        return JSONObject().put("contents", contents).put("tools", tools)
    }

    private fun extrahiereText(rohantwort: String): String? = try {
        val json = JSONObject(rohantwort)
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            null
        } else {
            val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            sb.toString().takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        null
    }

    private fun fehlermeldungAus(rohantwort: String): String = try {
        JSONObject(rohantwort).optJSONObject("error")?.optString("message") ?: rohantwort.take(200)
    } catch (e: Exception) {
        rohantwort.take(200)
    }

    private fun parseSuggestion(text: String): GeminiErgebnis {
        val jsonText = extrahiereJsonSubstring(text)
            ?: return GeminiErgebnis.Fehler("Antwort war kein gültiges JSON.")
        return try {
            val json = JSONObject(jsonText)
            if (json.optBoolean("nichtGenugDaten", false)) {
                return GeminiErgebnis.NichtGenugDaten
            }
            GeminiErgebnis.Erfolg(
                PerfumeSuggestion(
                    name = json.optStringOrNull("name"),
                    marke = json.optStringOrNull("marke"),
                    beschreibung = json.optStringOrNull("beschreibung"),
                    uvp = json.optDoubleOrNull("uvp"),
                    flakongroesse = json.optStringOrNull("flakongroesse"),
                    verfuegbareGroessen = json.optStringOrNull("verfuegbareGroessen"),
                    notenKopf = json.optStringListe("notenKopf"),
                    notenHerz = json.optStringListe("notenHerz"),
                    notenBasis = json.optStringListe("notenBasis"),
                    stockBildUrl = json.optStringOrNull("stockBildUrl"),
                ),
            )
        } catch (e: Exception) {
            GeminiErgebnis.Fehler("Antwort konnte nicht gelesen werden: ${e.message}")
        }
    }

    private fun extrahiereJsonSubstring(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONObject.optStringListe(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private companion object {
        const val ENDPOINT_BASIS = "https://generativelanguage.googleapis.com/v1beta/models"
        // gemini-3.5-flash (zurückgewechselt von gemini-2.5-flash, siehe Commit-Historie):
        // Grounding mit Google-Suche kostet hier seit 5.1.2026 Geld (429 ohne aktives
        // Billing), aber die 2.5er-Reihe ist inzwischen für neu erstellte API-Keys
        // komplett gesperrt (404 "no longer available to new users", vorgezogen vor dem
        // offiziellen Abschaltdatum 16.10.2026) — Grounding mit einem älteren Modell
        // kostenlos zu bekommen ist damit kein gangbarer Weg mehr. Erfordert stattdessen
        // ein Google-Cloud-Billing-Konto (pay-as-you-go, 5.000 grounded Anfragen/Monat
        // weiterhin gratis inklusive, siehe ai.google.dev/gemini-api/docs/pricing).
        const val MODELL = "gemini-3.5-flash"

        val PROMPT_VORLAGE = """
            Du bist ein Parfum-Experte. Analysiere das beigefügte Foto eines
            Parfum-Flakons und identifiziere das Produkt. Nutze bei Bedarf eine
            Websuche, um aktuelle und korrekte Informationen zu finden
            (unverbindliche Preisempfehlung, verfügbare Flakongrößen,
            Duftpyramide, ein offizielles Produktbild).

            Antworte AUSSCHLIESSLICH mit einem einzigen JSON-Objekt (kein
            Markdown, kein Fließtext davor oder danach) exakt in diesem Format:
            {
              "name": string oder null,
              "marke": string oder null,
              "beschreibung": string oder null (kurze, prägnante Duftbeschreibung, max. 2 Sätze),
              "uvp": number oder null (unverbindliche Preisempfehlung in Euro, nur die Zahl),
              "flakongroesse": string oder null (die auf dem Foto erkennbare Größe, z. B. "100ml"),
              "verfuegbareGroessen": string oder null (z. B. "30ml, 50ml, 100ml"),
              "notenKopf": string[] (Kopfnoten der Duftpyramide),
              "notenHerz": string[] (Herznoten der Duftpyramide),
              "notenBasis": string[] (Basisnoten der Duftpyramide),
              "stockBildUrl": string oder null (URL zu einem offiziellen Produktfoto, falls über die Websuche gefunden),
              "nichtGenugDaten": boolean (true, falls das Parfum nicht sicher identifizierbar ist)
            }

            Wenn du das Parfum nicht sicher identifizieren kannst, setze
            "nichtGenugDaten" auf true und lasse die übrigen Felder auf
            null/leer — rate nicht.
        """.trimIndent()
    }
}
