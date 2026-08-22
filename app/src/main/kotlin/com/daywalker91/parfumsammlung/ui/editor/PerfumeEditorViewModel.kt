package com.daywalker91.parfumsammlung.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daywalker91.parfumsammlung.data.AktivesBild
import com.daywalker91.parfumsammlung.data.BildDownloader
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.NotenEingabe
import com.daywalker91.parfumsammlung.data.Perfume
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.PerfumeStatus
import com.daywalker91.parfumsammlung.data.Position
import com.daywalker91.parfumsammlung.data.Saison
import com.daywalker91.parfumsammlung.data.claude.ClaudeApiKeyStore
import com.daywalker91.parfumsammlung.data.claude.ClaudeService
import com.daywalker91.parfumsammlung.data.claude.ErkennungErgebnis
import com.daywalker91.parfumsammlung.data.model.PerfumeSuggestion
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ergebnis eines "Daten aktualisieren"-Versuchs — analog zu AddHinweis im Foto-Flow,
 * aber als einmaliges Toast-Ereignis statt blockierendem Dialog (der Editor-Inhalt
 * bleibt ja nutzbar, das betrifft nur den Aktualisierungs-Versuch selbst). */
sealed interface AktualisierungsHinweis {
    data object KeinFoto : AktualisierungsHinweis
    data object KeinApiKey : AktualisierungsHinweis
    data object Offline : AktualisierungsHinweis
    data object NichtGenugDaten : AktualisierungsHinweis
    data object Erfolgreich : AktualisierungsHinweis
    data object KeinStockBildGefunden : AktualisierungsHinweis
    data object StockBildDownloadFehlgeschlagen : AktualisierungsHinweis
    data class Fehler(val nachricht: String) : AktualisierungsHinweis
}

data class PerfumeEditorUiState(
    val name: String = "",
    val marke: String = "",
    val beschreibung: String = "",
    val uvp: String = "",
    val status: PerfumeStatus = PerfumeStatus.BESITZT,
    val flakongroesse: String = "",
    val ean: String = "",
    val verfuegbareGroessen: String = "",
    val notiz: String = "",
    val bewertung: Int? = null,
    val saison: Saison? = null,
    val noten: List<NotenEingabe> = emptyList(),
    val bildPfadEigen: String? = null,
    val bildPfadStock: String? = null,
    val aktivesBild: AktivesBild? = null,
    val nameFehler: Boolean = false,
    val markeFehler: Boolean = false,
    val duplikat: Perfume? = null,
    val gespeichert: Boolean = false,
    val aktualisierungLaeuft: Boolean = false,
)

