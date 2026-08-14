package com.daywalker91.parfumsammlung.ui.settings

import androidx.lifecycle.ViewModel
import com.daywalker91.parfumsammlung.data.gemini.GeminiApiKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val apiKey: String = "",
    val geradeGespeichert: Boolean = false,
)

class SettingsViewModel(private val apiKeyStore: GeminiApiKeyStore) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(apiKey = apiKeyStore.getKey().orEmpty()))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun apiKeyGeaendert(value: String) = _uiState.update { it.copy(apiKey = value, geradeGespeichert = false) }

    fun speichern() {
        val key = _uiState.value.apiKey.trim()
        if (key.isBlank()) apiKeyStore.clearKey() else apiKeyStore.setKey(key)
        _uiState.update { it.copy(geradeGespeichert = true) }
    }
}
