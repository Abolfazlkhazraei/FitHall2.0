package com.fithall.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * Backgrounds for full-screen and highlighted panels. Light text on them passes AA. The names are
 * kept from the earlier gradient look; both are now flat, since the accents do the work.
 */
object Brand {
    /** The app's ground, for full-screen pages such as sign-in. */
    val gradient: Brush = SolidColor(Palette.ground)
    /** A deep blue panel for coach suggestions such as the weekly check-in. */
    val warmGradient: Brush = SolidColor(Palette.blueDeep)
    val onGradient = Palette.text
    val onGradientMuted = Palette.muted
}

/** Dark text on light accents (lime, blue), light text on dark ones. */
fun contentOn(color: Color): Color = if (color.luminance() > 0.45f) Palette.onLime else Palette.text

/**
 * The top of each tab: a small uppercase subtitle over a big title, and optional content (stats,
 * rings) in a raised card underneath. It pads itself below the status bar.
 */
@Composable
fun HeroHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                subtitle?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Brand.onGradientMuted,
                        letterSpacing = 0.06.em,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Brand.onGradient)
            }
            trailing?.invoke()
        }
        content?.let {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Palette.surface)
                    .border(1.dp, Palette.line, MaterialTheme.shapes.extraLarge)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = it,
            )
        }
    }
}

/** A ring that fills to [progress] (0..1, animated), with [content] in the middle. */
@Composable
fun ProgressRing(
    progress: Float,
    size: Dp,
    stroke: Dp,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
            if (animated > 0f) {
                drawArc(
                    color, -90f, 360f * animated, false, Offset(inset, inset), arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** A small ring for one macro: grams eaten in the middle, label and target underneath. */
@Composable
fun MacroRing(label: String, eaten: Int, target: Int, color: Color, onGradient: Boolean = false) {
    val track = color.copy(alpha = 0.16f)
    val text = if (onGradient) Brand.onGradient else MaterialTheme.colorScheme.onSurface
    val muted = if (onGradient) Brand.onGradientMuted else MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressRing(
            progress = if (target == 0) 0f else eaten.toFloat() / target,
            size = 64.dp, stroke = 7.dp, color = color, trackColor = track,
        ) {
            Text("$eaten", style = MaterialTheme.typography.titleSmall, color = text, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = text)
        Text("of ${target}g", style = MaterialTheme.typography.labelSmall, color = muted)
    }
}

/** A rounded icon in a tinted circle. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A big, colourful button tile with an icon and label. */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (enabled) Palette.raised else Palette.surface,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) accent else accent.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = contentOn(accent), modifier = Modifier.size(24.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A compact stat: icon, big value, small label. */
@Composable
fun StatTile(icon: ImageVector, value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconBadge(icon, accent, size = 32.dp)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** A section title with an optional action on the right. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** A card with a flat tonal surface and generous padding; the base for most content blocks. */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, Palette.line),
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** A small rounded label, e.g. "520 kcal". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** Empty state: a big tinted icon with a short message. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, accent: Color = MaterialTheme.colorScheme.primary) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconBadge(icon, accent, size = 64.dp)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Fixed width spacer helper for rows. */
@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))
