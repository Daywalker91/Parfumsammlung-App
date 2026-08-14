package com.daywalker91.parfumsammlung.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daywalker91.parfumsammlung.data.Perfume
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.PerfumeStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CollectionUiState(
    val besitzt: List<Perfume> = emptyList(),
    val wunschliste: List<Perfume> = emptyList(),
)

class CollectionViewModel(repository: PerfumeRepository) : ViewModel() {
    val uiState: StateFlow<CollectionUiState> = combine(
        repository.observeByStatus(PerfumeStatus.BESITZT),
        repository.observeByStatus(PerfumeStatus.WUNSCHLISTE),
    ) { besitzt, wunschliste ->
        CollectionUiState(besitzt = besitzt, wunschliste = wunschliste)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionUiState(),
    )
}
