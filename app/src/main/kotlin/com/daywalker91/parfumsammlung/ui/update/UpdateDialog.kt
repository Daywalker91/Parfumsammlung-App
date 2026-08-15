package com.daywalker91.parfumsammlung.ui.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.daywalker91.parfumsammlung.R

/**
 * Deckt alle drei Zustände eines Update-Vorgangs ab (verfügbar → lädt herunter
 * → bereit zur Installation) — wird sowohl vom automatischen Start-Check
 * (CollectionScreen) als auch vom manuellen Check (Entwickler-Optionen) genutzt.
 */
@Composable
fun UpdateDialog(uiState: UpdateUiState, viewModel: UpdateViewModel) {
    val info = uiState.verfuegbaresUpdate ?: return
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (uiState.ladeFortschritt == null) viewModel.updateDialogSchliessen() },
        title = { Text(stringResource(R.string.update_verfuegbar_titel, info.versionName)) },
        text = {
            val fortschritt = uiState.ladeFortschritt
            when {
                uiState.bereitZurInstallation != null -> Text(stringResource(R.string.update_bereit_text))
                fortschritt != null -> Column {
                    Text(stringResource(R.string.update_laedt_herunter))
                    LinearProgressIndicator(
                        progress = { fortschritt },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }
                else -> Text(
                    stringResource(
                        if (info.istPrerelease) R.string.update_verfuegbar_experimental_text else R.string.update_verfuegbar_text,
                    ),
                )
            }
        },
        confirmButton = {
            val bereitDatei = uiState.bereitZurInstallation
            when {
                bereitDatei != null -> TextButton(onClick = {
                    installierenOderBerechtigungAnfordern(context, bereitDatei)
                    viewModel.installationAngestossen()
                }) { Text(stringResource(R.string.update_installieren)) }

                uiState.ladeFortschritt == null -> TextButton(onClick = viewModel::herunterladen) {
                    Text(stringResource(R.string.update_herunterladen))
                }

                else -> {}
            }
        },
        dismissButton = {
            if (uiState.ladeFortschritt == null && uiState.bereitZurInstallation == null) {
                TextButton(onClick = viewModel::updateDialogSchliessen) { Text(stringResource(R.string.spaeter)) }
            }
        },
    )
}

private fun installierenOderBerechtigungAnfordern(context: android.content.Context, datei: java.io.File) {
    if (context.packageManager.canRequestPackageInstalls()) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } else {
        // Einmaliger manueller Schritt, siehe Plan „Einmalige Nutzer-Aktion" — nicht automatisierbar.
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
