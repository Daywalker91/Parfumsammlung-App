package com.daywalker91.parfumsammlung.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daywalker91.parfumsammlung.data.Perfume
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.PerfumeStatus
import com.daywalker91.parfumsammlung.data.Saison
import com.daywalker91.parfumsammlung.data.SortMode
import com.daywalker91.parfumsammlung.data.SortPreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CollectionUiState(
    val besitzt: List<Perfume> = emptyList(),
    val wunschliste: List<Perfume> = emptyList(),
    val verfuegbareMarken: List<String> = emptyList(),
    val suchtext: String = "",
    val markeFilter: String? = null,
    val saisonFilter: Saison? = null,
    val sortMode: SortMode = SortMode.NAME,
)

/** Kombination aus Such-/Filtertext und Sortiermodus — eigene innere Klasse nur, damit die `combine`-Kette lesbar bleibt. */
private data class FilterZustand(
    val suchtext: String,
    val marke: String?,
    val saison: Saison?,
    val sortMode: SortMode,
)

class CollectionViewModel(
    repository: PerfumeRepository,
    private val sortPreferenceStore: SortPreferenceStore,
) : ViewModel() {

    private val _suchtext = MutableStateFlow("")
    private val _markeFilter = MutableStateFlow<String?>(null)
    private val _saisonFilter = MutableStateFlow<Saison?>(null)
    private val _sortMode = MutableStateFlow(sortPreferenceStore.getSortMode())

    val uiState: StateFlow<CollectionUiState> = combine(
        repository.observeByStatus(PerfumeStatus.BESITZT),
        repository.observeByStatus(PerfumeStatus.WUNSCHLISTE),
        combine(_suchtext, _markeFilter, _saisonFilter, _sortMode) { suchtext, marke, saison, sortMode ->
            FilterZustand(suchtext, marke, saison, sortMode)
        },
    ) { besitzt, wunschliste, filterZustand ->
        CollectionUiState(
            besitzt = besitzt.gefiltertUndSortiert(filterZustand),
            wunschliste = wunschliste.gefiltertUndSortiert(filterZustand),
            verfuegbareMarken = (besitzt + wunschliste).map { it.marke }.distinct().sorted(),
            suchtext = filterZustand.suchtext,
            markeFilter = filterZustand.marke,
            saisonFilter = filterZustand.saison,
            sortMode = filterZustand.sortMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionUiState(sortMode = sortPreferenceStore.getSortMode()),
    )

    fun sucheAendern(text: String) { _suchtext.value = text }

    fun markeFilterAendern(marke: String?) { _markeFilter.value = marke }

    fun saisonFilterAendern(saison: Saison?) { _saisonFilter.value = saison }

    /** Sortiermodus ist eine globale Einstellung (siehe Konzeptdokument) — direkt mitpersistiert. */
    fun sortModeAendern(mode: SortMode) {
        sortPreferenceStore.setSortMode(mode)
        _sortMode.value = mode
    }

    private fun List<Perfume>.gefiltertUndSortiert(filterZustand: FilterZustand): List<Perfume> {
        val gefiltert = filter { perfume ->
            (filterZustand.suchtext.isBlank() || perfume.name.contains(filterZustand.suchtext, ignoreCase = true)) &&
                (filterZustand.marke == null || perfume.marke == filterZustand.marke) &&
                (filterZustand.saison == null || perfume.saison == filterZustand.saison)
        }
        return when (filterZustand.sortMode) {
            SortMode.NAME -> gefiltert.sortedBy { it.name }
            SortMode.MARKE -> gefiltert.sortedBy { it.marke }
            SortMode.UVP -> gefiltert.sortedWith(compareBy(nullsLast<Double>()) { it.uvp })
        }
    }
}
