package com.daywalker91.parfumsammlung.di

import android.content.Context
import com.daywalker91.parfumsammlung.data.AppDatabase
import com.daywalker91.parfumsammlung.data.BildDownloader
import com.daywalker91.parfumsammlung.data.FirstLaunchPrefs
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.SortPreferenceStore
import com.daywalker91.parfumsammlung.data.SpendenLinkStore
import com.daywalker91.parfumsammlung.data.UsageCounterStore
import com.daywalker91.parfumsammlung.data.backup.BackupManager
import com.daywalker91.parfumsammlung.data.claude.ClaudeApiKeyStore
import com.daywalker91.parfumsammlung.data.claude.ClaudeService
import com.daywalker91.parfumsammlung.data.update.ApkDownloader
import com.daywalker91.parfumsammlung.data.update.UpdateChannelStore
import com.daywalker91.parfumsammlung.data.update.UpdateChecker

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

    val firstLaunchPrefs: FirstLaunchPrefs by lazy { FirstLaunchPrefs(context) }

    val updateChecker: UpdateChecker by lazy { UpdateChecker() }

    val apkDownloader: ApkDownloader by lazy { ApkDownloader(context) }

    val updateChannelStore: UpdateChannelStore by lazy { UpdateChannelStore(context) }

    val sortPreferenceStore: SortPreferenceStore by lazy { SortPreferenceStore(context) }

    val backupManager: BackupManager by lazy { BackupManager(perfumeRepository, imageStorage) }

    // KI-Backend (Claude Haiku 4.5) — siehe ClaudeService für die Begründung.
    val usageCounterStore: UsageCounterStore by lazy { UsageCounterStore(context) }

    val claudeApiKeyStore: ClaudeApiKeyStore by lazy { ClaudeApiKeyStore(context) }

    val claudeService: ClaudeService by lazy { ClaudeService(usageCounterStore) }

    val bildDownloader: BildDownloader by lazy { BildDownloader() }

    val spendenLinkStore: SpendenLinkStore by lazy { SpendenLinkStore(context) }
}
