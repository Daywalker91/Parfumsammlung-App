package com.daywalker91.parfumsammlung.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daywalker91.parfumsammlung.BuildConfig
import com.daywalker91.parfumsammlung.data.update.ApkDownloader
import com.daywalker91.parfumsammlung.data.update.UpdateChannelStore
import com.daywalker91.parfumsammlung.data.update.UpdateCheckErgebnis
import com.daywalker91.parfumsammlung.data.update.UpdateChecker
import com.daywalker91.parfumsammlung.data.update.UpdateInfo
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val verfuegbaresUpdate: UpdateInfo? = null,
    val ladeFortschritt: Float? = null,
    val bereitZurInstallation: File? = null,
    val manuellerCheckLaeuft: Boolean = false,
    val manuellerCheckErgebnis: UpdateCheckErgebnis? = null,
)

class UpdateViewModel(
    private val updateChecker: UpdateChecker,
    private val apkDownloader: ApkDownloader,
    private val updateChannelStore: UpdateChannelStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** Stiller Check beim App-Start — zeigt nur bei Treffer etwas an, sonst passiert nichts sichtbares. */
    fun automatischPruefen() {
        viewModelScope.launch {
            val ergebnis = updateChecker.pruefen(updateChannelStore.getChannel(), BuildConfig.VERSION_CODE)
            if (ergebnis is UpdateCheckErgebnis.UpdateVerfuegbar) {
                _uiState.update { it.copy(verfuegbaresUpdate = ergebnis.info) }
            }
        }
    }

    /** Manueller Check aus den Entwickler-Optionen — zeigt auch "kein Update"/Fehler an. */
    fun manuellPruefen() {
        viewModelScope.launch {
            _uiState.update { it.copy(manuellerCheckLaeuft = true, manuellerCheckErgebnis = null) }
            val ergebnis = updateChecker.pruefen(updateChannelStore.getChannel(), BuildConfig.VERSION_CODE)
            _uiState.update { it.copy(manuellerCheckLaeuft = false, manuellerCheckErgebnis = ergebnis) }
            if (ergebnis is UpdateCheckErgebnis.UpdateVerfuegbar) {
                _uiState.update { it.copy(verfuegbaresUpdate = ergebnis.info) }
            }
        }
    }

    fun updateDialogSchliessen() = _uiState.update { it.copy(verfuegbaresUpdate = null) }

    fun herunterladen() {
        val info = _uiState.value.verfuegbaresUpdate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(ladeFortschritt = 0f) }
            val datei = apkDownloader.herunterladen(info.downloadUrl) { fortschritt ->
                _uiState.update { it.copy(ladeFortschritt = fortschritt) }
            }
            _uiState.update { it.copy(ladeFortschritt = null, bereitZurInstallation = datei) }
        }
    }

    fun installationAngestossen() = _uiState.update { it.copy(bereitZurInstallation = null, verfuegbaresUpdate = null) }
}
