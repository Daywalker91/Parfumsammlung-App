package com.daywalker91.parfumsammlung.ui.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.daywalker91.parfumsammlung.data.Perfume
import com.daywalker91.parfumsammlung.data.PerfumeRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    repository: PerfumeRepository,
    onPerfumeClick: (Long) -> Unit,
    onAddClick: () -> Unit,
) {
    val viewModel: CollectionViewModel = viewModel(
        factory = viewModelFactory { initializer { CollectionViewModel(repository) } },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var ausgewaehlterTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
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
}

@Composable
private fun PerfumeListItem(perfume: Perfume, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = perfume.name, style = MaterialTheme.typography.titleMedium)
            Text(text = perfume.marke, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
