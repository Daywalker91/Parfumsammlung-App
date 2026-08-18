package com.daywalker91.parfumsammlung.data.gemini

import android.util.Base64
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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

/** Ergebnis der Namens-Kandidatensuche (Phase 8b) — noch keine vollen Duftdaten, nur zur Auswahl/Bestätigung. */
sealed interface GeminiKandidatenErgebnis {
    data class Erfolg(val kandidaten: List<PerfumeKandidat>) : GeminiKandidatenErgebnis
    data object NichtGefunden : GeminiKandidatenErgebnis
    data object Offline : GeminiKandidatenErgebnis
    data class Fehler(val nachricht: String) : GeminiKandidatenErgebnis
}

/** Ergebnis der Shop-Suche (Phase 8c) — reine Momentaufnahme, wird nirgends persistiert. */
sealed interface GeminiShopSucheErgebnis {
    data class Erfolg(val angebote: List<ShopAngebot>) : GeminiShopSucheErgebnis
    data object NichtGefunden : GeminiShopSucheErgebnis
    data object Offline : GeminiShopSucheErgebnis
    data class Fehler(val nachricht: String) : GeminiShopSucheErgebnis
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
class GeminiService(
    // Bewusst KEIN Timeout mehr (siehe callTimeout(0, ...) unten): ein
    // Grounded-Request (Bildanalyse + echte Websuche, siehe baueRequestBody)
    // kann je nach Google-Antwortzeit sehr unterschiedlich lange dauern —
    // statt eines festen Zeitlimits, das mal zu kurz und mal unnötig lang
    // ist, kann der Nutzer den Vorgang stattdessen manuell abbrechen (siehe
    // AddChoiceViewModel.abbrechen). Damit das Abbrechen den Netzwerk-Call
    // auch wirklich sofort stoppt (Coroutine-Abbruch ist kooperativ, ein
    // blockierender execute()-Call würde ihn ignorieren), läuft die Anfrage
    // über ausfuehrenAbbrechbar() statt über ein simples execute().
    //
    // Wichtig: callTimeout(0) allein reicht NICHT — OkHttp hat daneben noch
    // getrennte connect-/write-/read-Timeouts, die ohne explizite Angabe auf
    // den Default von 10s stehen. Der Read-Timeout (Lücke zwischen Antwort-
    // Paketen, z. B. während Gemini "nachdenkt", bevor die Antwort zu
    // streamen beginnt) hat genau deshalb weiterhin zugeschlagen — daher
    // hier alle vier einzeln auf 0 (unbegrenzt).
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .writeTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build(),
) {

    suspend fun erkennePerfum(apiKey: String, bildBytes: ByteArray, ean: String?): GeminiErgebnis =
        geminiAnfrage(
            apiKey = apiKey,
            requestJson = baueRequestBody(bildBytes, ean),
            offline = GeminiErgebnis.Offline,
            fehler = { GeminiErgebnis.Fehler(it) },
            keineAntwort = GeminiErgebnis.Fehler("Keine verwertbare Antwort von Gemini erhalten."),
            parse = ::parseSuggestion,
        )

    /** Kandidatensuche (Phase 8b, Schritt 1) — nur der Name ist bekannt, kein Foto. */
    suspend fun sucheKandidaten(apiKey: String, name: String): GeminiKandidatenErgebnis =
        geminiAnfrage(
            apiKey = apiKey,
            requestJson = baueTextRequestBody(promptKandidaten(name)),
            offline = GeminiKandidatenErgebnis.Offline,
            fehler = { GeminiKandidatenErgebnis.Fehler(it) },
            keineAntwort = GeminiKandidatenErgebnis.Fehler("Keine verwertbare Antwort von Gemini erhalten."),
            parse = ::parseKandidaten,
        )

    /** Volle Datenübernahme (Phase 8b, Schritt 2) — Marke+Name stehen nach der Kandidatenwahl schon fest. */
    suspend fun erkennePerfumNachNameUndMarke(apiKey: String, name: String, marke: String, ean: String?): GeminiErgebnis =
        geminiAnfrage(
            apiKey = apiKey,
            requestJson = baueTextRequestBody(promptNamensSuche(name, marke, ean)),
            offline = GeminiErgebnis.Offline,
            fehler = { GeminiErgebnis.Fehler(it) },
            keineAntwort = GeminiErgebnis.Fehler("Keine verwertbare Antwort von Gemini erhalten."),
            parse = ::parseSuggestion,
        )

    /** Shop-Suche (Phase 8c) — reine Momentaufnahme zum Abrufzeitpunkt, wird nicht persistiert. */
    suspend fun sucheShops(apiKey: String, name: String, marke: String): GeminiShopSucheErgebnis =
        geminiAnfrage(
            apiKey = apiKey,
            requestJson = baueTextRequestBody(promptShopSuche(name, marke)),
            offline = GeminiShopSucheErgebnis.Offline,
            fehler = { GeminiShopSucheErgebnis.Fehler(it) },
            keineAntwort = GeminiShopSucheErgebnis.Fehler("Keine verwertbare Antwort von Gemini erhalten."),
            parse = ::parseShopAngebote,
        )

    /** Lädt ein per Websuche gefundenes Stock-Bild herunter (für ImageStorage.speichereVonBytes). */
    suspend fun ladeBild(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                // Manche Bildhoster/CDNs blocken Downloads ohne "echten"
                // Browser-User-Agent als Hotlink-Schutz — OkHttps Default
                // ("okhttp/x.x") fällt darunter, ein Stock-Bild könnte sonst
                // trotz gültiger URL nie ankommen.
                .header("User-Agent", USER_AGENT)
                .build()
            ausfuehrenAbbrechbar(request).use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body.bytes()
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Wie `httpClient.newCall(request).execute()`, aber echt abbrechbar: wird
     * die aufrufende Coroutine abgebrochen (z. B. weil der Nutzer auf
     * "Abbrechen" tippt), wird über `invokeOnCancellation` der zugehörige
     * OkHttp-Call sofort gecancelt statt weiter im Hintergrund zu laufen.
     */
    private suspend fun ausfuehrenAbbrechbar(request: Request): Response =
        suspendCancellableCoroutine { fortsetzung ->
            val call = httpClient.newCall(request)
            fortsetzung.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (fortsetzung.isActive) fortsetzung.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (fortsetzung.isActive) fortsetzung.resumeWith(Result.success(response)) else response.close()
                }
            })
        }

    /**
     * Gemeinsames Grundgerüst für alle Gemini-Requests dieser Klasse (Foto-
     * Erkennung, Namens-Kandidatensuche, Namens-Vollabruf, Shop-Suche):
     * Request absetzen, HTTP-/Offline-Fehler einheitlich behandeln, bei Erfolg
     * den Antworttext extrahieren und an die jeweilige `parse`-Funktion
     * übergeben. Nur Request-Aufbau und Ergebnis-Parsing unterscheiden sich
     * zwischen den Aufrufern, daher generisch über den Ergebnistyp [T].
     */
    private suspend fun <T> geminiAnfrage(
        apiKey: String,
        requestJson: JSONObject,
        offline: T,
        fehler: (String) -> T,
        keineAntwort: T,
        parse: (String) -> T,
    ): T = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$ENDPOINT_BASIS/$MODELL:generateContent?key=$apiKey")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            ausfuehrenAbbrechbar(request).use { response ->
                val rohantwort = response.body.string()
                if (!response.isSuccessful) {
                    return@withContext fehler("Gemini-API-Fehler (${response.code}): ${fehlermeldungAus(rohantwort)}")
                }
                val text = extrahiereText(rohantwort) ?: return@withContext keineAntwort
                parse(text)
            }
        } catch (e: UnknownHostException) {
            offline
        } catch (e: IOException) {
            fehler(e.message ?: "Netzwerkfehler")
        }
    }

    /** Text-only-Variante von [baueRequestBody] — für Anfragen ohne Foto (Namenssuche, Shop-Suche). */
    private fun baueTextRequestBody(prompt: String): JSONObject {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        val tools = JSONArray().put(JSONObject().put("google_search", JSONObject()))
        val generationConfig = JSONObject().put("temperature", 0.1)
        return JSONObject()
            .put("contents", contents)
            .put("tools", tools)
            .put("generationConfig", generationConfig)
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
        // Niedrige Temperature: ohne explizite generationConfig läuft Gemini auf
        // seinem Default (deutlich über 0), das macht sich bei wiederholten
        // Anfragen für dasselbe Parfum (z. B. über "Daten aktualisieren") als
        // spürbar unterschiedliche Ergebnisse bemerkbar (andere Notenauswahl,
        // andere Formulierung, anderes Stock-Bild), obwohl sich an den echten
        // Websuche-Treffern nichts geändert hat. Für eine Erkennungs-/Recherche-
        // Aufgabe mit klarem JSON-Format ist Kreativität nicht gewollt — niedrige
        // Temperature reduziert diese Varianz, ohne echte, zeitlich bedingte
        // Änderungen (z. B. neue Websuche-Treffer) zu unterdrücken.
        val generationConfig = JSONObject().put("temperature", 0.1)

        return JSONObject()
            .put("contents", contents)
            .put("tools", tools)
            .put("generationConfig", generationConfig)
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
                    saison = json.optStringOrNull("saison"),
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

    private fun parseKandidaten(text: String): GeminiKandidatenErgebnis {
        val jsonText = extrahiereJsonArraySubstring(text)
            ?: return GeminiKandidatenErgebnis.Fehler("Antwort war kein gültiges JSON.")
        return try {
            val array = JSONArray(jsonText)
            val kandidaten = (0 until array.length()).mapNotNull { i ->
                val eintrag = array.optJSONObject(i) ?: return@mapNotNull null
                val name = eintrag.optStringOrNull("name") ?: return@mapNotNull null
                val marke = eintrag.optStringOrNull("marke") ?: return@mapNotNull null
                PerfumeKandidat(name = name, marke = marke, kurzhinweis = eintrag.optStringOrNull("kurzhinweis"))
            }
            if (kandidaten.isEmpty()) GeminiKandidatenErgebnis.NichtGefunden else GeminiKandidatenErgebnis.Erfolg(kandidaten)
        } catch (e: Exception) {
            GeminiKandidatenErgebnis.Fehler("Antwort konnte nicht gelesen werden: ${e.message}")
        }
    }

    private fun extrahiereJsonArraySubstring(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }

    private fun parseShopAngebote(text: String): GeminiShopSucheErgebnis {
        val jsonText = extrahiereJsonArraySubstring(text)
            ?: return GeminiShopSucheErgebnis.Fehler("Antwort war kein gültiges JSON.")
        return try {
            val array = JSONArray(jsonText)
            val angebote = (0 until array.length()).mapNotNull { i ->
                val eintrag = array.optJSONObject(i) ?: return@mapNotNull null
                val shopName = eintrag.optStringOrNull("shopName") ?: return@mapNotNull null
                ShopAngebot(
                    shopName = shopName,
                    link = eintrag.optStringOrNull("link"),
                    preis = eintrag.optDoubleOrNull("preis"),
                    verfuegbarkeit = eintrag.optStringOrNull("verfuegbarkeit"),
                )
            }
            if (angebote.isEmpty()) GeminiShopSucheErgebnis.NichtGefunden else GeminiShopSucheErgebnis.Erfolg(angebote)
        } catch (e: Exception) {
            GeminiShopSucheErgebnis.Fehler("Antwort konnte nicht gelesen werden: ${e.message}")
        }
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

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

        val PROMPT_VORLAGE = """
            Du bist ein Parfum-Experte. Analysiere das beigefügte Foto eines
            Parfum-Flakons genau (Markenlogo, Produktname auf dem Etikett,
            Flakonform, Verschlussfarbe) und identifiziere zuerst Marke und
            exakten Produktnamen.

            Führe danach IMMER mindestens eine Websuche durch, um deine
            Vermutung zu verifizieren und aktuelle Zusatzinformationen zu
            finden (unverbindliche Preisempfehlung, verfügbare Flakongrößen,
            Duftpyramide, ein offizielles Produktbild) — verlass dich nicht
            nur auf Trainingswissen, das kann veraltet sein. Bevorzuge dabei
            etablierte Duft-Datenbanken (z. B. Parfumo, Fragrantica) und
            offizielle Marken-/Händlerseiten gegenüber unklaren Quellen.

            Für "stockBildUrl" gilt eine harte Regel: nur eine URL eintragen,
            die DIREKT auf eine Bilddatei zeigt (endet auf .jpg/.jpeg/.png/
            .webp) und tatsächlich in einem Suchergebnis gesehen wurde —
            niemals eine vermutete/konstruierte URL. Im Zweifel lieber null
            als eine URL, die nicht wirklich existiert.

            Wähle unter mehreren gefundenen Bildern IMMER nach derselben
            Priorität (nicht das erstbeste nehmen):
            1. Offizielles Produktbild von der Marken-/Herstellerseite
            2. Produktbild eines großen, seriösen Parfum-Händlers (z. B.
               Douglas, Flaconi, Notino, Sephora)
            3. Produktbild einer etablierten Duft-Datenbank (Parfumo,
               Fragrantica)
            Bevorzuge dabei den klassischen Flakon-Frontal-Shot auf
            neutralem/weißem Hintergrund (Packshot) vor Lifestyle-Fotos,
            Werbebannern oder Bildern mit sichtbarem Wasserzeichen/Text-
            Overlay. Wenn dieselbe Marke/dasselbe Produkt in mehreren
            Quellen mit erkennbar demselben Packshot auftaucht, ist das ein
            gutes Zeichen für das korrekte, offizielle Bild — dieses wählen.

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
              "stockBildUrl": string oder null (direkte Bild-URL, siehe Regel oben),
              "saison": string oder null (einer von exakt: "Frühling/Sommer", "Herbst/Winter", "Ganzjährig" — nur setzen, wenn über die Websuche wirklich auffindbar, sonst null),
              "nichtGenugDaten": boolean (true NUR falls Marke UND Name nicht sicher identifizierbar sind)
            }

            Wenn Marke und Name sicher feststehen, aber einzelne andere Felder
            (z. B. Duftpyramide, UVP) über die Websuche nicht auffindbar sind,
            setze NICHT "nichtGenugDaten" auf true — lass diese Felder einfach
            auf null/leer. "nichtGenugDaten" gilt ausschließlich für den Fall,
            dass das Parfum selbst nicht identifiziert werden kann. Rate nie —
            weder beim Namen noch bei der stockBildUrl.
        """.trimIndent()

        /** Phase 8b, Schritt 1: nur der Name ist bekannt, noch kein Foto. */
        fun promptKandidaten(name: String) = """
            Ein Nutzer kennt von einem Parfum nur diesen Namen, ohne Foto:
            "$name". Finde per Websuche das/die dazu passenden Parfums.

            Wenn der Name eindeutig zu genau einem Parfum (einer Marke)
            passt: gib genau einen Kandidaten zurück. Wenn derselbe Name zu
            mehreren unterschiedlichen Marken oder Duftlinien passt (z. B.
            weil mehrere Marken ein Parfum mit ähnlichem/gleichem Namen
            führen): gib bis zu 5 plausible Kandidaten zurück, jeweils klar
            unterscheidbar. Erfinde keine Kandidaten, die du nicht wirklich
            in der Websuche gefunden hast.

            Antworte AUSSCHLIESSLICH mit einem einzigen JSON-Array (kein
            Markdown, kein Fließtext davor oder danach) exakt in diesem
            Format, leeres Array falls nichts Plausibles gefunden wurde:
            [
              {
                "name": string (exakter Produktname),
                "marke": string,
                "kurzhinweis": string oder null (max. 1 kurzer Satz, der bei
                  mehreren Kandidaten beim Unterscheiden hilft, z. B. Duftart
                  oder Erscheinungsjahr)
              }
            ]
        """.trimIndent()

        /** Phase 8b, Schritt 2: Marke+Name stehen nach der Kandidatenwahl schon fest, kein Foto. */
        fun promptNamensSuche(name: String, marke: String, ean: String?): String {
            val eanHinweis = if (!ean.isNullOrBlank()) {
                "\nDer Barcode (EAN) des Produkts lautet: $ean — nutze das als zusätzlichen Hinweis."
            } else {
                ""
            }
            return """
                Du bist ein Parfum-Experte. Marke und Produktname stehen
                bereits fest (vom Nutzer bestätigt, kein Foto vorhanden):
                Marke "$marke", Name "$name".$eanHinweis

                Führe eine Websuche durch, um zu diesem konkreten Parfum
                Zusatzinformationen zu finden (unverbindliche Preisempfehlung,
                verfügbare Flakongrößen, Duftpyramide, Saison, ein offizielles
                Produktbild) — verlass dich nicht nur auf Trainingswissen, das
                kann veraltet sein. Bevorzuge dabei etablierte Duft-Datenbanken
                (z. B. Parfumo, Fragrantica) und offizielle Marken-/
                Händlerseiten gegenüber unklaren Quellen.

                Für "stockBildUrl" gilt eine harte Regel: nur eine URL
                eintragen, die DIREKT auf eine Bilddatei zeigt (endet auf
                .jpg/.jpeg/.png/.webp) und tatsächlich in einem Suchergebnis
                gesehen wurde — niemals eine vermutete/konstruierte URL. Im
                Zweifel lieber null als eine URL, die nicht wirklich existiert.
                Wähle unter mehreren gefundenen Bildern dieselbe Priorität wie
                sonst üblich: offizielle Marke vor großem Händler vor
                Duft-Datenbank, klassischer Packshot vor Lifestyle-Foto.

                Antworte AUSSCHLIESSLICH mit einem einzigen JSON-Objekt (kein
                Markdown, kein Fließtext davor oder danach) exakt in diesem
                Format:
                {
                  "name": string,
                  "marke": string,
                  "beschreibung": string oder null (kurze, prägnante Duftbeschreibung, max. 2 Sätze),
                  "uvp": number oder null (unverbindliche Preisempfehlung in Euro, nur die Zahl),
                  "flakongroesse": string oder null,
                  "verfuegbareGroessen": string oder null (z. B. "30ml, 50ml, 100ml"),
                  "notenKopf": string[] (Kopfnoten der Duftpyramide),
                  "notenHerz": string[] (Herznoten der Duftpyramide),
                  "notenBasis": string[] (Basisnoten der Duftpyramide),
                  "stockBildUrl": string oder null (direkte Bild-URL, siehe Regel oben),
                  "saison": string oder null (einer von exakt: "Frühling/Sommer", "Herbst/Winter", "Ganzjährig"),
                  "nichtGenugDaten": boolean (true NUR falls zu diesem Parfum praktisch nichts auffindbar ist)
                }

                Wenn nur einzelne Felder (z. B. Duftpyramide, UVP) über die
                Websuche nicht auffindbar sind, setze NICHT "nichtGenugDaten"
                auf true — lass diese Felder einfach auf null/leer. Rate nie.
            """.trimIndent()
        }

        /** Phase 8c: aktuell verfügbare Online-Shops für ein konkretes Parfum. */
        fun promptShopSuche(name: String, marke: String) = """
            Finde per Websuche aktuell verfügbare Online-Shops für das
            Parfum "$name" von "$marke". Für jeden gefundenen Shop:
            Shop-Name, Link zur Produktseite, aktueller Preis in Euro (falls
            ermittelbar) und ein kurzer Verfügbarkeits-Hinweis (z. B.
            "lagernd", "nicht lagernd", "auf Anfrage") falls ermittelbar.

            Für "link" gilt eine harte Regel: nur eine URL eintragen, die
            tatsächlich in einem Suchergebnis gesehen wurde — niemals eine
            vermutete/konstruierte URL. Im Zweifel lieber null als eine URL,
            die nicht wirklich existiert. Trage nur Shops ein, bei denen du
            wirklich eine passende Produktseite gefunden hast, keine reinen
            Vermutungen.

            Antworte AUSSCHLIESSLICH mit einem einzigen JSON-Array (kein
            Markdown, kein Fließtext davor oder danach) exakt in diesem
            Format, leeres Array falls kein Shop gefunden wurde:
            [
              {
                "shopName": string,
                "link": string oder null (siehe Regel oben),
                "preis": number oder null (nur die Zahl in Euro),
                "verfuegbarkeit": string oder null
              }
            ]
        """.trimIndent()
    }
}
