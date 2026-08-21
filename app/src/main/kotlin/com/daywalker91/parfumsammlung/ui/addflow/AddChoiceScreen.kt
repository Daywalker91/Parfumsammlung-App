package com.daywalker91.parfumsammlung.ui.addflow

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkManager
import com.daywalker91.parfumsammlung.R
import com.daywalker91.parfumsammlung.data.BildDownloader
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.claude.ClaudeApiKeyStore
import com.daywalker91.parfumsammlung.data.claude.ClaudeService
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private const val BATCH_MAX_ITEMS = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChoiceScreen(
    imageStorage: ImageStorage,
    claudeService: ClaudeService,
    apiKeyStore: ClaudeApiKeyStore,
    bildDownloader: BildDownloader,
    workManager: WorkManager,
    onNavigateToEditor: () -> Unit,
    onNavigateToManuelleSuche: () -> Unit,
    onNavigateToBatchReview: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: AddChoiceViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AddChoiceViewModel(imageStorage, claudeService, apiKeyStore, bildDownloader, workManager) }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val barcodeScanner = remember { GmsBarcodeScanning.getClient(context) }

    var kameraZielUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val kameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { erfolgreich ->
        if (erfolgreich) kameraZielUri?.let(viewModel::fotoVerarbeiten)
    }
    val kameraBerechtigungLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { erlaubt ->
        if (erlaubt) {
            val ziel = imageStorage.neueKameraZielUri()
            kameraZielUri = ziel
            kameraLauncher.launch(ziel)
        }
    }
    // Automatische Weiche statt manueller Batch/Einzel-Auswahl: 1 Bild läuft
    // über den unveränderten Einzel-Flow, >1 Bild startet den Hintergrund-
    // Batch (siehe AddChoiceViewModel.galerieAuswahlVerarbeitet).
    val galerieLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(BATCH_MAX_ITEMS),
    ) { uris -> viewModel.galerieAuswahlVerarbeitet(uris) }

    val aktuelleNavigation by rememberUpdatedState(onNavigateToEditor)
    LaunchedEffect(uiState.navigiereZuEditor) {
        if (uiState.navigiereZuEditor) {
            viewModel.navigationErledigt()
            aktuelleNavigation()
        }
    }

    val aktuelleBatchNavigation by rememberUpdatedState(onNavigateToBatchReview)
    LaunchedEffect(uiState.navigiereZuBatchReview) {
        if (uiState.navigiereZuBatchReview) {
            viewModel.batchNavigationErledigt()
            aktuelleBatchNavigation()
        }
    }

    // Nur ein Toast, kein blockierender Dialog — die Haupterkennung war ja
    // trotzdem erfolgreich, das betrifft nur das optionale Stock-Bild.
    LaunchedEffect(Unit) {
        viewModel.stockBildHinweis.collect { textResId ->
            Toast.makeText(context, context.getString(textResId), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parfum_hinzufuegen)) },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            uiState.ean?.let { ean -> Text(stringResource(R.string.ean_erkannt, ean)) }

            Button(
                onClick = { kameraBerechtigungLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.ladeVorgang,
            ) { Text(stringResource(R.string.foto_aufnehmen)) }

            Button(
                onClick = {
                    galerieLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.ladeVorgang,
            ) { Text(stringResource(R.string.aus_galerie_waehlen)) }

            val barcodeFehlgeschlagenText = stringResource(R.string.barcode_fehlgeschlagen)
            OutlinedButton(
                onClick = {
                    barcodeScanner.startScan()
                        .addOnSuccessListener { barcode -> barcode.rawValue?.let(viewModel::eanErkannt) }
                        .addOnFailureListener {
                            Toast.makeText(context, barcodeFehlgeschlagenText, Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.ladeVorgang,
            ) { Text(stringResource(R.string.barcode_scannen)) }

            OutlinedButton(
                onClick = onNavigateToManuelleSuche,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.ladeVorgang,
            ) { Text(stringResource(R.string.manuelle_suche_button)) }

            OutlinedButton(
                onClick = viewModel::manuellOhneFoto,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.ladeVorgang,
            ) { Text(stringResource(R.string.manuell_eingeben)) }

            if (uiState.ladeVorgang) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    // Kein Timeout mehr auf Claude-Anfragen (siehe ClaudeService) — dafür
                    // muss der Nutzer selbst abbrechen können, statt endlos zu warten.
                    TextButton(onClick = viewModel::abbrechen) {
                        Text(stringResource(R.string.abbrechen))
                    }
                }
            }
        }
    }

    uiState.hinweis?.let { hinweis ->
        AlertDialog(
            onDismissRequest = viewModel::hinweisSchliessen,
            title = { Text(hinweisTitel(hinweis)) },
            text = { Text(hinweisText(hinweis)) },
            confirmButton = {
                TextButton(onClick = viewModel::trotzdemManuellFortfahren) {
                    Text(stringResource(R.string.manuell_eingeben))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hinweisSchliessen) { Text(stringResource(R.string.abbrechen)) }
            },
        )
    }
}

@Composable
private fun hinweisTitel(hinweis: AddHinweis): String = when (hinweis) {
    AddHinweis.KeinApiKey -> stringResource(R.string.hinweis_kein_api_key_titel)
    AddHinweis.Offline -> stringResource(R.string.hinweis_offline_titel)
    AddHinweis.NichtGenugDaten -> stringResource(R.string.hinweis_nicht_genug_daten_titel)
    is AddHinweis.Fehler -> stringResource(R.string.hinweis_fehler_titel)
}

@Composable
private fun hinweisText(hinweis: AddHinweis): String = when (hinweis) {
    AddHinweis.KeinApiKey -> stringResource(R.string.hinweis_kein_api_key_text)
    AddHinweis.Offline -> stringResource(R.string.hinweis_offline_text)
    AddHinweis.NichtGenugDaten -> stringResource(R.string.hinweis_nicht_genug_daten_text)
    is AddHinweis.Fehler -> hinweis.nachricht
}
