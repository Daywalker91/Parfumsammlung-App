package com.daywalker91.parfumsammlung.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Lädt ein per Websuche gefundenes Stock-Bild herunter — bewusst anbieter-
 * neutral (war schon bei GeminiService nie Gemini-spezifisch, reiner HTTP-GET
 * mit Hotlink-Schutz-Workaround), jetzt eigenständig statt in ClaudeService
 * dupliziert.
 */
class BildDownloader(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) {
    suspend fun laden(url: String): ByteArray? = withContext(Dispatchers.IO) {
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

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    }
}
