package com.daywalker91.parfumsammlung.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.daywalker91.parfumsammlung.R
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.Position

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    perfumeId: Long,
    repository: PerfumeRepository,
    onEditClick: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(
        factory = viewModelFactory { initializer { DetailViewModel(perfumeId, repository) } },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var zeigeLoeschDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.perfume?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.zurueck))
                    }
                },
                actions = {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.bearbeiten))
                    }
                    IconButton(onClick = { zeigeLoeschDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.loeschen))
                    }
                },
            )
        },
    ) { innerPadding ->
        val perfume = uiState.perfume
        if (perfume != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = perfume.marke, style = MaterialTheme.typography.titleMedium)

                perfume.beschreibung?.let { Text(it) }

                perfume.bewertung?.let { bewertung ->
                    Text(text = "★".repeat(bewertung) + "☆".repeat(5 - bewertung))
                }

                perfume.uvp?.let { Text("${stringResource(R.string.feld_uvp)}: $it €") }
                perfume.flakongroesse?.let { Text("${stringResource(R.string.feld_flakongroesse)}: $it") }
                perfume.verfuegbareGroessen?.let {
                    Text("${stringResource(R.string.feld_verfuegbare_groessen)}: $it")
                }
                perfume.ean?.let { Text("${stringResource(R.string.feld_ean)}: $it") }

                if (uiState.notes.isNotEmpty()) {
                    Text(text = stringResource(R.string.duftpyramide), style = MaterialTheme.typography.titleMedium)
                    listOf(
                        Position.KOPF to R.string.position_kopf,
                        Position.HERZ to R.string.position_herz,
                        Position.BASIS to R.string.position_basis,
                    ).forEach { (position, labelRes) ->
                        val notesFuerPosition = uiState.notes.filter { it.position == position }
                        if (notesFuerPosition.isNotEmpty()) {
                            Text(text = "${stringResource(labelRes)}: ${notesFuerPosition.joinToString { it.name }}")
                        }
                    }
                }

                perfume.notiz?.let {
                    Text(text = stringResource(R.string.feld_notiz), style = MaterialTheme.typography.titleMedium)
                    Text(it)
                }
            }
        }
    }

    if (zeigeLoeschDialog) {
        val perfume = uiState.perfume
        AlertDialog(
            onDismissRequest = { zeigeLoeschDialog = false },
            title = { Text(stringResource(R.string.loeschen_bestaetigen_titel)) },
            text = {
                Text(
                    stringResource(
                        R.string.loeschen_bestaetigen_text,
                        perfume?.name.orEmpty(),
                        perfume?.marke.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    zeigeLoeschDialog = false
                    viewModel.loeschen(onDeleted)
                }) { Text(stringResource(R.string.loeschen)) }
            },
            dismissButton = {
                TextButton(onClick = { zeigeLoeschDialog = false }) { Text(stringResource(R.string.abbrechen)) }
            },
        )
    }
}
