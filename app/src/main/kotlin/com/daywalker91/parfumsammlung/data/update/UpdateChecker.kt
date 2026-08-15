package com.daywalker91.parfumsammlung.data.update

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class UpdateInfo(
    /** "X.Y" wie im Tag, ohne führendes "v" (siehe Parfum-App_CICD_Plan.md, Versionierung). */
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val istPrerelease: Boolean,
)

sealed interface UpdateCheckErgebnis {
    data class UpdateVerfuegbar(val info: UpdateInfo) : UpdateCheckErgebnis
    data object KeinUpdateVerfuegbar : UpdateCheckErgebnis
    data class Fehler(val nachricht: String) : UpdateCheckErgebnis
}

/**
 * Fragt die öffentliche GitHub-Releases-API ab (kein Auth nötig, siehe
 * Parfum-App_Plan.md, „Self-Update über GitHub Releases"). Stable nutzt
 * `/releases/latest` (schließt Pre-Releases per Definition aus), Experimental
 * nimmt den jeweils neuesten Eintrag aus `/releases` (kann ein Pre-Release sein).
 */
class UpdateChecker(private val httpClient: OkHttpClient = OkHttpClient()) {

    suspend fun pruefen(kanal: UpdateChannel, installierterVersionCode: Int): UpdateCheckErgebnis =
        withContext(Dispatchers.IO) {
            try {
                val releaseJson = when (kanal) {
                    UpdateChannel.STABLE -> holeNeuestesStableRelease()
                    UpdateChannel.EXPERIMENTAL -> holeAllerneuestesRelease()
                } ?: return@withContext UpdateCheckErgebnis.KeinUpdateVerfuegbar

                val info = parseRelease(releaseJson)
                    ?: return@withContext UpdateCheckErgebnis.Fehler("Release-Daten unvollständig (keine APK im Release?).")

                if (info.versionCode > installierterVersionCode) {
                    UpdateCheckErgebnis.UpdateVerfuegbar(info)
                } else {
                    UpdateCheckErgebnis.KeinUpdateVerfuegbar
                }
            } catch (e: IOException) {
                UpdateCheckErgebnis.Fehler(e.message ?: "Netzwerkfehler")
            } catch (e: org.json.JSONException) {
                UpdateCheckErgebnis.Fehler("Antwort von GitHub konnte nicht gelesen werden.")
            }
        }

    private fun holeNeuestesStableRelease(): JSONObject? {
        val request = anfrage("$API_BASIS/releases/latest")
        httpClient.newCall(request).execute().use { response ->
            // 404 bedeutet hier: noch kein Stable-Release existiert — kein Fehler, einfach kein Update.
            if (!response.isSuccessful) return null
            return JSONObject(response.body.string())
        }
    }

    private fun holeAllerneuestesRelease(): JSONObject? {
        val request = anfrage("$API_BASIS/releases")
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val array = JSONArray(response.body.string())
            return if (array.length() > 0) array.getJSONObject(0) else null
        }
    }

    private fun anfrage(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", "Aromathek-App")
        .header("Accept", "application/vnd.github+json")
        .build()

    private fun parseRelease(json: JSONObject): UpdateInfo? {
        val tag = json.optString("tag_name", "").removePrefix("v")
        val teile = tag.split(".")
        if (teile.size != 2) return null
        val x = teile[0].toIntOrNull() ?: return null
        val y = teile[1].toIntOrNull() ?: return null

        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name").endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
        val gefundeneUrl = apkUrl ?: return null

        return UpdateInfo(
            versionName = tag,
            versionCode = x * 10000 + y,
            downloadUrl = gefundeneUrl,
            istPrerelease = json.optBoolean("prerelease", false),
        )
    }

    private companion object {
        const val API_BASIS = "https://api.github.com/repos/Daywalker91/Parfumsammlung-App"
    }
}
