package com.daywalker91.parfumsammlung.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.daywalker91.parfumsammlung.data.batch.PerfumeBatchWorker

/**
 * WorkManager instanziiert Worker normalerweise per Reflection über einen
 * leeren Konstruktor. PerfumeBatchWorker braucht aber dieselben Services wie
 * der Rest der App (aus AppContainer) — deshalb eine eigene, sehr schlanke
 * Factory statt Hilt/Dagger (gleiche Begründung wie bei AppContainer selbst).
 */
class AromathekWorkerFactory(private val container: AppContainer) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        PerfumeBatchWorker::class.java.name -> PerfumeBatchWorker(
            appContext,
            workerParameters,
            container.claudeService,
            container.claudeApiKeyStore,
            container.bildDownloader,
            container.imageStorage,
            container.batchErgebnisStore,
        )
        else -> null
    }
}
