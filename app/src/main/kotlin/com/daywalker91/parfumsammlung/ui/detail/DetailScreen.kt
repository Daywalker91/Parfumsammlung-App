package com.daywalker91.parfumsammlung.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import coil3.compose.AsyncImage
import com.daywalker91.parfumsammlung.R
import com.daywalker91.parfumsammlung.data.AktivesBild
import com.daywalker91.parfumsammlung.data.NoteWithPosition
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.Position
import com.daywalker91.parfumsammlung.data.claude.ClaudeApiKeyStore
import com.daywalker91.parfumsammlung.data.claude.ClaudeService
import com.daywalker91.parfumsammlung.data.model.ShopAngebot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    perfumeId: Long,
    repository: PerfumeRepository,
    claudeService: ClaudeService,
    apiKeyStore: ClaudeApiKeyStore,
    onEditClick: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(
        factory = viewModelFactory { initializer { DetailViewModel(perfumeId, repository, claudeService, apiKeyStore) } },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var zeigeLoeschDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.shopSucheHinweis.collect { hinweis ->
            Toast.makeText(context, shopSucheHinweisText(context, hinweis), Toast.LENGTH_LONG).show()
        }
    }

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
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // Kopfbereich — immer sichtbar, unabhängig vom gewählten Tab
                // (Nutzer-Feedback: "alles auf einen Haufen" wirkte vorher).
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val angezeigterPfad = when (perfume.aktivesBild) {
                        AktivesBild.STOCK -> perfume.bildPfadStock ?: perfume.bildPfadEigen
                        else -> perfume.bildPfadEigen ?: perfume.bildPfadStock
                    }
                    if (angezeigterPfad != null) {
                        AsyncImage(
                            model = angezeigterPfad,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    if (perfume.bildPfadEigen != null && perfume.bildPfadStock != null) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = perfume.aktivesBild == AktivesBild.EIGEN,
                                onClick = { viewModel.aktivesBildUmschalten(AktivesBild.EIGEN) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text(stringResource(R.string.eigenes_foto)) }
                            SegmentedButton(
                                selected = perfume.aktivesBild == AktivesBild.STOCK,
                                onClick = { viewModel.aktivesBildUmschalten(AktivesBild.STOCK) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text(stringResource(R.string.stock_bild)) }
                        }
                    }
                    Text(text = perfume.marke, style = MaterialTheme.typography.titleMedium)
                }

                var ausgewaehlterTab by remember { mutableIntStateOf(0) }
                val tabTitel = listOf(R.string.tab_info, R.string.tab_bewertung, R.string.tab_preis)
                SecondaryTabRow(selectedTabIndex = ausgewaehlterTab) {
                    tabTitel.forEachIndexed { index, titelRes ->
                        Tab(
                            selected = ausgewaehlterTab == index,
                            onClick = { ausgewaehlterTab = index },
                            text = { Text(stringResource(titelRes)) },
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (ausgewaehlterTab) {
                        0 -> InfoTab(perfume.beschreibung, uiState.notes)
                        1 -> BewertungTab(perfume.bewertung, perfume.notiz)
                        2 -> {
                            PreisTab(perfume.uvp, perfume.flakongroesse, perfume.verfuegbareGroessen, perfume.ean)
                            ShopSucheSektion(
                                angebote = uiState.shopAngebote,
                                laeuft = uiState.shopSucheLaeuft,
                                abgerufen = uiState.shopSucheAbgerufen,
                                onSuchen = viewModel::shopSucheStarten,
                            )
                        }
                    }
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

/** Tab 1: Beschreibung + Duftpyramide (mit kleinen Symbolen pro Note, angelehnt an parfumo.de). */
@Composable
private fun InfoTab(beschreibung: String?, notes: List<NoteWithPosition>) {
    beschreibung?.let { Text(it) }

    if (notes.isNotEmpty()) {
        Text(text = stringResource(R.string.duftpyramide), style = MaterialTheme.typography.titleMedium)
        listOf(
            Position.KOPF to R.string.position_kopf,
            Position.HERZ to R.string.position_herz,
            Position.BASIS to R.string.position_basis,
        ).forEach { (position, labelRes) ->
            val notesFuerPosition = notes.filter { it.position == position }
            if (notesFuerPosition.isNotEmpty()) {
                Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    notesFuerPosition.forEach { note -> Text("${noteEmoji(note.name)} ${note.name}") }
                }
            }
        }
    }
}

/** Tab 2: eigene Bewertung + Notiz. */
@Composable
private fun BewertungTab(bewertung: Int?, notiz: String?) {
    Text(text = stringResource(R.string.feld_bewertung), style = MaterialTheme.typography.titleMedium)
    if (bewertung != null) {
        Text(text = "★".repeat(bewertung) + "☆".repeat(5 - bewertung))
    } else {
        Text(text = stringResource(R.string.keine_bewertung), style = MaterialTheme.typography.bodyMedium)
    }

    if (notiz != null) {
        Text(text = stringResource(R.string.feld_notiz), style = MaterialTheme.typography.titleMedium)
        Text(notiz)
    }
}

/** Tab 3: Preis + Flakongröße + verfügbare Flakongrößen (+ EAN, passt inhaltlich dazu). */
@Composable
private fun PreisTab(uvp: Double?, flakongroesse: String?, verfuegbareGroessen: String?, ean: String?) {
    uvp?.let { Text("${stringResource(R.string.feld_uvp)}: $it €") }
    flakongroesse?.let { Text("${stringResource(R.string.feld_flakongroesse)}: $it") }
    verfuegbareGroessen?.let { Text("${stringResource(R.string.feld_verfuegbare_groessen)}: $it") }
    ean?.let { Text("${stringResource(R.string.feld_ean)}: $it") }
    if (uvp == null && flakongroesse == null && verfuegbareGroessen == null && ean == null) {
        Text(text = stringResource(R.string.keine_angaben), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Shop-Suche (Phase 8c) — bewusst nur auf expliziten Nutzer-Tap (Erstabruf-
 * Button bzw. Refresh-Icon), reine Momentaufnahme, wird nirgends persistiert.
 */
@Composable
private fun ShopSucheSektion(
    angebote: List<ShopAngebot>,
    laeuft: Boolean,
    abgerufen: Boolean,
    onSuchen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.shops_titel), style = MaterialTheme.typography.titleMedium)
        if (abgerufen) {
            IconButton(onClick = onSuchen, enabled = !laeuft) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.shops_suchen))
            }
        }
    }

    if (!abgerufen) {
        OutlinedButton(onClick = onSuchen, enabled = !laeuft) {
            Text(stringResource(R.string.shops_suchen))
        }
        Text(stringResource(R.string.shops_momentaufnahme_hinweis), style = MaterialTheme.typography.bodySmall)
    }

    if (laeuft) {
        CircularProgressIndicator()
    }

    if (abgerufen && !laeuft) {
        if (angebote.isEmpty()) {
            Text(stringResource(R.string.shops_keine_gefunden), style = MaterialTheme.typography.bodyMedium)
        } else {
            val uriHandler = LocalUriHandler.current
            angebote.forEach { angebot ->
                Card(
                    onClick = { angebot.link?.let { uriHandler.openUri(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = angebot.shopName, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            angebot.preis?.let { Text("$it €", style = MaterialTheme.typography.bodyMedium) }
                            angebot.verfuegbarkeit?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

/** Nicht-@Composable, da innerhalb eines LaunchedEffect (Coroutine) aufgerufen —
 * dort ist stringResource() nicht erlaubt, context.getString() aber schon. */
private fun shopSucheHinweisText(context: android.content.Context, hinweis: ShopSucheHinweis): String = when (hinweis) {
    ShopSucheHinweis.KeinApiKey -> context.getString(R.string.shops_hinweis_kein_api_key)
    ShopSucheHinweis.Offline -> context.getString(R.string.shops_hinweis_offline)
    ShopSucheHinweis.NichtGefunden -> context.getString(R.string.shops_hinweis_nicht_gefunden)
    is ShopSucheHinweis.Fehler -> hinweis.nachricht
}

/**
 * Grobe Zuordnung Notenname → Emoji-Symbol per Stichwort-Erkennung (kein
 * eigenes Icon-Set nötig, keine neue Abhängigkeit) — angelehnt an die
 * Symbole auf parfumo.de. Deckt keine Einzelfälle exakt ab, sondern gängige
 * Duftfamilien; unbekannte Noten bekommen ein neutrales Symbol.
 */
private fun noteEmoji(name: String): String {
    val n = name.lowercase()
    return when {
        listOf("zitrus", "zitrone", "limette", "orange", "mandarine", "bergamotte", "grapefruit").any { it in n } -> "🍋"
        listOf("blüte", "blume", "rose", "jasmin", "flieder", "veilchen", "neroli", "maiglöckchen", "flor").any { it in n } -> "🌸"
        listOf("meer", "maritim", "aquatisch", "aldehyd", "wasser", "ozean", "marine").any { it in n } -> "🌊"
        listOf("pfeffer", "gewürz", "kardamom", "zimt", "nelke", "ingwer", "muskat", "chili").any { it in n } -> "🌶️"
        listOf("zeder", "holz", "sandelholz", "vetiver", "eiche", "patchouli", "guajak").any { it in n } -> "🌲"
        listOf("amber", "ambra", "moschus", "leder", "tabak", "erdig").any { it in n } -> "🟤"
        listOf("vanille", "tonka", "kakao", "karamell", "honig", "praline", "mandel").any { it in n } -> "🍯"
        listOf("apfel", "birne", "pfirsich", "beere", "kirsche", "aprikose", "frucht").any { it in n } -> "🍑"
        listOf("minze", "basilikum", "salbei", "grün", "kraut", "grüntee", "gras").any { it in n } -> "🌿"
        else -> "✨"
    }
}
