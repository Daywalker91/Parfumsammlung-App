package com.daywalker91.parfumsammlung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.daywalker91.parfumsammlung.ui.navigation.AromathekNavHost
import com.daywalker91.parfumsammlung.ui.theme.AromathekTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AromathekApplication).container

        setContent {
            AromathekTheme {
                AromathekNavHost(container = container)
            }
        }
    }
}
