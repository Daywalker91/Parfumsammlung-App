package com.daywalker91.parfumsammlung.ui.addflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.gemini.GeminiApiKeyStore
import com.daywalker91.parfumsammlung.data.gemini.GeminiErgebnis
import com.daywalker91.parfumsammlung.data.gemini.GeminiKandidatenErgebnis
import com.daywalker91.parfumsammlung.data.gemini.GeminiService
import com.daywalker91.parfumsammlung.data.gemini.PerfumeKandidat
import com.daywalker91.parfumsammlung.data.gemini.PerfumeSuggestion
import com.daywalker91.parfumsammlung.di.PerfumeSuggestionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Warum die Namenssuche nicht direkt zu einem übernehmbaren Ergebnis geführt hat. */
sealed interface SucheHinweis {
    data object KeinApiKey : SucheHinweis
    data object Offline : SucheHinweis
    data object NichtGefunden : SucheHinweis
    data class Fehler(val nachricht: String) : SucheHinweis
}

data class ManuelleSucheUiState(
    val suchtext: String = "",
    val ladeVorgang: Boolean = false,
    val kandidaten: List<PerfumeKandidat> = emptyList(),
    /** Genau ein Treffer gefunden — braucht die "Meinten Sie …?"-Rückfrage statt einer Auswahlliste. */
    val bestaetigungKandidat: PerfumeKandidat? = null,
    val hinweis: SucheHinweis? = null,
    val navigiereZuEditor: Boolean = false,
)

class ManuelleSucheViewModel(
    private val geminiService: GeminiService,
    private val apiKeyStore: GeminiApiKeyStore,
    private val imageStorage: ImageStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManuelleSucheUiState())
    val uiState: StateFlow<ManuelleSucheUiState> = _uiState.asStateFlow()

    // Referenz auf den jeweils laufenden Vorgang (Kandidatensuche ODER
    // Vollabruf nach Auswahl), damit "Abbrechen" gezielt ihn canceln kann —
    // gleiches Muster wie AddChoiceViewModel.erkennungsJob.
    private var laufenderJob: Job? = null

    fun suchtextGeaendert(value: String) = _uiState.update { it.copy(suchtext = value) }

    fun abbrechen() {
        laufenderJob?.cancel()
        _uiState.update { it.copy(ladeVorgang = false) }
    }

    fun hinweisSchliessen() = _uiState.update { it.copy(hinweis = null) }

    fun navigationErledigt() = _uiState.update { it.copy(navigiereZuEditor = false) }

    fun bestaetigungAbgelehnt() = _uiState.update { it.copy(bestaetigungKandidat = null) }

    fun suchen() {
        val name = _uiState.value.suchtext.trim()
        if (name.isBlank()) return
        laufenderJob = viewModelScope.launch {
            _uiState.update { it.copy(ladeVorgang = true, kandidaten = emptyList(), bestaetigungKandidat = null) }

            val apiKey = apiKeyStore.getKey()
            if (apiKey == null) {
                _uiState.update { it.copy(ladeVorgang = false, hinweis = SucheHinweis.KeinApiKey) }
                return@launch
            }

            when (val ergebnis = geminiService.sucheKandidaten(apiKey, name)) {
                is GeminiKandidatenErgebnis.Erfolg -> {
                    // Genau ein Treffer: klassische Ja/Nein-Rückfrage. Mehrere
                    // Treffer: die Auswahl aus der Liste IST die Bestätigung,
                    // kein zusätzlicher Dialog (siehe Konzeptdokument).
                    if (ergebnis.kandidaten.size == 1) {
                        _uiState.update { it.copy(ladeVorgang = false, bestaetigungKandidat = ergebnis.kandidaten.first()) }
                    } else {
                        _uiState.update { it.copy(ladeVorgang = false, kandidaten = ergebnis.kandidaten) }
                    }
                }

                GeminiKandidatenErgebnis.NichtGefunden ->
                    _uiState.update { it.copy(ladeVorgang = false, hinweis = SucheHinweis.NichtGefunden) }

                GeminiKandidatenErgebnis.Offline ->
                    _uiState.update { it.copy(ladeVorgang = false, hinweis = SucheHinweis.Offline) }

                is GeminiKandidatenErgebnis.Fehler ->
                    _uiState.update { it.copy(ladeVorgang = false, hinweis = SucheHinweis.Fehler(ergebnis.nachricht)) }
            }
        }
    }

    /** Bestätigung aus dem "Meinten Sie …?"-Dialog ODER Auswahl aus der Kandidatenliste. */
    fun kandidatUebernehmen(kandidat: PerfumeKandidat) {
        laufenderJob = viewModelScope.launch {
            _uiState.update { it.copy(ladeVorgang = true, bestaetigungKandidat = null, kandidaten = emptyList()) }

            val apiKey = apiKeyStore.getKey()
            if (apiKey == null) {
                _uiState.update { it.copy(ladeVorgang = false, hinweis = SucheHinweis.KeinApiKey) }
                return@launch
            }

            when (val ergebnis = geminiService.erkennePerfumNachNameUndMarke(apiKey, kandidat.name, kandidat.marke, ean = null)) {
                is GeminiErgebnis.Erfolg -> zurEditorUebernehmen(ergebnis.vorschlag)

                // Marke+Name sind über die Kandidatenwahl bereits sicher —
                // ein "nichtGenugDaten" hier heißt nur, dass keine weiteren
                // Details auffindbar waren. Statt einer Fehlermeldung trotzdem
                // mit den bekannten Basisdaten zum Editor weiter, wie bei
                // einer normalen Erkennung mit unvollständigen Zusatzfeldern.
                GeminiErgebnis.NichtGenugDaten ->
                    zurEditorUebernehmen(PerfumeSuggestion(name = kandidat.name, marke = kandidat.marke, beschreibung = null, uvp = null, flakongroesse = null, verfuegbareGroessen = null))

                GeminiErgebnis.Offline ->
                    _uiState.update { it.copy(ladeVorgang = false, hinweis = SucheHinweis.Offline) }

                is GeminiErgebnis.Fehler ->
                    _uiState.update { it.copy(ladeVorgang = false, hinweis = SucheHinweis.Fehler(ergebnis.nachricht)) }
            }
        }
    }

    private suspend fun zurEditorUebernehmen(vorschlag: PerfumeSuggestion) {
        val bildPfadStock = vorschlag.stockBildUrl?.let { url ->
            geminiService.ladeBild(url)?.let { bytes -> withContext(Dispatchers.IO) { imageStorage.speichereVonBytes(bytes) } }
        }
        PerfumeSuggestionBridge.setzen(PerfumeSuggestionBridge.Payload(vorschlag, null, bildPfadStock, null))
        _uiState.update { it.copy(ladeVorgang = false, navigiereZuEditor = true) }
    }
}