class PerfumeEditorViewModel(
    private val perfumeId: Long?,
    private val repository: PerfumeRepository,
    private val imageStorage: ImageStorage,
    private val claudeService: ClaudeService,
    private val apiKeyStore: ClaudeApiKeyStore,
    private val bildDownloader: BildDownloader,
    /** Vom Foto-Hinzufügen-Flow: das für die Erkennung genutzte Foto, schon lokal gespeichert. */
    initialBildPfadEigen: String? = null,
    /** Vom Foto-Hinzufügen-Flow: per Websuche gefundenes, schon lokal gespeichertes Stock-Bild. */
    initialBildPfadStock: String? = null,
    /** Von Claude gelieferter Erkennungsvorschlag — nur bei Neuanlage relevant. */
    vorschlag: PerfumeSuggestion? = null,
    /** Vorab gescannter Barcode aus dem Foto-Hinzufügen-Flow. */
    initialEan: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PerfumeEditorUiState(
            name = vorschlag?.name.orEmpty(),
            marke = vorschlag?.marke.orEmpty(),
            beschreibung = vorschlag?.beschreibung.orEmpty(),
            uvp = vorschlag?.uvp?.toString().orEmpty(),
            flakongroesse = vorschlag?.flakongroesse.orEmpty(),
            ean = initialEan.orEmpty(),
            verfuegbareGroessen = vorschlag?.verfuegbareGroessen.orEmpty(),
            saison = vorschlag?.saison?.let { Saison.ausLabel(it) },
            noten = (vorschlag?.notenKopf.orEmpty().map { NotenEingabe(it, Position.KOPF) } +
                vorschlag?.notenHerz.orEmpty().map { NotenEingabe(it, Position.HERZ) } +
                vorschlag?.notenBasis.orEmpty().map { NotenEingabe(it, Position.BASIS) }),
            bildPfadEigen = initialBildPfadEigen,
            bildPfadStock = initialBildPfadStock,
            aktivesBild = initialBildPfadEigen?.let { AktivesBild.EIGEN } ?: initialBildPfadStock?.let { AktivesBild.STOCK },
        ),
    )
    val uiState: StateFlow<PerfumeEditorUiState> = _uiState.asStateFlow()

    private val _aktualisierungsHinweis = MutableSharedFlow<AktualisierungsHinweis>(extraBufferCapacity = 1)
    val aktualisierungsHinweis: SharedFlow<AktualisierungsHinweis> = _aktualisierungsHinweis.asSharedFlow()

    init {
        perfumeId?.let { id ->
            viewModelScope.launch {
                val perfume = repository.getById(id) ?: return@launch
                val noten = repository.observeNotesForPerfume(id).first()
                _uiState.value = PerfumeEditorUiState(
                    name = perfume.name,
                    marke = perfume.marke,
                    beschreibung = perfume.beschreibung.orEmpty(),
                    uvp = perfume.uvp?.toString().orEmpty(),
                    status = perfume.status,
                    flakongroesse = perfume.flakongroesse.orEmpty(),
                    ean = perfume.ean.orEmpty(),
                    verfuegbareGroessen = perfume.verfuegbareGroessen.orEmpty(),
                    notiz = perfume.notiz.orEmpty(),
                    bewertung = perfume.bewertung,
                    saison = perfume.saison,
                    noten = noten.map { NotenEingabe(it.name, it.position) },
                    bildPfadEigen = perfume.bildPfadEigen,
                    bildPfadStock = perfume.bildPfadStock,
                    aktivesBild = perfume.aktivesBild,
                )
            }
        }
        // Beim Fund über den Foto-Flow (vorschlag != null) parallel nach einem
        // vorhandenen Eintrag mit gleichem Name/Marke suchen, sobald beide
        // Felder von Claude geliefert wurden — derselbe Duplikat-Dialog wie
        // beim manuellen Speichern, nur schon vor dem ersten Tastendruck.
        if (perfumeId == null && vorschlag?.name != null && vorschlag.marke != null) {
            viewModelScope.launch {
                repository.findByNameAndMarke(vorschlag.name.trim(), vorschlag.marke.trim())?.let { treffer ->
                    _uiState.update { it.copy(duplikat = treffer) }
                }
            }
        }
    }

    fun nameGeaendert(value: String) = _uiState.update { it.copy(name = value, nameFehler = false) }
    fun markeGeaendert(value: String) = _uiState.update { it.copy(marke = value, markeFehler = false) }
    fun beschreibungGeaendert(value: String) = _uiState.update { it.copy(beschreibung = value) }
    fun uvpGeaendert(value: String) = _uiState.update { it.copy(uvp = value) }
    fun statusGeaendert(value: PerfumeStatus) = _uiState.update { it.copy(status = value) }
    fun flakongroesseGeaendert(value: String) = _uiState.update { it.copy(flakongroesse = value) }
    fun eanGeaendert(value: String) = _uiState.update { it.copy(ean = value) }
    fun verfuegbareGroessenGeaendert(value: String) = _uiState.update { it.copy(verfuegbareGroessen = value) }
    fun notizGeaendert(value: String) = _uiState.update { it.copy(notiz = value) }
    fun bewertungGeaendert(value: Int?) = _uiState.update { it.copy(bewertung = value) }
    fun saisonGeaendert(value: Saison?) = _uiState.update { it.copy(saison = value) }

    fun noteHinzufuegen(name: String, position: Position) {
        if (name.isBlank()) return
        _uiState.update { it.copy(noten = it.noten + NotenEingabe(name.trim(), position)) }
    }

    fun noteEntfernen(eingabe: NotenEingabe) {
        _uiState.update { it.copy(noten = it.noten - eingabe) }
    }

    /** Übernimmt ein neu aufgenommenes/ausgewähltes eigenes Foto (komprimiert lokal gespeichert). */
    fun eigenesFotoGewaehlt(uri: Uri) {
        viewModelScope.launch {
            val altesBild = _uiState.value.bildPfadEigen
            val neuerPfad = withContext(Dispatchers.IO) { imageStorage.speichereVonUri(uri) } ?: return@launch
            withContext(Dispatchers.IO) { imageStorage.loesche(altesBild) }
            _uiState.update {
                it.copy(bildPfadEigen = neuerPfad, aktivesBild = it.aktivesBild ?: AktivesBild.EIGEN)
            }
        }
    }

    fun aktivesBildGesetzt(bild: AktivesBild) = _uiState.update { it.copy(aktivesBild = bild) }

    /** Dreht das aktuell angezeigte Bild (eigenes Foto oder Stock-Bild) um 90°. */
    fun bildDrehen() {
        val state = _uiState.value
        val aktiv = state.aktivesBild ?: return
        val pfad = when (aktiv) {
            AktivesBild.EIGEN -> state.bildPfadEigen
            AktivesBild.STOCK -> state.bildPfadStock
        } ?: return
        viewModelScope.launch {
            val neuerPfad = withContext(Dispatchers.IO) { imageStorage.drehe90Grad(pfad) } ?: return@launch
            _uiState.update {
                when (aktiv) {
                    AktivesBild.EIGEN -> it.copy(bildPfadEigen = neuerPfad)
                    AktivesBild.STOCK -> it.copy(bildPfadStock = neuerPfad)
                }
            }
        }
    }

    /**
     * Fragt Claude erneut nach Beschreibung/UVP/Größen/Duftpyramide/Stock-Bild
     * für das aktuelle eigene Foto ab und überschreibt die entsprechenden
     * Felder. Braucht ein eigenes Foto (dasselbe wie beim ursprünglichen
     * Erkennen) — Name/Marke/Notiz/Bewertung/Status bleiben unangetastet.
     * Grund: Claude-Antworten sind nicht deterministisch, ein zweiter Versuch
     * kann andere (oder vollständigere) Daten liefern als der erste.
     */
    fun datenAktualisieren() {
        val state = _uiState.value
        val bildPfad = state.bildPfadEigen
        if (bildPfad == null) {
            _aktualisierungsHinweis.tryEmit(AktualisierungsHinweis.KeinFoto)
            return
        }
        viewModelScope.launch {
            val apiKey = apiKeyStore.getKey()
            if (!claudeService.kannAnfragenSenden(apiKey)) {
                _aktualisierungsHinweis.tryEmit(AktualisierungsHinweis.KeinApiKey)
                return@launch
            }
            _uiState.update { it.copy(aktualisierungLaeuft = true) }
            val bildBytes = withContext(Dispatchers.IO) { File(bildPfad).readBytes() }
            when (val ergebnis = claudeService.erkennePerfum(apiKey, bildBytes, state.ean.trim().ifBlank { null })) {
                is ErkennungErgebnis.Erfolg -> {
                    val vorschlag = ergebnis.vorschlag
                    val stockUrl = vorschlag.stockBildUrl
                    val neuesBildPfadStock = stockUrl?.let { url ->
                        bildDownloader.laden(url)?.let { bytes ->
                            withContext(Dispatchers.IO) { imageStorage.speichereVonBytes(bytes) }
                        }
                    }
                    // Altes Stock-Bild ersetzen statt anhäufen, wenn ein neues gefunden wurde.
                    if (neuesBildPfadStock != null) {
                        withContext(Dispatchers.IO) { imageStorage.loesche(state.bildPfadStock) }
                    }
                    _uiState.update {
                        it.copy(
                            beschreibung = vorschlag.beschreibung ?: it.beschreibung,
                            uvp = vorschlag.uvp?.toString() ?: it.uvp,
                            flakongroesse = vorschlag.flakongroesse ?: it.flakongroesse,
                            verfuegbareGroessen = vorschlag.verfuegbareGroessen ?: it.verfuegbareGroessen,
                            saison = vorschlag.saison?.let { label -> Saison.ausLabel(label) } ?: it.saison,
                            noten = if (vorschlag.notenKopf.isEmpty() && vorschlag.notenHerz.isEmpty() && vorschlag.notenBasis.isEmpty()) {
                                it.noten
                            } else {
                                vorschlag.notenKopf.map { n -> NotenEingabe(n, Position.KOPF) } +
                                    vorschlag.notenHerz.map { n -> NotenEingabe(n, Position.HERZ) } +
                                    vorschlag.notenBasis.map { n -> NotenEingabe(n, Position.BASIS) }
                            },
                            bildPfadStock = neuesBildPfadStock ?: it.bildPfadStock,
                            aktualisierungLaeuft = false,
                        )
                    }
                    _aktualisierungsHinweis.tryEmit(
                        when {
                            stockUrl == null -> AktualisierungsHinweis.KeinStockBildGefunden
                            neuesBildPfadStock == null -> AktualisierungsHinweis.StockBildDownloadFehlgeschlagen
                            else -> AktualisierungsHinweis.Erfolgreich
                        },
                    )
                }

                ErkennungErgebnis.NichtGenugDaten -> {
                    _uiState.update { it.copy(aktualisierungLaeuft = false) }
                    _aktualisierungsHinweis.tryEmit(AktualisierungsHinweis.NichtGenugDaten)
                }

                ErkennungErgebnis.Offline -> {
                    _uiState.update { it.copy(aktualisierungLaeuft = false) }
                    _aktualisierungsHinweis.tryEmit(AktualisierungsHinweis.Offline)
                }

                is ErkennungErgebnis.Fehler -> {
                    _uiState.update { it.copy(aktualisierungLaeuft = false) }
                    _aktualisierungsHinweis.tryEmit(AktualisierungsHinweis.Fehler(ergebnis.nachricht))
                }
            }
        }
    }

    fun speichern() {
        val state = _uiState.value
        if (state.name.isBlank() || state.marke.isBlank()) {
            _uiState.update {
                it.copy(nameFehler = state.name.isBlank(), markeFehler = state.marke.isBlank())
            }
            return
        }
        viewModelScope.launch {
            // Duplikat-Check nur beim Neuanlegen — beim Bearbeiten ist der
            // Eintrag selbst schon der "Treffer".
            if (perfumeId == null) {
                val vorhandenes = repository.findByNameAndMarke(state.name.trim(), state.marke.trim())
                if (vorhandenes != null) {
                    _uiState.update { it.copy(duplikat = vorhandenes) }
                    return@launch
                }
            }
            tatsaechlichSpeichern(zielId = perfumeId)
        }
    }

    fun duplikatAlsUpdateSpeichern() {
        val duplikat = _uiState.value.duplikat ?: return
        viewModelScope.launch { tatsaechlichSpeichern(zielId = duplikat.id) }
    }

    fun duplikatTrotzdemNeuAnlegen() {
        viewModelScope.launch { tatsaechlichSpeichern(zielId = null) }
    }

    fun duplikatDialogSchliessen() = _uiState.update { it.copy(duplikat = null) }

    private suspend fun tatsaechlichSpeichern(zielId: Long?) {
        val state = _uiState.value
        // Bei bestehenden Einträgen wird auf Basis des geladenen Datensatzes
        // kopiert, damit erstellt_am erhalten bleibt statt von frischen
        // Default-Werten überschrieben zu werden. Die Bildfelder kommen
        // bewusst explizit aus dem UI-State (nicht aus `basis`), da sie durch
        // eigenesFotoGewaehlt()/aktivesBildGesetzt() im State selbst schon
        // aktuell gehalten werden.
        val basis = zielId?.let { repository.getById(it) } ?: Perfume(name = "", marke = "", status = state.status)
        val perfume = basis.copy(
            name = state.name.trim(),
            marke = state.marke.trim(),
            beschreibung = state.beschreibung.trim().ifBlank { null },
            uvp = state.uvp.replace(',', '.').toDoubleOrNull(),
            status = state.status,
            flakongroesse = state.flakongroesse.trim().ifBlank { null },
            ean = state.ean.trim().ifBlank { null },
            verfuegbareGroessen = state.verfuegbareGroessen.trim().ifBlank { null },
            notiz = state.notiz.trim().ifBlank { null },
            bewertung = state.bewertung,
            saison = state.saison,
            bildPfadEigen = state.bildPfadEigen,
            bildPfadStock = state.bildPfadStock,
            aktivesBild = state.aktivesBild,
        )
        if (zielId != null) {
            repository.update(perfume, state.noten)
        } else {
            repository.insert(perfume, state.noten)
        }
        _uiState.update { it.copy(duplikat = null, gespeichert = true) }
    }
}
