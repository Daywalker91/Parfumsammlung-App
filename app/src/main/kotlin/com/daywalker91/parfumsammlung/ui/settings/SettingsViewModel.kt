package com.daywalker91.parfumsammlung.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daywalker91.parfumsammlung.data.SortMode
import com.daywalker91.parfumsammlung.data.SortPreferenceStore
import com.daywalker91.parfumsammlung.data.backup.BackupManager
import com.daywalker91.parfumsammlung.data.claude.ClaudeApiKeyStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ergebnis eines Backup-Vorgangs — einmaliges Toast-Ereignis, kein blockierender Dialog. */
sealed interface BackupHinweis {
    data object ExportFehlgeschlagen : BackupHinweis
    data object ExportErfolgreich : BackupHinweis
    data object ImportFehlgeschlagen : BackupHinweis
    data class ImportErfolgreich(val importiert: Int, val uebersprungen: Int) : BackupHinweis
}

data class SettingsUiState(
    val apiKey: String = "",
    val geradeGespeichert: Boolean = false,
    /** Nur In-Memory/Session-State — resettet beim Schließen der App (siehe Plan). */
    val versionZeilenTaps: Int = 0,
    val backupLaeuft: Boolean = false,
    val sortMode: SortMode = SortMode.NAME,
)

class SettingsViewModel(
    private val apiKeyStore: ClaudeApiKeyStore,
    private val backupManager: BackupManager,
    private val sortPreferenceStore: SortPreferenceStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(apiKey = apiKeyStore.getKey().orEmpty(), sortMode = sortPreferenceStore.getSortMode()),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _backupHinweis = MutableSharedFlow<BackupHinweis>(extraBufferCapacity = 1)
    val backupHinweis: SharedFlow<BackupHinweis> = _backupHinweis.asSharedFlow()

    fun apiKeyGeaendert(value: String) = _uiState.update { it.copy(apiKey = value, geradeGespeichert = false) }

    fun speichern() {
        val key = _uiState.value.apiKey.trim()
        if (key.isBlank()) apiKeyStore.clearKey() else apiKeyStore.setKey(key)
        _uiState.update { it.copy(geradeGespeichert = true) }
    }

    fun versionZeileGetippt() = _uiState.update { it.copy(versionZeilenTaps = it.versionZeilenTaps + 1) }

    fun sortModeGeaendert(mode: SortMode) {
        sortPreferenceStore.setSortMode(mode)
        _uiState.update { it.copy(sortMode = mode) }
    }

    fun backupExportieren(context: Context, zielUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(backupLaeuft = true) }
            val erfolg = backupManager.exportiere(context, zielUri)
            _uiState.update { it.copy(backupLaeuft = false) }
            _backupHinweis.tryEmit(
                if (erfolg) BackupHinweis.ExportErfolgreich else BackupHinweis.ExportFehlgeschlagen,
            )
        }
    }

    fun backupImportieren(context: Context, quelleUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(backupLaeuft = true) }
            val ergebnis = backupManager.importiere(context, quelleUri)
            _uiState.update { it.copy(backupLaeuft = false) }
            _backupHinweis.tryEmit(
                if (ergebnis != null) {
                    BackupHinweis.ImportErfolgreich(ergebnis.importiert, ergebnis.uebersprungen)
                } else {
                    BackupHinweis.ImportFehlgeschlagen
                },
            )
        }
    }
}
