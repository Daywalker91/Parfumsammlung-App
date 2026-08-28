package com.daywalker91.parfumsammlung.data.claude

import android.util.Base64
import com.daywalker91.parfumsammlung.BuildConfig
import com.daywalker91.parfumsammlung.data.GatewayAccessCodeStore
import com.daywalker91.parfumsammlung.data.UsageCounterStore
import com.daywalker91.parfumsammlung.data.model.PerfumeKandidat
import com.daywalker91.parfumsammlung.data.model.PerfumeSuggestion
import com.daywalker91.parfumsammlung.data.model.ShopAngebot
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
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

/** Ergebnis eines Erkennungsversuchs (Foto oder Namens-Vollabruf) — siehe frühere GeminiErgebnis. */
sealed interface ErkennungErgebnis {
    data class Erfolg(val vorschlag: PerfumeSuggestion) : ErkennungErgebnis
    data object NichtGenugDaten : ErkennungErgebnis
    data object Offline : ErkennungErgebnis
    data class Fehler(val nachricht: String) : ErkennungErgebnis
}

/** Ergebnis der Namens-Kandidatensuche (Phase 8b) — noch keine vollen Duftdaten, nur zur Auswahl/Bestätigung. */
sealed interface KandidatenErgebnis {
    data class Erfolg(val kandidaten: List<PerfumeKandidat>) : KandidatenErgebnis
    data object NichtGefunden : KandidatenErgebnis
    data object Offline : KandidatenErgebnis
    data class Fehler(val nachricht: String) : KandidatenErgebnis
}

/** Ergebnis der Shop-Suche (Phase 8c) — reine Momentaufnahme, wird nirgends persistiert. */
sealed interface ShopSucheErgebnis {
    data class Erfolg(val angebote: List<ShopAngebot>) : ShopSucheErgebnis
    data object NichtGefunden : ShopSucheErgebnis
    data object Offline : ShopSucheErgebnis
    data class Fehler(val nachricht: String) : ShopSucheErgebnis
}

/** Zustand des geteilten Gateway-Zugangs (Lizenzschlüssel) — reine Anzeige in den Settings, kein Cache. */
sealed interface GatewayStatus {
    /**
     * [verbleibendHeute] ist null bei unlimitiertem Tageslimit (am Gateway leer
     * gelassen). [spendenLink] kommt zentral vom Gateway (über /admin gepflegt)
     * — die App hängt nur noch den Betrag an.
     */
    data class Verfuegbar(val verbleibendHeute: Int?, val spendenLink: String? = null) : GatewayStatus
    data object Gesperrt : GatewayStatus
    /** Kein Gateway in diesem Build (Dev-Build) ODER kein Lizenzschlüssel hinterlegt. */
    data object KeinGateway : GatewayStatus
}

/**
 * Direkter REST-Client für die Anthropic-Messages-API — bewusst kein
 * offizielles SDK (gleiche Begründung wie zuvor bei GeminiService: keine
 * weitere Dependency-Versionsfront neben AGP/Kotlin/Compose, volle Kontrolle
 * über Prompt/Parsing). Alleiniges KI-Backend der App seit dem Umstieg von
 * Gemini (Vergleichstest auf Branch "KI-Vergleich": Claude Haiku 4.5 bei
 * 94–100% Erfolgsquote durchweg in 10–25s, bestes Preis-Leistungs-Verhältnis).
 *
 * Modell ist bewusst rein intern (kein Parameter der öffentlichen Methoden,
 * analog zu GeminiService.MODELL) — die App unterstützt nur noch genau ein
 * Modell, die Mehrfach-Modell-Fähigkeit war nur für den KI-Vergleich nötig.
 */
