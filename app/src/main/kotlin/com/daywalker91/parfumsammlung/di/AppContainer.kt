package com.daywalker91.parfumsammlung.di

import android.content.Context
import com.daywalker91.parfumsammlung.data.AppDatabase
import com.daywalker91.parfumsammlung.data.PerfumeRepository

/**
 * Sehr schlanke manuelle Dependency-Injection — bewusst kein Hilt/Dagger, um
 * nicht noch eine weitere Dependency-Versions-Baustelle neben AGP/Kotlin/Compose
 * aufzumachen (siehe Kommentare in app/build.gradle.kts zur Versionswahl).
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val perfumeRepository: PerfumeRepository by lazy {
        PerfumeRepository(database.perfumeDao(), database.noteDao(), database.perfumeNoteDao())
    }
}
