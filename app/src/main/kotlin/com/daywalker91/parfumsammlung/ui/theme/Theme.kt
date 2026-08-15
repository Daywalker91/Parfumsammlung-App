package com.daywalker91.parfumsammlung.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Amber40,
    secondary = AmberGrey40,
    tertiary = Gold40,
)

private val DarkColors = darkColorScheme(
    primary = Amber80,
    secondary = AmberGrey80,
    tertiary = Gold80,
)

@Composable
fun AromathekTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic Color (Material You) auf Android 12+ bevorzugt, sonst eigene Palette.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
