package com.daywalker91.parfumsammlung.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daywalker91.parfumsammlung.data.NoteWithPosition
import com.daywalker91.parfumsammlung.data.Perfume
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val perfume: Perfume? = null,
    val notes: List<NoteWithPosition> = emptyList(),
)

class DetailViewModel(
    perfumeId: Long,
    private val repository: PerfumeRepository,
) : ViewModel() {

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeById(perfumeId),
        repository.observeNotesForPerfume(perfumeId),
    ) { perfume, notes ->
        DetailUiState(perfume = perfume, notes = notes)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(),
    )

    fun loeschen(onFertig: () -> Unit) {
        viewModelScope.launch {
            uiState.value.perfume?.let { repository.delete(it) }
            onFertig()
        }
    }
}
