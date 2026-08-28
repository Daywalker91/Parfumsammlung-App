package com.daywalker91.parfumsammlung.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.daywalker91.parfumsammlung.data.GatewayAccessCodeStore
import com.daywalker91.parfumsammlung.data.SortMode
import com.daywalker91.parfumsammlung.data.SortPreferenceStore
import com.daywalker91.parfumsammlung.data.UsageCounterStore
import com.daywalker91.parfumsammlung.data.backup.BackupManager
import com.daywalker91.parfumsammlung.data.claude.ClaudeApiKeyStore
import com.daywalker91.parfumsammlung.data.claude.ClaudeService
import com.daywalker91.parfumsammlung.data.claude.GatewayStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CLAUDE_CONSOLE_URL = "https://console.anthropic.com/settings/keys"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKeyStore: ClaudeApiKeyStore,
    backupManager: BackupManager,
    sortPreferenceStore: SortPreferenceStore,
    usageCounterStore: UsageCounterStore,
    gatewayAccessCodeStore: GatewayAccessCodeStore,
    claudeService: ClaudeService,
    onBack: () -> Unit,
    onDevOptionsClick: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    apiKeyStore,
                    backupManager,
                    sortPreferenceStore,
                    usageCounterStore,
                    gatewayAccessCodeStore,
                    claudeService,
                )
            }
        },
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
            Text(stringResource(R.string.claude_key_erklaerung), style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::apiKeyGeaendert,
                label = { Text(stringResource(R.string.claude_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            // Komfort statt Automatisierung: eine echte Console-Auto-Provisionierung
            // ist nicht möglich (Anthropic sperrt OAuth-Flows serverseitig auf Claude
            // Code/Claude.ai, siehe Plan) — hier nur Browser-Shortcut + Zwischenablage.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CLAUDE_CONSOLE_URL)))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.claude_console_oeffnen)) }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                        if (!text.isNullOrBlank()) viewModel.apiKeyGeaendert(text.trim())
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.aus_zwischenablage_einfuegen)) }
            }

            // Alternative zum eigenen Key: individueller Lizenzschlüssel (Lizenz-Gateway-Plan) —
            // greift nur, wenn oben kein eigener Key hinterlegt ist (ClaudeService entscheidet).
            OutlinedTextField(
                value = uiState.lizenzschluessel,
                onValueChange = viewModel::lizenzschluesselGeaendert,
                label = { Text(stringResource(R.string.lizenzschluessel_feld)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.lizenzschluessel_erklaerung), style = MaterialTheme.typography.bodySmall)

            when (val status = uiState.gatewayStatus) {
                is GatewayStatus.Verfuegbar -> Text(
                    stringResource(R.string.gateway_status_verfuegbar, status.verbleibendHeute),
                    style = MaterialTheme.typography.bodySmall,
                )
                GatewayStatus.Gesperrt -> Text(
                    stringResource(R.string.gateway_status_gesperrt),
                    style = MaterialTheme.typography.bodySmall,
                )
                GatewayStatus.KeinGateway -> Unit
            }

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

            Text(stringResource(R.string.verbrauch_titel), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.verbrauch_diesen_monat,
                    formatToken(uiState.verbrauch.tokenDiesenMonat),
                    uiState.verbrauch.anfragenDiesenMonat,
                    formatEuro(uiState.verbrauch.kostenDiesenMonatEuro),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.verbrauch_seit_zahlung,
                    formatToken(uiState.verbrauch.tokenSeitZahlung),
                    uiState.verbrauch.anfragenSeitZahlung,
                    formatEuro(uiState.verbrauch.kostenSeitZahlungEuro),
                ) + if (uiState.verbrauch.letzteZahlungMillis > 0) {
                    stringResource(
                        R.string.verbrauch_seit_datum,
                        SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(uiState.verbrauch.letzteZahlungMillis)),
                    )
                } else {
                    ""
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = viewModel::verbrauchBeglichen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.verbrauch_beglichen))
            }
            Text(stringResource(R.string.verbrauch_schaetzung_hinweis), style = MaterialTheme.typography.bodySmall)

            // Kein Backend, keine automatische Zahlungsbestätigung möglich — rein
            // manueller/Ehrlichkeits-Ablauf: Spenden-Button öffnet den Link, der
            // "Verbrauch beglichen"-Button oben wird danach von Hand bestätigt.
            // Link kommt zentral vom Gateway (über /admin gepflegt, siehe Plan) —
            // deshalb nur sichtbar, wenn gerade per Lizenzschlüssel gelaufen wird
            // (bei eigenem API-Key zahlt man ja direkt an Anthropic, kein Sinn).
            val spendenLink = (uiState.gatewayStatus as? GatewayStatus.Verfuegbar)?.spendenLink
            if (spendenLink != null) {
                Text(stringResource(R.string.spenden_hinweis), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = {
                        val betrag = uiState.verbrauch.kostenSeitZahlungEuro
                        val ziel = if (betrag > 0) {
                            "${spendenLink.trimEnd('/')}/${String.format(Locale.US, "%.2f", betrag)}EUR"
                        } else {
                            spendenLink
                        }
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ziel)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.spenden_button)) }
            }

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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.sortierung_titel), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val optionen = listOf(
                    SortMode.NAME to R.string.sortierung_name,
                    SortMode.MARKE to R.string.sortierung_marke,
                    SortMode.UVP to R.string.sortierung_preis,
                )
                optionen.forEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        selected = uiState.sortMode == mode,
                        onClick = { viewModel.sortModeGeaendert(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = optionen.size),
                    ) { Text(stringResource(labelRes)) }
                }
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

private fun formatToken(token: Long): String = String.format(Locale.GERMANY, "%,d", token)

private fun formatEuro(euro: Double): String = String.format(Locale.GERMANY, "%.2f €", euro)

/** Nicht-@Composable, da innerhalb eines LaunchedEffect (Coroutine) aufgerufen —
 * dort ist stringResource() nicht erlaubt, context.getString() aber schon. */
private fun backupHinweisText(context: android.content.Context, hinweis: BackupHinweis): String = when (hinweis) {
    BackupHinweis.ExportErfolgreich -> context.getString(R.string.backup_export_erfolgreich)
    BackupHinweis.ExportFehlgeschlagen -> context.getString(R.string.backup_export_fehlgeschlagen)
    BackupHinweis.ImportFehlgeschlagen -> context.getString(R.string.backup_import_fehlgeschlagen)
    is BackupHinweis.ImportErfolgreich ->
        context.getString(R.string.backup_import_erfolgreich, hinweis.importiert, hinweis.uebersprungen)
}
