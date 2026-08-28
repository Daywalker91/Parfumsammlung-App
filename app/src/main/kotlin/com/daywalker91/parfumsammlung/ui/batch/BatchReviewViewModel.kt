package com.daywalker91.parfumsammlung.ui.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.daywalker91.parfumsammlung.data.AktivesBild
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.NotenEingabe
import com.daywalker91.parfumsammlung.data.Perfume
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.PerfumeStatus
import com.daywalker91.parfumsammlung.data.Position
import com.daywalker91.parfumsammlung.data.Saison
import com.daywalker91.parfumsammlung.data.batch.BatchEintrag
import com.daywalker91.parfumsammlung.data.batch.BatchErgebnisStore
import com.daywalker91.parfumsammlung.data.batch.PerfumeBatchWorker
import com.daywalker91.parfumsammlung.di.PerfumeSuggestionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Eine Zeile in der Review-Liste — editierbare Kopie der Vorschlagsdaten, nicht der Vorschlag selbst. */
data class BatchZeile(
    val eintrag: BatchEintrag,
    val name: String,
    val marke: String,
    val status: PerfumeStatus = PerfumeStatus.BESITZT,
    val istDuplikat: Boolean,
    val eingeschlossen: Boolean,
)

sealed interface BatchZustand {
    data object Laeuft : BatchZustand
    data class Review(val zeilen: List<BatchZeile>) : BatchZustand
    data object Fehlgeschlagen : BatchZustand
}

data class BatchReviewUiState(
    val zustand: BatchZustand = BatchZustand.Laeuft,
    val aktuell: Int = 0,
    val gesamt: Int = 0,
    val navigiereZuEditor: Boolean = false,
    val fertig: Boolean = false,
)

/**
 * Beobachtet den laufenden PerfumeBatchWorker (gleiches Muster wie zuvor
 * VergleichViewModel auf Branch KI-Vergleich) und zeigt nach Abschluss eine
 * editierbare Übersicht — bewusst KEIN automatisches Speichern, der Nutzer
 * entscheidet pro Zeile Status/Einschluss, bevor irgendwas in die DB
 * geschrieben wird (siehe Plan).
 */
