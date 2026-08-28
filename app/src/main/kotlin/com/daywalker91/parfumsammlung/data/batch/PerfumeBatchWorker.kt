package com.daywalker91.parfumsammlung.data.batch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.daywalker91.parfumsammlung.data.BildDownloader
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.claude.ClaudeApiKeyStore
import com.daywalker91.parfumsammlung.data.claude.ClaudeService
import com.daywalker91.parfumsammlung.data.claude.ErkennungErgebnis
import java.io.File
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Verarbeitet mehrere per Galerie-Mehrfachauswahl gewählte Fotos sequentiell
 * im Hintergrund (Feature 6) — Foreground-Service mit Fortschritts-
 * Notification. Erzeugt KEINE Perfume-DB-Einträge selbst — das Ergebnis
 * landet in BatchErgebnisStore, BatchReviewScreen entscheidet nach manueller
 * Durchsicht, was tatsächlich übernommen wird (siehe Plan: bewusst kein
 * automatisches Speichern ohne Review-Schritt).
 *
 * Timeout pro Bild (anders als der interaktive Einzel-Flow, der bewusst
 * KEIN Timeout hat — siehe ClaudeService): ohne manuellen Abbrechen-Button
 * pro Bild würde ein einzelner hängender Request sonst den kompletten Batch
 * für immer blockieren (gleiche Begründung wie beim früheren KI-Vergleichs-
 * Tool, dort live beobachtet).
 */
class PerfumeBatchWorker(
    appContext: Context,
    params: WorkerParameters,
    private val claudeService: ClaudeService,
    private val claudeApiKeyStore: ClaudeApiKeyStore,
    private val bildDownloader: BildDownloader,
    private val imageStorage: ImageStorage,
    private val batchErgebnisStore: BatchErgebnisStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bildPfade = inputData.getStringArray(KEY_BILD_PFADE)?.toList().orEmpty()
        if (bildPfade.isEmpty()) return Result.failure()
        val apiKey = claudeApiKeyStore.getKey()
        if (!claudeService.kannAnfragenSenden(apiKey)) return Result.failure()

        val ergebnisse = bildPfade.mapIndexed { index, pfad ->
            setForeground(erstelleForegroundInfo(index, bildPfade.size))
            setProgress(workDataOf(KEY_PROGRESS_AKTUELL to index, KEY_PROGRESS_GESAMT to bildPfade.size))
            verarbeiteEinzelnesBild(apiKey, pfad)
        }
        batchErgebnisStore.schreiben(ergebnisse)
        setProgress(workDataOf(KEY_PROGRESS_AKTUELL to bildPfade.size, KEY_PROGRESS_GESAMT to bildPfade.size))
        return Result.success()
    }

    private suspend fun verarbeiteEinzelnesBild(apiKey: String?, pfad: String): BatchEintrag {
        val bytes = File(pfad).takeIf { it.exists() }?.readBytes()
            ?: return BatchEintrag(pfad, null, null, "Foto konnte nicht gelesen werden.")

        val ergebnis = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { claudeService.erkennePerfum(apiKey, bytes, null) }
            ?: return BatchEintrag(pfad, null, null, "Zeitüberschreitung im Batch (>${PROVIDER_TIMEOUT_MS / 1000}s).")

        return when (ergebnis) {
            is ErkennungErgebnis.Erfolg -> {
                // Kein EAN im Batch-Flow (akzeptierte Einschränkung, kein
                // Barcode-Scan-Schritt pro Bild bei Mehrfachauswahl vorgesehen).
                val stockPfad = ergebnis.vorschlag.stockBildUrl?.let { url ->
                    bildDownloader.laden(url)?.let { imageStorage.speichereVonBytes(it) }
                }
                BatchEintrag(pfad, ergebnis.vorschlag, stockPfad, null)
            }
            ErkennungErgebnis.NichtGenugDaten -> BatchEintrag(pfad, null, null, "Nicht genug Daten gefunden.")
            ErkennungErgebnis.Offline -> BatchEintrag(pfad, null, null, "Keine Internetverbindung.")
            is ErkennungErgebnis.Fehler -> BatchEintrag(pfad, null, null, ergebnis.nachricht)
        }
    }

    private fun erstelleForegroundInfo(aktuell: Int, gesamt: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(KANAL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(KANAL_ID, "Foto-Import", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, KANAL_ID)
            .setContentTitle("Fotos werden erkannt")
            .setContentText("Bild ${aktuell + 1} von $gesamt")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setProgress(gesamt, aktuell, false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_BILD_PFADE = "bildPfade"
        const val KEY_PROGRESS_AKTUELL = "aktuell"
        const val KEY_PROGRESS_GESAMT = "gesamt"
        const val UNIQUE_WORK_NAME = "perfume_batch"
        private const val KANAL_ID = "perfume_batch"
        private const val NOTIFICATION_ID = 4712
        private const val PROVIDER_TIMEOUT_MS = 120_000L
    }
}
