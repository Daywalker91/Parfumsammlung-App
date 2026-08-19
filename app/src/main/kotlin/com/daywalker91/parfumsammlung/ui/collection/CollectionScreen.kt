package com.daywalker91.parfumsammlung.ui.collection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import coil3.compose.AsyncImage
import com.daywalker91.parfumsammlung.R
import com.daywalker91.parfumsammlung.data.FirstLaunchPrefs
import com.daywalker91.parfumsammlung.data.Perfume
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.Saison
import com.daywalker91.parfumsammlung.data.SortPreferenceStore
import com.daywalker91.parfumsammlung.data.update.ApkDownloader
import com.daywalker91.parfumsammlung.data.update.UpdateChannelStore
import com.daywalker91.parfumsammlung.data.update.UpdateChecker
import com.daywalker91.parfumsammlung.ui.update.UpdateDialog
import com.daywalker91.parfumsammlung.ui.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    repository: PerfumeRepository,
    firstLaunchPrefs: FirstLaunchPrefs,
    updateChecker: UpdateChecker,
    apkDownloader: ApkDownloader,
    updateChannelStore: UpdateChannelStore,
    sortPreferenceStore: SortPreferenceStore,
    onPerfumeClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val viewModel: CollectionViewModel = viewModel(
        factory = viewModelFactory { initializer { CollectionViewModel(repository, sortPreferenceStore) } },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var ausgewaehlterTab by remember { mutableIntStateOf(0) }
    var zeigeDatenschutzHinweis by remember { mutableStateOf(!firstLaunchPrefs.datenschutzGesehen()) }
    // Such-/Filterbereich ist standardmäßig eingeklappt (Nutzer-Feedback: nahm
    // sonst dauerhaft Platz weg) — Ziehgriff darunter zum Auf-/Zuziehen.
    var filterOffen by remember { mutableStateOf(false) }

    // Greift z. B. beim Zurückkommen aus den Einstellungen, wo der Sortiermodus
    // geändert worden sein könnte (siehe CollectionViewModel.sortModeNeuLaden).
    LaunchedEffect(Unit) { viewModel.sortModeNeuLaden() }

    val updateViewModel: UpdateViewModel = viewModel(
        factory = viewModelFactory {
            initializer { UpdateViewModel(updateChecker, apkDownloader, updateChannelStore) }
        },
    )
    val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { updateViewModel.automatischPruefen() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.einstellungen))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.hinzufuegen))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = ausgewaehlterTab) {
                Tab(
                    selected = ausgewaehlterTab == 0,
                    onClick = { ausgewaehlterTab = 0 },
                    text = { Text(stringResource(R.string.tab_sammlung, uiState.besitzt.size)) },
                )
                Tab(
                    selected = ausgewaehlterTab == 1,
                    onClick = { ausgewaehlterTab = 1 },
                    text = { Text(stringResource(R.string.tab_wunschliste, uiState.wunschliste.size)) },
                )
            }

            FilterZiehgriff(offen = filterOffen, onOffenAendern = { filterOffen = it })

            AnimatedVisibility(visible = filterOffen) {
                SuchUndFilterLeiste(
                    uiState = uiState,
                    onSuchtextAendern = viewModel::sucheAendern,
                    onMarkeFilterAendern = viewModel::markeFilterAendern,
                    onSaisonFilterAendern = viewModel::saisonFilterAendern,
                )
            }

            val angezeigtePerfumes = if (ausgewaehlterTab == 0) uiState.besitzt else uiState.wunschliste

            if (angezeigtePerfumes.isEmpty()) {
                Text(
                    text = stringResource(
                        if (ausgewaehlterTab == 0) R.string.leere_sammlung else R.string.leere_wunschliste,
                    ),
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(angezeigtePerfumes, key = { it.id }) { perfume ->
                        PerfumeListItem(perfume = perfume, onClick = { onPerfumeClick(perfume.id) })
                    }
                }
            }
        }
    }

    if (zeigeDatenschutzHinweis) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.datenschutz_hinweis_titel)) },
            text = { Text(stringResource(R.string.datenschutz_hinweis_text)) },
            confirmButton = {
                TextButton(onClick = {
                    firstLaunchPrefs.datenschutzAlsGesehenMarkieren()
                    zeigeDatenschutzHinweis = false
                }) { Text(stringResource(R.string.verstanden)) }
            },
        )
    }

    UpdateDialog(uiState = updateUiState, viewModel = updateViewModel)
}

