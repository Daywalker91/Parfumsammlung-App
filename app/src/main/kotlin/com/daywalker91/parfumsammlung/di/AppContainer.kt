package com.daywalker91.parfumsammlung.di

import android.content.Context
import com.daywalker91.parfumsammlung.data.AppDatabase
import com.daywalker91.parfumsammlung.data.FirstLaunchPrefs
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.gemini.GeminiApiKeyStore
import com.daywalker91.parfumsammlung.data.gemini.GeminiService

/**
 * Sehr schlanke manuelle Dependency-Injection — bewusst kein Hilt/Dagger, um
 * nicht noch eine weitere Dependency-Versions-Baustelle neben AGP/Kotlin/Compose
 * aufzumachen (siehe Kommentare in app/build.gradle.kts zur Versionswahl).
 */
class AppContainer(private val context: Context) {
    private val database = AppDatabase.getInstance(context)

    val perfumeRepository: PerfumeRepository by lazy {
        PerfumeRepository(database.perfumeDao(), database.noteDao(), database.perfumeNoteDao())
    }

    val imageStorage: ImageStorage by lazy { ImageStorage(context) }

    val geminiApiKeyStore: GeminiApiKeyStore by lazy { GeminiApiKeyStore(context) }

    val geminiService: GeminiService by lazy { GeminiService() }

    val firstLaunchPrefs: FirstLaunchPrefs by lazy { FirstLaunchPrefs(context) }
}
