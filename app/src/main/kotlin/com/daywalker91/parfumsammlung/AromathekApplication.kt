package com.daywalker91.parfumsammlung

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.daywalker91.parfumsammlung.di.AppContainer
import com.daywalker91.parfumsammlung.di.AromathekWorkerFactory

class AromathekApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Automatische WorkManager-Init per androidx.startup ist im Manifest
        // abgeschaltet (siehe AndroidManifest.xml) — ohne den dortigen
        // Standard-Initializer liest NICHTS mehr automatisch von
        // Configuration.Provider, deshalb hier zwingend manuell initialisieren,
        // sonst crasht der erste WorkManager.getInstance()-Aufruf mit
        // IllegalStateException ("WorkManager is not initialized").
        WorkManager.initialize(this, workManagerConfiguration)
    }

    // Für den Batch-Fotoimport (Feature 6): eigene WorkerFactory statt
    // WorkManagers Default-Reflection-Konstruktor (siehe AromathekWorkerFactory).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AromathekWorkerFactory(container))
            .build()
}
