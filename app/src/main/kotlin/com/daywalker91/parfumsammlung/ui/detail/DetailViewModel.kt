package com.daywalker91.parfumsammlung.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daywalker91.parfumsammlung.data.AktivesBild
import com.daywalker91.parfumsammlung.data.NoteWithPosition
import com.daywalker91.parfumsammlung.data.Perfume
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.claude.ClaudeApiKeyStore
import com.daywalker91.parfumsammlung.data.claude.ClaudeService
import com.daywalker91.parfumsammlung.data.claude.ShopSucheErgebnis
import com.daywalker91.parfumsammlung.data.model.ShopAngebot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val perfume: Perfume? = null,
    val notes: List<NoteWithPosition> = emptyList(),
    // Shop-Suche (Phase 8c) ist bewusst reine Momentaufnahme, nirgends
    // persistiert — lebt nur im ViewModel-State, nicht in `Perfume`.
    val shopAngebote: List<ShopAngebot> = emptyList(),
    val shopSucheLaeuft: Boolean = false,
    val shopSucheAbgerufen: Boolean = false,
)

/** Warum eine Shop-Suche nicht zu Ergebnissen geführt hat — einmaliges Toast-Ereignis. */
sealed interface ShopSucheHinweis {
    data object KeinApiKey : ShopSucheHinweis
    data object Offline : ShopSucheHinweis
    data object NichtGefunden : ShopSucheHinweis
    data class Fehler(val nachricht: String) : ShopSucheHinweis
}

/** Nur intern, um die `combine`-Kette lesbar zu halten (statt eines Triple). */
private data class ShopZustand(
    val angebote: List<ShopAngebot> = emptyList(),
    val laeuft: Boolean = false,
    val abgerufen: Boolean = false,
)

class DetailViewModel(
    perfumeId: Long,
    private val repository: PerfumeRepository,
    private val claudeService: ClaudeService,
    private val apiKeyStore: ClaudeApiKeyStore,
) : ViewModel() {

    private val _shopZustand = MutableStateFlow(ShopZustand())

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeById(perfumeId),
        repository.observeNotesForPerfume(perfumeId),
        _shopZustand,
    ) { perfume, notes, shopZustand ->
        DetailUiState(
            perfume = perfume,
            notes = notes,
            shopAngebote = shopZustand.angebote,
            shopSucheLaeuft = shopZustand.laeuft,
            shopSucheAbgerufen = shopZustand.abgerufen,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(),
    )

    private val _shopSucheHinweis = MutableSharedFlow<ShopSucheHinweis>(extraBufferCapacity = 1)
    val shopSucheHinweis: SharedFlow<ShopSucheHinweis> = _shopSucheHinweis.asSharedFlow()

    fun loeschen(onFertig: () -> Unit) {
        viewModelScope.launch {
            uiState.value.perfume?.let { repository.delete(it) }
            onFertig()
        }
    }

    fun aktivesBildUmschalten(bild: AktivesBild) {
        val perfumeId = uiState.value.perfume?.id ?: return
        viewModelScope.launch { repository.setzeAktivesBild(perfumeId, bild) }
    }

    /**
     * Bewusst nur auf expliziten Nutzer-Tap (Erstabruf-Button bzw.
     * Refresh-Icon), kein automatischer Abruf beim Öffnen des Preis-Tabs —
     * sonst würde jedes bloße Ansehen der Detailseite eine Claude-Anfrage
     * auslösen (siehe Konzeptdokument: "Momentaufnahme, kein automatisches
     * Nachfragen").
     */
    fun shopSucheStarten() {
        val perfume = uiState.value.perfume ?: return
        viewModelScope.launch {
            _shopZustand.update { it.copy(laeuft = true) }
            val apiKey = apiKeyStore.getKey()
            if (!claudeService.kannAnfragenSenden(apiKey)) {
                _shopZustand.update { it.copy(laeuft = false) }
                _shopSucheHinweis.tryEmit(ShopSucheHinweis.KeinApiKey)
                return@launch
            }
            when (val ergebnis = claudeService.sucheShops(apiKey, perfume.name, perfume.marke)) {
                is ShopSucheErgebnis.Erfolg ->
                    _shopZustand.update { ShopZustand(angebote = ergebnis.angebote, laeuft = false, abgerufen = true) }

                ShopSucheErgebnis.NichtGefunden -> {
                    _shopZustand.update { ShopZustand(angebote = emptyList(), laeuft = false, abgerufen = true) }
                    _shopSucheHinweis.tryEmit(ShopSucheHinweis.NichtGefunden)
                }

                ShopSucheErgebnis.Offline -> {
                    _shopZustand.update { it.copy(laeuft = false) }
                    _shopSucheHinweis.tryEmit(ShopSucheHinweis.Offline)
                }

                is ShopSucheErgebnis.Fehler -> {
                    _shopZustand.update { it.copy(laeuft = false) }
                    _shopSucheHinweis.tryEmit(ShopSucheHinweis.Fehler(ergebnis.nachricht))
                }
            }
        }
    }
}
