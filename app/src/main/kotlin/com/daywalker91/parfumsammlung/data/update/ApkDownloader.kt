package com.daywalker91.parfumsammlung.data.update

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Lädt eine Release-APK herunter und legt sie im Cache ab (FileProvider-Pfad „updates/"). */
class ApkDownloader(private val context: Context, private val httpClient: OkHttpClient = OkHttpClient()) {

    suspend fun herunterladen(url: String, onFortschritt: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body
                    val gesamtGroesse = body.contentLength()
                    val zielOrdner = File(context.cacheDir, "updates").apply { mkdirs() }
                    val zielDatei = File(zielOrdner, "update.apk")

                    body.byteStream().use { input ->
                        FileOutputStream(zielDatei).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var gelesenGesamt = 0L
                            var gelesen: Int
                            while (input.read(buffer).also { gelesen = it } != -1) {
                                output.write(buffer, 0, gelesen)
                                gelesenGesamt += gelesen
                                if (gesamtGroesse > 0) onFortschritt(gelesenGesamt.toFloat() / gesamtGroesse)
                            }
                        }
                    }
                    zielDatei
                }
            } catch (e: IOException) {
                null
            }
        }
}
