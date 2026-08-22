package com.daywalker91.parfumsammlung.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daywalker91.parfumsammlung.data.GatewayAccessCodeStore
import com.daywalker91.parfumsammlung.data.SortMode
import com.daywalker91.parfumsammlung.data.SortPreferenceStore
import com.daywalker91.parfumsammlung.data.SpendenLinkStore
import com.daywalker91.parfumsammlung.data.UsageCounterStore
import com.daywalker91.parfumsammlung.data.backup.BackupManager
import com.daywalker91.parfumsammlung.data.claude.ClaudeApiKeyStore
import com.daywalker91.parfumsammlung.data.claude.ClaudeService
import com.daywalker91.parfumsammlung.data.claude.GatewayStatus
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
    val lizenzschluessel: String = "",
    val gatewayStatus: GatewayStatus = GatewayStatus.KeinGateway,
    val geradeGespeichert: Boolean = false,
    /** Nur In-Memory/Session-State — resettet beim Schließen der App (siehe Plan). */
    val versionZeilenTaps: Int = 0,
    val backupLaeuft: Boolean = false,
    val sortMode: SortMode = SortMode.NAME,
    val verbrauch: VerbrauchUiState = VerbrauchUiState(),
    val spendenLink: String = "",
)

/** Zwei unabhängige Zähler-Sets, siehe UsageCounterStore — nur zur Anzeige, kein eigener State. */
data class VerbrauchUiState(
    val tokenDiesenMonat: Long = 0,
    val anfragenDiesenMonat: Int = 0,
    val kostenDiesenMonatEuro: Double = 0.0,
    val tokenSeitZahlung: Long = 0,
    val anfragenSeitZahlung: Int = 0,
    val kostenSeitZahlungEuro: Double = 0.0,
    val letzteZahlungMillis: Long = 0,
)

class SettingsViewModel(
    private val apiKeyStore: ClaudeApiKeyStore,
    private val backupManager: BackupManager,
    private val sortPreferenceStore: SortPreferenceStore,
    private val usageCounterStore: UsageCounterStore,
    private val spendenLinkStore: SpendenLinkStore,
    private val gatewayAccessCodeStore: GatewayAccessCodeStore,
    private val claudeService: ClaudeService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            apiKey = apiKeyStore.getKey().orEmpty(),
            lizenzschluessel = gatewayAccessCodeStore.getCode().orEmpty(),
            sortMode = sortPreferenceStore.getSortMode(),
            verbrauch = verbrauchAusStore(),
            spendenLink = spendenLinkStore.getLink().orEmpty(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Nur informativ (Anzeige "läuft über Lizenzschlüssel, noch N Anfragen heute") — kein
        // Anthropic-Aufruf, siehe ClaudeService.gatewayStatus(). In Dev-Builds ohne Gateway
        // (BuildConfig.GATEWAY_BASE_URL leer) liefert das sofort KeinGateway, keine Netzwerkanfrage.
        viewModelScope.launch {
            _uiState.update { it.copy(gatewayStatus = claudeService.gatewayStatus()) }
        }
    }

    private fun verbrauchAusStore() = VerbrauchUiState(
        tokenDiesenMonat = usageCounterStore.tokenDiesenMonat(),
        anfragenDiesenMonat = usageCounterStore.anfragenDiesenMonat(),
        kostenDiesenMonatEuro = usageCounterStore.kostenDiesenMonatEuro(),
        tokenSeitZahlung = usageCounterStore.tokenSeitZahlung(),
        anfragenSeitZahlung = usageCounterStore.anfragenSeitZahlung(),
        kostenSeitZahlungEuro = usageCounterStore.kostenSeitZahlungEuro(),
        letzteZahlungMillis = usageCounterStore.letzteZahlungMillis(),
    )

    /** Betrifft nur "seit letzter Zahlung" — der Monats-Zähler läuft unabhängig automatisch weiter. */
    fun verbrauchBeglichen() {
        usageCounterStore.verbrauchBeglichen()
        _uiState.update { it.copy(verbrauch = verbrauchAusStore()) }
    }

    private val _backupHinweis = MutableSharedFlow<BackupHinweis>(extraBufferCapacity = 1)
    val backupHinweis: SharedFlow<BackupHinweis> = _backupHinweis.asSharedFlow()

    fun apiKeyGeaendert(value: String) = _uiState.update { it.copy(apiKey = value, geradeGespeichert = false) }

    fun lizenzschluesselGeaendert(value: String) =
        _uiState.update { it.copy(lizenzschluessel = value, geradeGespeichert = false) }

    fun speichern() {
        val key = _uiState.value.apiKey.trim()
        if (key.isBlank()) apiKeyStore.clearKey() else apiKeyStore.setKey(key)

        val lizenzschluessel = _uiState.value.lizenzschluessel.trim()
        if (lizenzschluessel.isBlank()) gatewayAccessCodeStore.clearCode() else gatewayAccessCodeStore.setCode(lizenzschluessel)

        _uiState.update { it.copy(geradeGespeichert = true) }
        // Status neu abfragen, falls sich der Lizenzschlüssel geändert hat (z. B. neu eingetragen).
        viewModelScope.launch {
            _uiState.update { it.copy(gatewayStatus = claudeService.gatewayStatus()) }
        }
    }

    fun versionZeileGetippt() = _uiState.update { it.copy(versionZeilenTaps = it.versionZeilenTaps + 1) }

    /** Speichert sofort bei jeder Änderung — kein eigener Speichern-Button, kein Secret. */
    fun spendenLinkGeaendert(value: String) {
        spendenLinkStore.setLink(value)
        _uiState.update { it.copy(spendenLink = value) }
    }

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
