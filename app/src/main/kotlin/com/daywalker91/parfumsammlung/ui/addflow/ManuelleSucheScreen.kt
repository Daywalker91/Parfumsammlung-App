package com.daywalker91.parfumsammlung.ui.addflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daywalker91.parfumsammlung.R
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.gemini.GeminiApiKeyStore
import com.daywalker91.parfumsammlung.data.gemini.GeminiService
import com.daywalker91.parfumsammlung.data.gemini.PerfumeKandidat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManuelleSucheScreen(
    geminiService: GeminiService,
    apiKeyStore: GeminiApiKeyStore,
    imageStorage: ImageStorage,
    onNavigateToEditor: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: ManuelleSucheViewModel = viewModel(
        factory = viewModelFactory { initializer { ManuelleSucheViewModel(geminiService, apiKeyStore, imageStorage) } },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val aktuelleNavigation by rememberUpdatedState(onNavigateToEditor)
    LaunchedEffect(uiState.navigiereZuEditor) {
        if (uiState.navigiereZuEditor) {
            viewModel.navigationErledigt()
            aktuelleNavigation()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manuelle_suche_titel)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.zurueck))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.suchtext,
                onValueChange = viewModel::suchtextGeaendert,
                label = { Text(stringResource(R.string.manuelle_suche_feld)) },
                singleLine = true,
                enabled = !uiState.ladeVorgang,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::suchen,
                enabled = !uiState.ladeVorgang && uiState.suchtext.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.manuelle_suche_suchen)) }

            if (uiState.ladeVorgang) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    TextButton(onClick = viewModel::abbrechen) { Text(stringResource(R.string.abbrechen)) }
                }
            }

            if (uiState.kandidaten.isNotEmpty()) {
                Text(stringResource(R.string.manuelle_suche_kandidaten_titel), style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.kandidaten) { kandidat ->
                        KandidatCard(kandidat = kandidat, onClick = { viewModel.kandidatUebernehmen(kandidat) })
                    }
                }
            }
        }
    }

    uiState.bestaetigungKandidat?.let { kandidat ->
        AlertDialog(
            onDismissRequest = viewModel::bestaetigungAbgelehnt,
            title = { Text(stringResource(R.string.manuelle_suche_bestaetigung_titel)) },
            text = { Text(stringResource(R.string.manuelle_suche_bestaetigung_text, kandidat.name, kandidat.marke)) },
            confirmButton = {
                TextButton(onClick = { viewModel.kandidatUebernehmen(kandidat) }) {
                    Text(stringResource(R.string.manuelle_suche_ja))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::bestaetigungAbgelehnt) { Text(stringResource(R.string.manuelle_suche_nein)) }
            },
        )
    }

    uiState.hinweis?.let { hinweis ->
        AlertDialog(
            onDismissRequest = viewModel::hinweisSchliessen,
            title = { Text(hinweisTitel(hinweis)) },
            text = { Text(hinweisText(hinweis)) },
            confirmButton = {
                TextButton(onClick = viewModel::hinweisSchliessen) { Text(stringResource(R.string.verstanden)) }
            },
        )
    }
}

@Composable
private fun KandidatCard(kandidat: PerfumeKandidat, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = kandidat.name, style = MaterialTheme.typography.titleMedium)
            Text(text = kandidat.marke, style = MaterialTheme.typography.bodyMedium)
            kandidat.kurzhinweis?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun hinweisTitel(hinweis: SucheHinweis): String = when (hinweis) {
    SucheHinweis.KeinApiKey -> stringResource(R.string.hinweis_kein_api_key_titel)
    SucheHinweis.Offline -> stringResource(R.string.hinweis_offline_titel)
    SucheHinweis.NichtGefunden -> stringResource(R.string.hinweis_nicht_gefunden_titel)
    is SucheHinweis.Fehler -> stringResource(R.string.hinweis_fehler_titel)
}

@Composable
private fun hinweisText(hinweis: SucheHinweis): String = when (hinweis) {
    SucheHinweis.KeinApiKey -> stringResource(R.string.manuelle_suche_hinweis_kein_api_key_text)
    SucheHinweis.Offline -> stringResource(R.string.hinweis_offline_text)
    SucheHinweis.NichtGefunden -> stringResource(R.string.hinweis_nicht_gefunden_text)
    is SucheHinweis.Fehler -> hinweis.nachricht
}