class ClaudeService(
    private val usageCounterStore: UsageCounterStore,
    // Lizenz-Gateway (siehe Plan "Lizenz-Gateway für geteilten Claude-API-
    // Zugang") — greift nur, wenn kein eigener Key vorliegt. gatewayBaseUrl
    // ist reine Server-Adresse, kein Geheimnis, darf fest einkompiliert sein
    // (Default leer in lokalen Dev-Builds ohne -P-Property, Verhalten dort
    // unverändert: nur eigener Key funktioniert). Der Lizenzschlüssel selbst
    // ist NICHT einkompiliert, sondern kommt ausschließlich vom Nutzer
    // eingetragen aus gatewayAccessCodeStore.
    private val gatewayAccessCodeStore: GatewayAccessCodeStore,
    private val gatewayBaseUrl: String = BuildConfig.GATEWAY_BASE_URL,
    // Bewusst KEIN Timeout (siehe GeminiService für die ausführliche
    // Begründung) — ein Grounded-Request kann unterschiedlich lange dauern,
    // der Nutzer bricht im Einzel-Flow stattdessen manuell ab. Nur der
    // Batch-Import (PerfumeBatchWorker) legt sich selbst einen Timeout auf,
    // weil dort kein manuelles Abbrechen pro Bild möglich ist.
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) {

    /** Ob überhaupt ein Weg existiert, eine Anfrage zu senden — eigener Key ODER Gateway-Zugang. */
    fun kannAnfragenSenden(apiKey: String?): Boolean =
        apiKey != null || (gatewayBaseUrl.isNotBlank() && gatewayAccessCodeStore.getCode() != null)

    /** Status des geteilten Zugangs für die Settings-Anzeige — kein Anthropic-Aufruf, kein Kostenrisiko. */
    suspend fun gatewayStatus(): GatewayStatus {
        if (gatewayBaseUrl.isBlank()) return GatewayStatus.KeinGateway
        val code = gatewayAccessCodeStore.getCode() ?: return GatewayStatus.KeinGateway
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${gatewayBaseUrl.trimEnd('/')}/v1/status")
                    .header("X-Access-Code", code)
                    .build()
                ausfuehrenAbbrechbar(request).use { response ->
                    if (!response.isSuccessful) return@withContext GatewayStatus.Gesperrt
                    val json = JSONObject(response.body.string())
                    if (json.optBoolean("gueltig", false)) {
                        GatewayStatus.Verfuegbar(
                            // null = unlimitiertes Tageslimit (siehe /admin, Feld leer lassen) —
                            // der Gateway lässt "verbleibendHeute" dann ganz weg.
                            verbleibendHeute = if (json.optBoolean("unlimitiert", false)) {
                                null
                            } else {
                                json.optInt("verbleibendHeute", 0)
                            },
                            spendenLink = json.optString("spendenLink").takeIf { it.isNotBlank() },
                        )
                    } else {
                        GatewayStatus.Gesperrt
                    }
                }
            } catch (e: IOException) {
                GatewayStatus.KeinGateway
            }
        }
    }

    suspend fun erkennePerfum(apiKey: String?, bildBytes: ByteArray, ean: String?): ErkennungErgebnis =
        claudeAnfrage(
            apiKey = apiKey,
            requestJson = baueBildRequestBody(bildBytes, ean),
            offline = ErkennungErgebnis.Offline,
            fehler = { ErkennungErgebnis.Fehler(it) },
            keineAntwort = ErkennungErgebnis.Fehler("Keine verwertbare Antwort von Claude erhalten."),
            parse = ::parseSuggestion,
        )

    /** Kandidatensuche (Phase 8b, Schritt 1) — nur der Name ist bekannt, kein Foto. */
    suspend fun sucheKandidaten(apiKey: String?, name: String): KandidatenErgebnis =
        claudeAnfrage(
            apiKey = apiKey,
            requestJson = baueTextRequestBody(promptKandidaten(name)),
            offline = KandidatenErgebnis.Offline,
            fehler = { KandidatenErgebnis.Fehler(it) },
            keineAntwort = KandidatenErgebnis.Fehler("Keine verwertbare Antwort von Claude erhalten."),
            parse = ::parseKandidaten,
        )

    /** Volle Datenübernahme (Phase 8b, Schritt 2) — Marke+Name stehen nach der Kandidatenwahl schon fest. */
    suspend fun erkennePerfumNachNameUndMarke(apiKey: String?, name: String, marke: String, ean: String?): ErkennungErgebnis =
        claudeAnfrage(
            apiKey = apiKey,
            requestJson = baueTextRequestBody(promptNamensSuche(name, marke, ean)),
            offline = ErkennungErgebnis.Offline,
            fehler = { ErkennungErgebnis.Fehler(it) },
            keineAntwort = ErkennungErgebnis.Fehler("Keine verwertbare Antwort von Claude erhalten."),
            parse = ::parseSuggestion,
        )

    /** Shop-Suche (Phase 8c) — reine Momentaufnahme zum Abrufzeitpunkt, wird nicht persistiert. */
    suspend fun sucheShops(apiKey: String?, name: String, marke: String): ShopSucheErgebnis =
        claudeAnfrage(
            apiKey = apiKey,
            requestJson = baueTextRequestBody(promptShopSuche(name, marke)),
            offline = ShopSucheErgebnis.Offline,
            fehler = { ShopSucheErgebnis.Fehler(it) },
            keineAntwort = ShopSucheErgebnis.Fehler("Keine verwertbare Antwort von Claude erhalten."),
            parse = ::parseShopAngebote,
        )

    /** Wie GeminiService.ausfuehrenAbbrechbar — echt abbrechbar statt blockierendem execute(). */
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
     * Gemeinsames Grundgerüst für alle Claude-Requests dieser Klasse — analog
     * zu GeminiService.geminiAnfrage: Request absetzen, HTTP-/Offline-Fehler
     * einheitlich behandeln, Verbrauchszähler aktualisieren, bei Erfolg den
     * Antworttext extrahieren (inkl. <cite>-Tag-Bereinigung) und an die
     * jeweilige parse-Funktion übergeben.
     */
    private suspend fun <T> claudeAnfrage(
        apiKey: String?,
        requestJson: JSONObject,
        offline: T,
        fehler: (String) -> T,
        keineAntwort: T,
        parse: (String) -> T,
    ): T = withContext(Dispatchers.IO) {
        // Routing: eigener Key -> direkt Anthropic (heutiges Verhalten). Kein
        // eigener Key -> Lizenzschlüssel aus dem Gateway-Store, falls
        // vorhanden UND ein Gateway in diesem Build konfiguriert ist. Fehlen
        // beide, hätte kannAnfragenSenden() das schon vorher verhindern
        // sollen — dieser Zweig ist nur ein Sicherheitsnetz.
        val zugangscode = if (apiKey == null) gatewayAccessCodeStore.getCode() else null
        if (apiKey == null && (zugangscode == null || gatewayBaseUrl.isBlank())) {
            return@withContext fehler("Kein eigener Claude-API-Key und kein Lizenzschlüssel hinterlegt.")
        }
        try {
            val requestBuilder = Request.Builder()
                .header("anthropic-version", ANTHROPIC_VERSION)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            if (apiKey != null) {
                requestBuilder.url(ENDPOINT).header("x-api-key", apiKey)
            } else {
                requestBuilder.url("${gatewayBaseUrl.trimEnd('/')}/v1/messages").header("X-Access-Code", zugangscode!!)
            }
            val request = requestBuilder.build()

            ausfuehrenAbbrechbar(request).use { response ->
                val rohantwort = response.body.string()
                if (!response.isSuccessful) {
                    return@withContext fehler("Claude-API-Fehler (${response.code}): ${fehlermeldungAus(rohantwort)}")
                }
                val json = JSONObject(rohantwort)
                // usage ist auch bei einer Ablehnung (stop_reason "refusal") oder
                // nicht auswertbarer Antwort vorhanden — Anthropic berechnet die
                // verbrauchten Token unabhängig vom Inhalt der Antwort.
                json.optJSONObject("usage")?.let {
                    usageCounterStore.hinzufuegen(it.optInt("input_tokens"), it.optInt("output_tokens"))
                }
                // Sicherheits-Klassifizierer können eine Anfrage ablehnen (stop_reason
                // "refusal") — dann ist der Content nicht auswertbar, vorher abfangen.
                if (json.optString("stop_reason") == "refusal") {
                    return@withContext fehler("Claude hat die Anfrage abgelehnt.")
                }
                val text = extrahiereText(json) ?: return@withContext keineAntwort
                parse(text)
            }
        } catch (e: UnknownHostException) {
            offline
        } catch (e: IOException) {
            fehler(e.message ?: "Netzwerkfehler")
        }
    }

    private fun baueBildRequestBody(bildBytes: ByteArray, ean: String?): JSONObject {
        val base64Bild = Base64.encodeToString(bildBytes, Base64.NO_WRAP)
        val eanHinweis = if (!ean.isNullOrBlank()) {
            "\nDer Barcode (EAN) des Produkts lautet: $ean — nutze das als zusätzlichen Hinweis zur Identifikation."
        } else {
            ""
        }
        val content = JSONArray()
            .put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", base64Bild),
                ),
            )
            .put(JSONObject().put("type", "text").put("text", PROMPT_VORLAGE + eanHinweis))
        return baueRequestBody(content)
    }

    /** Text-only-Variante — für Anfragen ohne Foto (Namenssuche, Namens-Vollabruf, Shop-Suche). */
    private fun baueTextRequestBody(prompt: String): JSONObject {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
        return baueRequestBody(content)
    }

    private fun baueRequestBody(content: JSONArray): JSONObject {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content))
        // max_uses deckelt die Anzahl der Suchrunden pro Anfrage — ohne dieses
        // Limit liefen im Batch-Testlauf einzelne Anfragen bis zum Timeout durch
        // (siehe KI-Vergleich-Historie). 5 Runden reichen für diese Aufgabe
        // großzügig aus.
        val tools = JSONArray().put(
            JSONObject().put("type", WEB_SEARCH_TOOL_TYP).put("name", "web_search").put("max_uses", 5),
        )
        return JSONObject()
            .put("model", MODELL)
            .put("max_tokens", 4096)
            // Niedrige Temperature reduziert Varianz zwischen wiederholten
            // Anfragen für dasselbe Parfum (z. B. "Daten aktualisieren") — gleiche
            // Begründung wie zuvor bei GeminiService.
            .put("temperature", 0.1)
            .put("tools", tools)
            .put("messages", messages)
    }

    /**
     * Konkateniert alle Text-Blöcke der Antwort (überspringt server_tool_use/
     * web_search_tool_result-Blöcke) und entfernt dabei rohe Zitat-Marker, die
     * Claude (vor allem Haiku) bei Web-Search-gestützten Aussagen manchmal
     * direkt im Fließtext hinterlässt (z. B. <cite index="2-1">...</cite>) —
     * landet das innerhalb eines JSON-Strings, muss es vor dem Parsen raus.
     */
    private fun extrahiereText(json: JSONObject): String? {
        val content = json.optJSONArray("content") ?: return null
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") {
                sb.append(block.optString("text", ""))
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }?.let(::entferneCiteTags)
    }

    private fun entferneCiteTags(text: String): String = text.replace(CITE_TAG_REGEX, "")

    /** Deckt sowohl Anthropics eigenes Fehlerformat ({"error":{"message":...}})
     * als auch das schlankere Gateway-Format ({"fehler":"..."}) ab. */
    private fun fehlermeldungAus(rohantwort: String): String = try {
        val json = JSONObject(rohantwort)
        json.optJSONObject("error")?.optString("message")
            ?: json.optStringOrNull("fehler")
            ?: rohantwort.take(200)
    } catch (e: Exception) {
        rohantwort.take(200)
    }

    private fun parseSuggestion(text: String): ErkennungErgebnis {
        val jsonText = extrahiereJsonSubstring(text)
            ?: return ErkennungErgebnis.Fehler("Antwort war kein gültiges JSON.")
        return try {
            val json = JSONObject(jsonText)
            if (json.optBoolean("nichtGenugDaten", false)) {
                return ErkennungErgebnis.NichtGenugDaten
            }
            ErkennungErgebnis.Erfolg(
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
            ErkennungErgebnis.Fehler("Antwort konnte nicht gelesen werden: ${e.message}")
        }
    }

    private fun parseKandidaten(text: String): KandidatenErgebnis {
        val jsonText = extrahiereJsonArraySubstring(text)
            ?: return KandidatenErgebnis.Fehler("Antwort war kein gültiges JSON.")
        return try {
            val array = JSONArray(jsonText)
            val kandidaten = (0 until array.length()).mapNotNull { i ->
                val eintrag = array.optJSONObject(i) ?: return@mapNotNull null
                val name = eintrag.optStringOrNull("name") ?: return@mapNotNull null
                val marke = eintrag.optStringOrNull("marke") ?: return@mapNotNull null
                PerfumeKandidat(name = name, marke = marke, kurzhinweis = eintrag.optStringOrNull("kurzhinweis"))
            }
            if (kandidaten.isEmpty()) KandidatenErgebnis.NichtGefunden else KandidatenErgebnis.Erfolg(kandidaten)
        } catch (e: Exception) {
            KandidatenErgebnis.Fehler("Antwort konnte nicht gelesen werden: ${e.message}")
        }
    }

    private fun parseShopAngebote(text: String): ShopSucheErgebnis {
        val jsonText = extrahiereJsonArraySubstring(text)
            ?: return ShopSucheErgebnis.Fehler("Antwort war kein gültiges JSON.")
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
            if (angebote.isEmpty()) ShopSucheErgebnis.NichtGefunden else ShopSucheErgebnis.Erfolg(angebote)
        } catch (e: Exception) {
            ShopSucheErgebnis.Fehler("Antwort konnte nicht gelesen werden: ${e.message}")
        }
    }

    private fun extrahiereJsonSubstring(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }

    private fun extrahiereJsonArraySubstring(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
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
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MODELL = "claude-haiku-4-5"

        // Haikus Basis-Web-Search-Variante — die dynamische-Filterung-Variante
        // (web_search_20260209) ist Sonnet/Opus-Modellen vorbehalten.
        const val WEB_SEARCH_TOOL_TYP = "web_search_20250305"

        // Trifft sowohl öffnende Tags mit Attributen (<cite index="2-1">) als
        // auch den schließenden Tag (</cite>).
        val CITE_TAG_REGEX = Regex("</?cite[^>]*>")

        // Prompts unverändert von GeminiService übernommen (waren bereits
        // anbieter-neutral formuliert, keine Gemini-spezifische Wortwahl).
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