/**
 * Schmaler Ziehgriff (angelehnt an Samsungs Edge-Panel-Griff) unterhalb der
 * Tabs — per Tap ODER per Runter-/Hochziehen wird der Such-/Filterbereich
 * auf-/zugeklappt.
 *
 * Bewusst EIN einziger, selbst geschriebener Gesture-Loop (`awaitEachGesture`)
 * statt separatem `clickable` + `detectVerticalDragGestures`: zwei
 * unabhängige Gesture-Detectoren auf demselben Element reagieren beide auf
 * denselben Tap (Compose koordiniert das nicht automatisch) — das führte
 * dazu, dass ein einzelner Tap den Zustand zweimal umschaltete (auf→zu im
 * selben Moment). Hier wird pro Geste nur EINMAL entschieden: kaum Bewegung
 * bis zum Loslassen = Tap (toggelt), spürbare Bewegung = Ziehen (öffnet/
 * schließt je nach Richtung).
 */
@Composable
private fun FilterZiehgriff(offen: Boolean, onOffenAendern: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(offen) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var verschiebung = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            verschiebung += change.positionChange().y
                            change.consume()
                        } else {
                            break
                        }
                    }
                    when {
                        verschiebung > ZIEHGRIFF_SCHWELLE_PX -> onOffenAendern(true)
                        verschiebung < -ZIEHGRIFF_SCHWELLE_PX -> onOffenAendern(false)
                        else -> onOffenAendern(!offen) // kaum Bewegung = reiner Tap
                    }
                }
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

private const val ZIEHGRIFF_SCHWELLE_PX = 20f

/** Volltextsuche über den Namen + Marke-/Saison-Filter — wirkt implizit kontextabhängig auf den gerade aktiven Tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuchUndFilterLeiste(
    uiState: CollectionUiState,
    onSuchtextAendern: (String) -> Unit,
    onMarkeFilterAendern: (String?) -> Unit,
    onSaisonFilterAendern: (Saison?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = uiState.suchtext,
            onValueChange = onSuchtextAendern,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.suche_hinweis)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        var markeMenuOffen by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = markeMenuOffen, onExpandedChange = { markeMenuOffen = it }) {
            OutlinedTextField(
                value = uiState.markeFilter ?: stringResource(R.string.filter_alle_marken),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.filter_marke)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = markeMenuOffen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            DropdownMenu(expanded = markeMenuOffen, onDismissRequest = { markeMenuOffen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.filter_alle_marken)) },
                    onClick = { onMarkeFilterAendern(null); markeMenuOffen = false },
                )
                uiState.verfuegbareMarken.forEach { marke ->
                    DropdownMenuItem(
                        text = { Text(marke) },
                        onClick = { onMarkeFilterAendern(marke); markeMenuOffen = false },
                    )
                }
            }
        }

        Text(text = stringResource(R.string.filter_saison), style = MaterialTheme.typography.labelLarge)
        // Bewusst Row+weight statt FlowRow: alle drei Chips bleiben immer in
        // einer Zeile und teilen sich die Breite gleichmäßig, statt dass der
        // dritte Chip auf eine zweite Zeile umbricht (Nutzer-Feedback). Die
        // Absicherung dafür ist maxLines=1+Ellipsis am Text, nicht eine
        // kleinere Schrift — auf sehr schmalen Bildschirmen wird der Text im
        // Zweifel gekürzt ("Frühling/S…") statt umzubrechen, die Schriftgröße
        // bleibt regulär lesbar (zweite Nutzer-Rückmeldung: erst zu klein).
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Saison.entries.forEach { saison ->
                FilterChip(
                    selected = uiState.saisonFilter == saison,
                    onClick = { onSaisonFilterAendern(if (uiState.saisonFilter == saison) null else saison) },
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
    }
}

@Composable
private fun PerfumeListItem(perfume: Perfume, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bildPfad = perfume.bildPfadEigen ?: perfume.bildPfadStock
            if (bildPfad != null) {
                AsyncImage(
                    model = bildPfad,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column {
                Text(text = perfume.name, style = MaterialTheme.typography.titleMedium)
                Text(text = perfume.marke, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