class BatchReviewViewModel(
    private val workManager: WorkManager,
    private val repository: PerfumeRepository,
    private val imageStorage: ImageStorage,
    private val batchErgebnisStore: BatchErgebnisStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchReviewUiState())
    val uiState: StateFlow<BatchReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(PerfumeBatchWorker.UNIQUE_WORK_NAME).collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        _uiState.update {
                            it.copy(
                                zustand = BatchZustand.Laeuft,
                                aktuell = info.progress.getInt(PerfumeBatchWorker.KEY_PROGRESS_AKTUELL, 0),
                                gesamt = info.progress.getInt(PerfumeBatchWorker.KEY_PROGRESS_GESAMT, 0),
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        if (_uiState.value.zustand !is BatchZustand.Review) ladeErgebnisse()
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        _uiState.update { it.copy(zustand = BatchZustand.Fehlgeschlagen) }
                    }
                }
            }
        }
    }

    private suspend fun ladeErgebnisse() {
        val eintraege = withContext(Dispatchers.IO) { batchErgebnisStore.lesen() }
        val zeilen = eintraege.map { eintrag ->
            val vorschlag = eintrag.vorschlag
            val istDuplikat = vorschlag?.name != null && vorschlag.marke != null &&
                repository.findByNameAndMarke(vorschlag.name.trim(), vorschlag.marke.trim()) != null
            BatchZeile(
                eintrag = eintrag,
                name = vorschlag?.name.orEmpty(),
                marke = vorschlag?.marke.orEmpty(),
                istDuplikat = istDuplikat,
                eingeschlossen = vorschlag != null && !istDuplikat,
            )
        }
        _uiState.update { it.copy(zustand = BatchZustand.Review(zeilen)) }
    }

    private fun aktualisiereZeile(index: Int, transform: (BatchZeile) -> BatchZeile) {
        val review = _uiState.value.zustand as? BatchZustand.Review ?: return
        val neueZeilen = review.zeilen.mapIndexed { i, z -> if (i == index) transform(z) else z }
        _uiState.update { it.copy(zustand = review.copy(zeilen = neueZeilen)) }
    }

    fun nameGeaendert(index: Int, value: String) = aktualisiereZeile(index) { it.copy(name = value) }
    fun markeGeaendert(index: Int, value: String) = aktualisiereZeile(index) { it.copy(marke = value) }

    fun statusUmschalten(index: Int) = aktualisiereZeile(index) {
        it.copy(status = if (it.status == PerfumeStatus.BESITZT) PerfumeStatus.WUNSCHLISTE else PerfumeStatus.BESITZT)
    }

    fun eingeschlossenUmschalten(index: Int) = aktualisiereZeile(index) { it.copy(eingeschlossen = !it.eingeschlossen) }

    /** Blendet alle nicht-Duplikat-Zeilen mit Vorschlag ein/aus — Duplikate bleiben immer ausgeschlossen. */
    fun alleUmschalten(einschliessen: Boolean) {
        val review = _uiState.value.zustand as? BatchZustand.Review ?: return
        val neueZeilen = review.zeilen.map { z ->
            if (z.eintrag.vorschlag != null && !z.istDuplikat) z.copy(eingeschlossen = einschliessen) else z
        }
        _uiState.update { it.copy(zustand = review.copy(zeilen = neueZeilen)) }
    }

    /**
     * Fallback für fehlgeschlagene Erkennung oder größeren Korrekturbedarf —
     * exakt derselbe Weg wie der Einzel-Flow (AddChoiceViewModel): das Foto
     * geht nie verloren, läuft stattdessen über den normalen Editor-Save.
     * Die Zeile verlässt danach die Batch-Liste (wird dort nicht mehr
     * berücksichtigt, auch nicht beim abschließenden Aufräumen).
     */
    fun oeffneImEditor(index: Int) {
        val review = _uiState.value.zustand as? BatchZustand.Review ?: return
        val zeile = review.zeilen.getOrNull(index) ?: return
        PerfumeSuggestionBridge.setzen(
            PerfumeSuggestionBridge.Payload(zeile.eintrag.vorschlag, zeile.eintrag.bildPfadEigen, zeile.eintrag.bildPfadStock, null),
        )
        val neueZeilen = review.zeilen.filterIndexed { i, _ -> i != index }
        _uiState.update { it.copy(zustand = review.copy(zeilen = neueZeilen), navigiereZuEditor = true) }
    }

    fun navigationErledigt() = _uiState.update { it.copy(navigiereZuEditor = false) }

    /** Speichert alle eingeschlossenen, nicht-doppelten Zeilen, räumt die übrigen Bilder auf. */
    fun uebernehmen() {
        val review = _uiState.value.zustand as? BatchZustand.Review ?: return
        viewModelScope.launch {
            val zumSpeichern = review.zeilen.filter { it.eingeschlossen && !it.istDuplikat && it.eintrag.vorschlag != null }
            val zumAufraeumen = review.zeilen.filterNot { it in zumSpeichern }

            zumSpeichern.forEach { zeile ->
                val vorschlag = zeile.eintrag.vorschlag ?: return@forEach
                val perfume = Perfume(
                    name = zeile.name.trim(),
                    marke = zeile.marke.trim(),
                    beschreibung = vorschlag.beschreibung,
                    uvp = vorschlag.uvp,
                    status = zeile.status,
                    flakongroesse = vorschlag.flakongroesse,
                    verfuegbareGroessen = vorschlag.verfuegbareGroessen,
                    saison = vorschlag.saison?.let { Saison.ausLabel(it) },
                    bildPfadEigen = zeile.eintrag.bildPfadEigen,
                    bildPfadStock = zeile.eintrag.bildPfadStock,
                    aktivesBild = AktivesBild.EIGEN,
                )
                val notenZuordnung = vorschlag.notenKopf.map { NotenEingabe(it, Position.KOPF) } +
                    vorschlag.notenHerz.map { NotenEingabe(it, Position.HERZ) } +
                    vorschlag.notenBasis.map { NotenEingabe(it, Position.BASIS) }
                repository.insert(perfume, notenZuordnung)
            }

            withContext(Dispatchers.IO) {
                zumAufraeumen.forEach { zeile ->
                    imageStorage.loesche(zeile.eintrag.bildPfadEigen)
                    imageStorage.loesche(zeile.eintrag.bildPfadStock)
                }
            }
            batchErgebnisStore.leeren()
            _uiState.update { it.copy(fertig = true) }
        }
    }
}
