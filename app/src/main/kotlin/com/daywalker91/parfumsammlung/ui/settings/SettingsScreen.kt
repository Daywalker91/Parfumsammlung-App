package com.daywalker91.parfumsammlung.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.daywalker91.parfumsammlung.BuildConfig
import com.daywalker91.parfumsammlung.R
import com.daywalker91.parfumsammlung.data.backup.BackupManager
import com.daywalker91.parfumsammlung.data.gemini.GeminiApiKeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKeyStore: GeminiApiKeyStore,
    backupManager: BackupManager,
    onBack: () -> Unit,
    onDevOptionsClick: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(apiKeyStore, backupManager) } },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.backupExportieren(context, it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.backupImportieren(context, it) } }

    LaunchedEffect(Unit) {
        viewModel.backupHinweis.collect { hinweis ->
            Toast.makeText(context, backupHinweisText(context, hinweis), Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.einstellungen)) },
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.gemini_key_erklaerung), style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::apiKeyGeaendert,
                label = { Text(stringResource(R.string.gemini_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = viewModel::speichern, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.speichern))
            }

            if (uiState.geradeGespeichert) {
                Text(stringResource(R.string.gespeichert), style = MaterialTheme.typography.bodySmall)
            }

            Text(
                text = stringResource(R.string.datenschutz_hinweis_text),
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.backup_titel), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_erklaerung), style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val dateiname = "aromathek-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())}.zip"
                        exportLauncher.launch(dateiname)
                    },
                    enabled = !uiState.backupLaeuft,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.backup_sichern)) }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !uiState.backupLaeuft,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.backup_wiederherstellen)) }
            }
            if (uiState.backupLaeuft) {
                CircularProgressIndicator()
            }

            Text(
                text = stringResource(R.string.version_anzeige, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .clickable(onClick = viewModel::versionZeileGetippt),
            )

            if (uiState.versionZeilenTaps == 10) {
                val text = stringResource(R.string.entwickler_optionen_aktiviert)
                LaunchedEffect(Unit) { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
            }

            if (uiState.versionZeilenTaps >= 10) {
                Text(
                    text = stringResource(R.string.entwickler_optionen),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDevOptionsClick)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** Nicht-@Composable, da innerhalb eines LaunchedEffect (Coroutine) aufgerufen —
 * dort ist stringResource() nicht erlaubt, context.getString() aber schon. */
private fun backupHinweisText(context: android.content.Context, hinweis: BackupHinweis): String = when (hinweis) {
    BackupHinweis.ExportErfolgreich -> context.getString(R.string.backup_export_erfolgreich)
    BackupHinweis.ExportFehlgeschlagen -> context.getString(R.string.backup_export_fehlgeschlagen)
    BackupHinweis.ImportFehlgeschlagen -> context.getString(R.string.backup_import_fehlgeschlagen)
    is BackupHinweis.ImportErfolgreich ->
        context.getString(R.string.backup_import_erfolgreich, hinweis.importiert, hinweis.uebersprungen)
}
