package com.fithall.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/*
 * FitHall's look: a near-black ground, electric lime for actions and food, soft blue for the
 * coach. The app is always dark, so the bright accents carry the hierarchy. Text colours meet
 * WCAG AA on their backgrounds. The window backgrounds in res/values/colors.xml (and values-night) match `background`.
 */

/** The palette, for the few places that need a colour outside the Material roles. */
object Palette {
    val ground = Color(0xFF0A0B0D)
    val surface = Color(0xFF141619)
    val raised = Color(0xFF1C1F23)
    val line = Color(0xFF2A2E34)
    val text = Color(0xFFF4F5F2)
    val muted = Color(0xFF9BA1A8)
    val lime = Color(0xFFD4FF4F)
    val onLime = Color(0xFF0A0B0D)
    val blue = Color(0xFF6AA8FF)
    val blueDeep = Color(0xFF111A28)
    val blueLine = Color(0xFF2E3F5C)
    val danger = Color(0xFFFF9C9C)
}

private val Colors = darkColorScheme(
    primary = Palette.lime,
    onPrimary = Palette.onLime,
    primaryContainer = Color(0xFF2B3A0B),
    onPrimaryContainer = Color(0xFFE6FF9A),
    secondary = Color(0xFFC9CDD2),
    onSecondary = Palette.ground,
    secondaryContainer = Palette.raised,
    onSecondaryContainer = Palette.text,
    tertiary = Palette.blue,
    onTertiary = Palette.ground,
    tertiaryContainer = Palette.blueDeep,
    onTertiaryContainer = Color(0xFFCFE1FF),
    error = Palette.danger,
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF3A1416),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Palette.ground,
    onBackground = Palette.text,
    surface = Palette.ground,
    onSurface = Palette.text,
    surfaceVariant = Palette.raised,
    onSurfaceVariant = Palette.muted,
    outline = Color(0xFF5C636B),
    outlineVariant = Palette.line,
    inverseSurface = Palette.text,
    inverseOnSurface = Palette.ground,
    inversePrimary = Color(0xFF4B6300),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF050607),
    surfaceContainerLow = Palette.surface,
    surfaceContainer = Color(0xFF181A1E),
    surfaceContainerHigh = Palette.raised,
    surfaceContainerHighest = Color(0xFF25292E),
    surfaceBright = Color(0xFF2C3036),
    surfaceDim = Palette.ground,
)

/** Colors used for macros so they read the same everywhere. They differ in lightness, not just hue. */
object MacroColors {
    val calories = Palette.lime
    val protein = Palette.blue
    val carbs = Color(0xFFFFB86B)
    val fat = Color(0xFFE8E9E4)
}

private val FitHallShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Big, tight, heavy headings and numbers; calm body text. */
private val FitHallTypography = Typography().run {
    val tight = (-0.02).em
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = tight),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = tight),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = tight),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = tight),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = tight),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = tight),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = tight),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun FitHallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        shapes = FitHallShapes,
        typography = FitHallTypography,
        content = content,
    )
}
