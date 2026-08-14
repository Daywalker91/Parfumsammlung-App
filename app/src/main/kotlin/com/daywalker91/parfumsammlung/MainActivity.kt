package com.daywalker91.parfumsammlung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daywalker91.parfumsammlung.data.AppDatabase
import com.daywalker91.parfumsammlung.data.PerfumeStatus
import com.daywalker91.parfumsammlung.ui.theme.AromathekTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Instanziiert (und öffnet damit) die Room-DB direkt beim Start —
        // dient hier v. a. als Smoke-Test, dass Entities/Schema kompilieren
        // und zur Laufzeit funktionieren. Die eigentlichen Screens/ViewModels
        // folgen in einem späteren Schritt.
        val db = AppDatabase.getInstance(applicationContext)

        setContent {
            AromathekTheme {
                val sammlung by db.perfumeDao()
                    .observeByStatus(PerfumeStatus.BESITZT)
                    .collectAsState(initial = emptyList())

                AromathekScaffold(anzahlInSammlung = sammlung.size)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AromathekScaffold(anzahlInSammlung: Int) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (anzahlInSammlung == 0) {
                    "Noch keine Parfums in der Sammlung."
                } else {
                    "$anzahlInSammlung Parfum(s) in der Sammlung."
                },
            )
        }
    }
}
