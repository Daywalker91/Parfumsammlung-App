package com.daywalker91.parfumsammlung.ui.editor

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import coil3.compose.AsyncImage
import com.daywalker91.parfumsammlung.R
import com.daywalker91.parfumsammlung.data.AktivesBild
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.NotenEingabe
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.PerfumeStatus
import com.daywalker91.parfumsammlung.data.Position
import com.daywalker91.parfumsammlung.data.Saison
import com.daywalker91.parfumsammlung.data.gemini.GeminiApiKeyStore
import com.daywalker91.parfumsammlung.data.gemini.GeminiService
import com.daywalker91.parfumsammlung.data.gemini.PerfumeSuggestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfumeEditorScreen(
    perfumeId: Long?,
    repository: PerfumeRepository,
    imageStorage: ImageStorage,
    geminiService: GeminiService,
    apiKeyStore: GeminiApiKeyStore,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    initialBildPfadEigen: String? = null,
    initialBildPfadStock: String? = null,
    vorschlag: PerfumeSuggestion? = null,
    initialEan: String? = null,
) {
    val viewModel: PerfumeEditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PerfumeEditorViewModel(
                    perfumeId, repository, imageStorage, geminiService, apiKeyStore,
                    initialBildPfadEigen, initialBildPfadStock, vorschlag, initialEan,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (uiState.gespeichert) {
        onSaved()
        return
    }

    LaunchedEffect(Unit) {
        viewModel.aktualisierungsHinweis.collect { hinweis ->
            Toast.makeText(context, aktualisierungsHinweisText(context, hinweis), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (perfumeId == null) R.string.neues_parfum else R.string.parfum_bearbeiten,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.zurueck))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::nameGeaendert,
                label = { Text(stringResource(R.string.feld_name)) },
                isError = uiState.nameFehler,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.marke,
                onValueChange = viewModel::markeGeaendert,
                label = { Text(stringResource(R.string.feld_marke)) },
                isError = uiState.markeFehler,
                modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.status == PerfumeStatus.BESITZT,
                    onClick = { viewModel.statusGeaendert(PerfumeStatus.BESITZT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.status_besitzt)) }
                SegmentedButton(
                    selected = uiState.status == PerfumeStatus.WUNSCHLISTE,
                    onClick = { viewModel.statusGeaendert(PerfumeStatus.WUNSCHLISTE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.status_wunschliste)) }
            }

            BildAuswahlSektion(
                bildPfadEigen = uiState.bildPfadEigen,
                bildPfadStock = uiState.bildPfadStock,
                aktivesBild = uiState.aktivesBild,
                imageStorage = imageStorage,
                onFotoGewaehlt = viewModel::eigenesFotoGewaehlt,
                onAktivesBildGeaendert = viewModel::aktivesBildGesetzt,
                onBildDrehen = viewModel::bildDrehen,
            )

            // Gemini-Antworten sind nicht deterministisch — ein zweiter
            // Versuch mit demselben Foto kann andere/vollständigere Daten
            // liefern. Braucht dasselbe eigene Foto wie die ursprüngliche
            // Erkennung, deshalb nur sichtbar wenn eins vorhanden ist.
            if (uiState.bildPfadEigen != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::datenAktualisieren, enabled = !uiState.aktualisierungLaeuft) {
                        Text(stringResource(R.string.daten_aktualisieren))
                    }
                    if (uiState.aktualisierungLaeuft) {
                        CircularProgressIndicator()
                    }
                }
            }

            OutlinedTextField(
                value = uiState.beschreibung,
                onValueChange = viewModel::beschreibungGeaendert,
                label = { Text(stringResource(R.string.feld_beschreibung)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.uvp,
                onValueChange = viewModel::uvpGeaendert,
                label = { Text(stringResource(R.string.feld_uvp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.flakongroesse,
                onValueChange = viewModel::flakongroesseGeaendert,
                label = { Text(stringResource(R.string.feld_flakongroesse)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.verfuegbareGroessen,
                onValueChange = viewModel::verfuegbareGroessenGeaendert,
                label = { Text(stringResource(R.string.feld_verfuegbare_groessen)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.ean,
                onValueChange = viewModel::eanGeaendert,
                label = { Text(stringResource(R.string.feld_ean)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.feld_saison), style = MaterialTheme.typography.titleMedium)
            // Bewusst Row+weight statt FlowRow: alle drei Chips bleiben immer in
            // einer Zeile und teilen sich die Breite gleichmäßig, statt dass der
            // dritte Chip auf eine zweite Zeile umbricht (Nutzer-Feedback,
            // gleiche Anpassung wie bei den Saison-Chips in CollectionScreen).
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Saison.entries.forEach { saison ->
                    FilterChip(
                        selected = uiState.saison == saison,
                        onClick = {
                            viewModel.saisonGeaendert(if (uiState.saison == saison) null else saison)
                        },
                        label = {
                            Text(
                                text = saison.label(),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            BewertungAuswahl(bewertung = uiState.bewertung, onBewertungGeaendert = viewModel::bewertungGeaendert)

            OutlinedTextField(
                value = uiState.notiz,
                onValueChange = viewModel::notizGeaendert,
                label = { Text(stringResource(R.string.feld_notiz)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.duftpyramide), style = MaterialTheme.typography.titleMedium)
            NotenEditor(
                noten = uiState.noten,
                onNoteHinzufuegen = viewModel::noteHinzufuegen,
                onNoteEntfernen = viewModel::noteEntfernen,
            )

            Button(onClick = viewModel::speichern, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.speichern))
            }
        }
    }

    uiState.duplikat?.let { duplikat ->
        AlertDialog(
            onDismissRequest = viewModel::duplikatDialogSchliessen,
            title = { Text(stringResource(R.string.duplikat_titel)) },
            text = { Text(stringResource(R.string.duplikat_text, duplikat.name, duplikat.marke)) },
            confirmButton = {
                TextButton(onClick = viewModel::duplikatAlsUpdateSpeichern) {
                    Text(stringResource(R.string.duplikat_aktualisieren))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::duplikatTrotzdemNeuAnlegen) {
                    Text(stringResource(R.string.duplikat_neu_anlegen))
                }
            },
        )
    }
}

@Composable
private fun BildAuswahlSektion(
    bildPfadEigen: String?,
    bildPfadStock: String?,
    aktivesBild: AktivesBild?,
    imageStorage: ImageStorage,
    onFotoGewaehlt: (Uri) -> Unit,
    onAktivesBildGeaendert: (AktivesBild) -> Unit,
    onBildDrehen: () -> Unit,
) {
    val angezeigterPfad = when (aktivesBild) {
        AktivesBild.STOCK -> bildPfadStock ?: bildPfadEigen
        else -> bildPfadEigen ?: bildPfadStock
    }

    var kameraZielUri by remember { mutableStateOf<Uri?>(null) }
    val kameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { erfolgreich -> if (erfolgreich) kameraZielUri?.let(onFotoGewaehlt) }
    val kameraBerechtigungLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { erlaubt ->
        if (erlaubt) {
            val ziel = imageStorage.neueKameraZielUri()
            kameraZielUri = ziel
            kameraLauncher.launch(ziel)
        }
    }
    val galerieLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onFotoGewaehlt) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.foto), style = MaterialTheme.typography.titleMedium)

        if (angezeigterPfad != null) {
            AsyncImage(
                model = angezeigterPfad,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }

        if (bildPfadEigen != null && bildPfadStock != null) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = aktivesBild == AktivesBild.EIGEN,
                    onClick = { onAktivesBildGeaendert(AktivesBild.EIGEN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.eigenes_foto)) }
                SegmentedButton(
                    selected = aktivesBild == AktivesBild.STOCK,
                    onClick = { onAktivesBildGeaendert(AktivesBild.STOCK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.stock_bild)) }
            }
        }

        // Jeder Button per weight(1f) gleich breit — teilen sich die Zeile fair
        // auf, statt dass (wie vorher mit einer normalen Row ohne weight) der
        // letzte Button auf eine winzige Restbreite zusammengequetscht wird.
        // Kompaktere Innenabstände + kleinere Schrift, damit auch die längeren
        // Labels ("Aus Galerie wählen") bei drei Buttons in einer Zeile noch
        // vernünftig (max. zweizeilig, nicht buchstabenweise) umbrechen.
        val fotoButtonPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { kameraBerechtigungLauncher.launch(android.Manifest.permission.CAMERA) },
                contentPadding = fotoButtonPadding,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.foto_aufnehmen), style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(
                onClick = {
                    galerieLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                contentPadding = fotoButtonPadding,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.aus_galerie_waehlen), style = MaterialTheme.typography.labelMedium) }
            if (angezeigterPfad != null) {
                OutlinedButton(
                    onClick = onBildDrehen,
                    contentPadding = fotoButtonPadding,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.foto_drehen), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun BewertungAuswahl(bewertung: Int?, onBewertungGeaendert: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "${stringResource(R.string.feld_bewertung)}: ", modifier = Modifier.wrapContentWidth())
        (1..5).forEach { stern ->
            IconButton(onClick = { onBewertungGeaendert(if (bewertung == stern) null else stern) }) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (bewertung != null && stern <= bewertung) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun NotenEditor(
    noten: List<NotenEingabe>,
    onNoteHinzufuegen: (String, Position) -> Unit,
    onNoteEntfernen: (NotenEingabe) -> Unit,
) {
    var neueNote by remember { mutableStateOf("") }
    var neuePosition by remember { mutableStateOf(Position.HERZ) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Position.KOPF to R.string.position_kopf,
                Position.HERZ to R.string.position_herz,
                Position.BASIS to R.string.position_basis,
            ).forEach { (position, labelRes) ->
                FilterChip(
                    selected = neuePosition == position,
                    onClick = { neuePosition = position },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = neueNote,
                onValueChange = { neueNote = it },
                label = { Text(stringResource(R.string.note_hinzufuegen)) },
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Button(onClick = {
                onNoteHinzufuegen(neueNote, neuePosition)
                neueNote = ""
            }) { Text(stringResource(R.string.hinzufuegen)) }
        }

        noten.forEach { eingabe ->
            InputChip(
                selected = false,
                onClick = {},
                label = { Text("${eingabe.name} (${positionLabel(eingabe.position)})") },
                trailingIcon = {
                    IconButton(onClick = { onNoteEntfernen(eingabe) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.entfernen))
                    }
                },
            )
        }
    }
}

/** Nicht-@Composable, da innerhalb eines LaunchedEffect (Coroutine) aufgerufen —
 * dort ist stringResource() nicht erlaubt, context.getString() aber schon. */
private fun aktualisierungsHinweisText(context: Context, hinweis: AktualisierungsHinweis): String = when (hinweis) {
    AktualisierungsHinweis.KeinFoto -> context.getString(R.string.hinweis_aktualisieren_kein_foto)
    AktualisierungsHinweis.KeinApiKey -> context.getString(R.string.hinweis_kein_api_key_titel)
    AktualisierungsHinweis.Offline -> context.getString(R.string.hinweis_offline_titel)
    AktualisierungsHinweis.NichtGenugDaten -> context.getString(R.string.hinweis_nicht_genug_daten_titel)
    AktualisierungsHinweis.Erfolgreich -> context.getString(R.string.hinweis_aktualisierung_erfolgreich)
    AktualisierungsHinweis.KeinStockBildGefunden -> context.getString(R.string.hinweis_kein_stock_bild_gefunden)
    AktualisierungsHinweis.StockBildDownloadFehlgeschlagen -> context.getString(R.string.hinweis_stock_bild_download_fehlgeschlagen)
    is AktualisierungsHinweis.Fehler -> hinweis.nachricht
}

@Composable
private fun positionLabel(position: Position): String = stringResource(
    when (position) {
        Position.KOPF -> R.string.position_kopf
        Position.HERZ -> R.string.position_herz
        Position.BASIS -> R.string.position_basis
    },
)
