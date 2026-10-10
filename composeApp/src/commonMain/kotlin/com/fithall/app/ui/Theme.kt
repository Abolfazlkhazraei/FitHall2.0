package com.fithall.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Placeholder brand: FitHall green (#1E8E5A, as on the launcher icon) with a warm orange accent.
 * Tonal palettes follow Material 3; text colours meet WCAG AA contrast on their backgrounds.
 * The window backgrounds in res/values/colors.xml and res/values-night/colors.xml match `background` here.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF16774A), // a shade darker than the icon green so white text passes AA
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F1CF),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6354),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D5),
    onSecondaryContainer = Color(0xFF0C1F13),
    tertiary = Color(0xFFA04F12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBC9),
    onTertiaryContainer = Color(0xFF341100),
    background = Color(0xFFF6FBF4),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF4),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC0C9C0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EE),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE4EAE3),
    surfaceContainerHighest = Color(0xFFDFE4DD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD8A9),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF005230),
    onPrimaryContainer = Color(0xFFA6F5C4),
    secondary = Color(0xFFB5CCB9),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3D),
    onSecondaryContainer = Color(0xFFD1E8D5),
    tertiary = Color(0xFFFFB68E),
    onTertiary = Color(0xFF552100),
    tertiaryContainer = Color(0xFF7A3405),
    onTertiaryContainer = Color(0xFFFFDBC9),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDFE4DD),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDFE4DD),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8A938A),
    outlineVariant = Color(0xFF414942),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF303632),
)

/** Colors used for macro bars so they read the same everywhere. */
object MacroColors {
    val calories = Color(0xFFE07A2E)
    val protein = Color(0xFF3B82F6)
    val carbs = Color(0xFF1E8E5A)
    val fat = Color(0xFFD9A400)
}

private val FitHallShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Material 3 type scale with bolder headings, so screen titles and numbers stand out. */
private val FitHallTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun FitHallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = FitHallShapes,
        typography = FitHallTypography,
        content = content,
    )
}
