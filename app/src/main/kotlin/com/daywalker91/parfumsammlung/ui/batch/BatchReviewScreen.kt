package com.daywalker91.parfumsammlung.ui.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import com.daywalker91.parfumsammlung.R
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.PerfumeStatus
import com.daywalker91.parfumsammlung.data.batch.BatchErgebnisStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchReviewScreen(
    workManager: WorkManager,
    repository: PerfumeRepository,
    imageStorage: ImageStorage,
    batchErgebnisStore: BatchErgebnisStore,
    onFertig: () -> Unit,
    onNavigateToEditor: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: BatchReviewViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BatchReviewViewModel(workManager, repository, imageStorage, batchErgebnisStore) }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.navigiereZuEditor) {
        if (uiState.navigiereZuEditor) {
            viewModel.navigationErledigt()
            onNavigateToEditor()
        }
    }
    LaunchedEffect(uiState.fertig) {
        if (uiState.fertig) onFertig()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_review_titel)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.zurueck))
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val zustand = uiState.zustand) {
            BatchZustand.Laeuft -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    if (uiState.gesamt > 0) {
                        Text(stringResource(R.string.batch_review_laeuft, uiState.aktuell + 1, uiState.gesamt))
                    }
                }
            }

            BatchZustand.Fehlgeschlagen -> {
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
                    Text(stringResource(R.string.batch_review_fehlgeschlagen))
                }
            }

            is BatchZustand.Review -> {
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.batch_review_anzahl, zustand.zeilen.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = { viewModel.alleUmschalten(true) }) {
                            Text(stringResource(R.string.batch_review_alle_uebernehmen))
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(zustand.zeilen) { index, zeile ->
                            BatchZeileCard(
                                zeile = zeile,
                                onNameGeaendert = { viewModel.nameGeaendert(index, it) },
                                onMarkeGeaendert = { viewModel.markeGeaendert(index, it) },
                                onStatusUmschalten = { viewModel.statusUmschalten(index) },
                                onEingeschlossenUmschalten = { viewModel.eingeschlossenUmschalten(index) },
                                onImEditorOeffnen = { viewModel.oeffneImEditor(index) },
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::uebernehmen,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text(stringResource(R.string.uebernehmen)) }
                }
            }
        }
    }
}

@Composable
private fun BatchZeileCard(
    zeile: BatchZeile,
    onNameGeaendert: (String) -> Unit,
    onMarkeGeaendert: (String) -> Unit,
    onStatusUmschalten: () -> Unit,
    onEingeschlossenUmschalten: () -> Unit,
    onImEditorOeffnen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = zeile.eintrag.bildPfadEigen,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (zeile.eintrag.vorschlag == null) {
                    Text(
                        zeile.eintrag.fehler ?: stringResource(R.string.batch_review_fehler_unbekannt),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onImEditorOeffnen) { Text(stringResource(R.string.batch_review_im_editor_oeffnen)) }
                } else {
                    OutlinedTextField(
                        value = zeile.name,
                        onValueChange = onNameGeaendert,
                        label = { Text(stringResource(R.string.feld_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = zeile.marke,
                        onValueChange = onMarkeGeaendert,
                        label = { Text(stringResource(R.string.feld_marke)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (zeile.istDuplikat) {
                        Text(stringResource(R.string.batch_review_duplikat), style = MaterialTheme.typography.bodySmall)
                    } else {
                        FilterChip(
                            selected = zeile.status == PerfumeStatus.BESITZT,
                            onClick = onStatusUmschalten,
                            label = {
                                Text(
                                    stringResource(
                                        if (zeile.status == PerfumeStatus.BESITZT) {
                                            R.string.status_besitzt
                                        } else {
                                            R.string.status_wunschliste
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            if (zeile.eintrag.vorschlag != null && !zeile.istDuplikat) {
                Checkbox(checked = zeile.eingeschlossen, onCheckedChange = { onEingeschlossenUmschalten() })
            }
        }
    }
}
