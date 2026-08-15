package com.daywalker91.parfumsammlung.ui.devoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.daywalker91.parfumsammlung.data.update.ApkDownloader
import com.daywalker91.parfumsammlung.data.update.UpdateChannel
import com.daywalker91.parfumsammlung.data.update.UpdateChannelStore
import com.daywalker91.parfumsammlung.data.update.UpdateCheckErgebnis
import com.daywalker91.parfumsammlung.data.update.UpdateChecker
import com.daywalker91.parfumsammlung.ui.update.UpdateDialog
import com.daywalker91.parfumsammlung.ui.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevOptionsScreen(
    updateChecker: UpdateChecker,
    apkDownloader: ApkDownloader,
    updateChannelStore: UpdateChannelStore,
    onBack: () -> Unit,
) {
    var kanal by remember { mutableStateOf(updateChannelStore.getChannel()) }

    val updateViewModel: UpdateViewModel = viewModel(
        factory = viewModelFactory {
            initializer { UpdateViewModel(updateChecker, apkDownloader, updateChannelStore) }
        },
    )
    val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.entwickler_optionen)) },
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
            Text(stringResource(R.string.update_kanal), style = MaterialTheme.typography.titleMedium)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = kanal == UpdateChannel.STABLE,
                    onClick = {
                        kanal = UpdateChannel.STABLE
                        updateChannelStore.setChannel(UpdateChannel.STABLE)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.kanal_stable)) }
                SegmentedButton(
                    selected = kanal == UpdateChannel.EXPERIMENTAL,
                    onClick = {
                        kanal = UpdateChannel.EXPERIMENTAL
                        updateChannelStore.setChannel(UpdateChannel.EXPERIMENTAL)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.kanal_experimental)) }
            }

            Text(stringResource(R.string.update_kanal_erklaerung), style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = updateViewModel::manuellPruefen,
                modifier = Modifier.fillMaxWidth(),
                enabled = !updateUiState.manuellerCheckLaeuft,
            ) { Text(stringResource(R.string.nach_updates_suchen)) }

            updateUiState.manuellerCheckErgebnis?.let { ergebnis ->
                Text(
                    text = when (ergebnis) {
                        is UpdateCheckErgebnis.UpdateVerfuegbar ->
                            stringResource(R.string.update_verfuegbar_titel, ergebnis.info.versionName)
                        UpdateCheckErgebnis.KeinUpdateVerfuegbar -> stringResource(R.string.kein_update_verfuegbar)
                        is UpdateCheckErgebnis.Fehler -> ergebnis.nachricht
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    UpdateDialog(uiState = updateUiState, viewModel = updateViewModel)
}
