package com.daywalker91.parfumsammlung.ui.addflow

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daywalker91.parfumsammlung.R
import com.daywalker91.parfumsammlung.data.BildDownloader
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.claude.ClaudeApiKeyStore
import com.daywalker91.parfumsammlung.data.claude.ClaudeService
import com.daywalker91.parfumsammlung.data.claude.ErkennungErgebnis
import com.daywalker91.parfumsammlung.di.PerfumeSuggestionBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Warum die Erkennung nicht direkt zu einem Vorschlag geführt hat — jeweils mit Fallback auf manuelle Eingabe. */
sealed interface AddHinweis {
    data object KeinApiKey : AddHinweis
    data object Offline : AddHinweis
    data object NichtGenugDaten : AddHinweis
    data class Fehler(val nachricht: String) : AddHinweis
}

data class AddChoiceUiState(
    val ean: String? = null,
    val ladeVorgang: Boolean = false,
    val hinweis: AddHinweis? = null,
    val navigiereZuEditor: Boolean = false,
)

class AddChoiceViewModel(
    private val imageStorage: ImageStorage,
    private val claudeService: ClaudeService,
    private val apiKeyStore: ClaudeApiKeyStore,
    private val bildDownloader: BildDownloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddChoiceUiState())
    val uiState: StateFlow<AddChoiceUiState> = _uiState.asStateFlow()

    // Referenz auf den laufenden Erkennungs-Vorgang, damit "Abbrechen" ihn
    // gezielt canceln kann (statt z. B. viewModelScope komplett zu canceln).
    private var erkennungsJob: Job? = null

    // Einmaliger Diagnose-Hinweis zum Stock-Bild (kein blockierender Dialog wie
    // `hinweis`, da die Haupterkennung ja trotzdem erfolgreich war) — hilft zu
    // unterscheiden, ob Claude schlicht keine Bild-URL geliefert hat oder ob der
    // Download einer gelieferten URL fehlgeschlagen ist (z. B. Hotlink-Schutz).
    private val _stockBildHinweis = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val stockBildHinweis: SharedFlow<Int> = _stockBildHinweis.asSharedFlow()

    fun eanErkannt(ean: String) = _uiState.update { it.copy(ean = ean) }

    /** Bricht einen laufenden Foto-Erkennungsvorgang ab (kein Timeout mehr, siehe ClaudeService). */
    fun abbrechen() {
        erkennungsJob?.cancel()
        _uiState.update { it.copy(ladeVorgang = false) }
    }

    fun hinweisSchliessen() = _uiState.update { it.copy(hinweis = null) }

    fun navigationErledigt() = _uiState.update { it.copy(navigiereZuEditor = false) }

    /** Direkt manuell, ganz ohne Foto. */
    fun manuellOhneFoto() {
        PerfumeSuggestionBridge.setzen(PerfumeSuggestionBridge.Payload(null, null, null, _uiState.value.ean))
        _uiState.update { it.copy(navigiereZuEditor = true) }
    }

    /** Fallback aus einem Hinweis-Dialog: das Foto bleibt erhalten (Bridge ist schon befüllt), nur ohne Vorschlag. */
    fun trotzdemManuellFortfahren() = _uiState.update { it.copy(hinweis = null, navigiereZuEditor = true) }

    fun fotoVerarbeiten(uri: Uri) {
        erkennungsJob = viewModelScope.launch {
            _uiState.update { it.copy(ladeVorgang = true) }

            val bildPfadEigen = withContext(Dispatchers.IO) { imageStorage.speichereVonUri(uri) }

            val apiKey = apiKeyStore.getKey()
            if (apiKey == null) {
                PerfumeSuggestionBridge.setzen(PerfumeSuggestionBridge.Payload(null, bildPfadEigen, null, _uiState.value.ean))
                _uiState.update { it.copy(ladeVorgang = false, hinweis = AddHinweis.KeinApiKey) }
                return@launch
            }

            val bildBytes = bildPfadEigen?.let { pfad -> withContext(Dispatchers.IO) { File(pfad).readBytes() } }
            if (bildBytes == null) {
                _uiState.update { it.copy(ladeVorgang = false, hinweis = AddHinweis.Fehler("Foto konnte nicht gelesen werden.")) }
                return@launch
            }

            when (val ergebnis = claudeService.erkennePerfum(apiKey, bildBytes, _uiState.value.ean)) {
                is ErkennungErgebnis.Erfolg -> {
                    val stockUrl = ergebnis.vorschlag.stockBildUrl
                    val bildPfadStock = stockUrl?.let { url ->
                        bildDownloader.laden(url)?.let { bytes ->
                            withContext(Dispatchers.IO) { imageStorage.speichereVonBytes(bytes) }
                        }
                    }
                    when {
                        stockUrl == null -> _stockBildHinweis.tryEmit(R.string.hinweis_kein_stock_bild_gefunden)
                        bildPfadStock == null -> _stockBildHinweis.tryEmit(R.string.hinweis_stock_bild_download_fehlgeschlagen)
                    }
                    PerfumeSuggestionBridge.setzen(
                        PerfumeSuggestionBridge.Payload(ergebnis.vorschlag, bildPfadEigen, bildPfadStock, _uiState.value.ean),
                    )
                    _uiState.update { it.copy(ladeVorgang = false, navigiereZuEditor = true) }
                }

                ErkennungErgebnis.NichtGenugDaten -> {
                    PerfumeSuggestionBridge.setzen(PerfumeSuggestionBridge.Payload(null, bildPfadEigen, null, _uiState.value.ean))
                    _uiState.update { it.copy(ladeVorgang = false, hinweis = AddHinweis.NichtGenugDaten) }
                }

                ErkennungErgebnis.Offline -> {
                    PerfumeSuggestionBridge.setzen(PerfumeSuggestionBridge.Payload(null, bildPfadEigen, null, _uiState.value.ean))
                    _uiState.update { it.copy(ladeVorgang = false, hinweis = AddHinweis.Offline) }
                }

                is ErkennungErgebnis.Fehler -> {
                    PerfumeSuggestionBridge.setzen(PerfumeSuggestionBridge.Payload(null, bildPfadEigen, null, _uiState.value.ean))
                    _uiState.update { it.copy(ladeVorgang = false, hinweis = AddHinweis.Fehler(ergebnis.nachricht)) }
                }
            }
        }
    }
}
