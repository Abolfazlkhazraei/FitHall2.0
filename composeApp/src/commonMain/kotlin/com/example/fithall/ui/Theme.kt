package com.example.fithall.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1E8E5A)
private val GreenLight = Color(0xFF6FDBA4)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F2DA),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4F6355),
    tertiary = Color(0xFFE07A2E),
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFFC9F2DA),
    secondary = Color(0xFFB6CCBA),
    tertiary = Color(0xFFFFB68A),
)

/** Colors used for macro bars so they read the same everywhere. */
object MacroColors {
    val calories = Color(0xFFE07A2E)
    val protein = Color(0xFF3B82F6)
    val carbs = Color(0xFF1E8E5A)
    val fat = Color(0xFFD9A400)
}

@Composable
fun FitHallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
