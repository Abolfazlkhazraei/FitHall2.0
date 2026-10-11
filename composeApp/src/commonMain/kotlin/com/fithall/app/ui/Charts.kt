package com.fithall.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.fithall.app.domain.ProgressCalculator
import kotlinx.datetime.LocalDate

private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-10-10" -> "Oct 10". */
fun shortDate(dayKey: String): String = runCatching {
    val date = LocalDate.parse(dayKey)
    "${monthNames[date.month.ordinal]} ${date.day}"
}.getOrDefault(dayKey)

fun formatNumber(value: Double): String = ProgressCalculator.formatNumber(value)

/**
 * Minimal line chart: points are spread evenly left to right (one per session or entry) and
 * scaled between their own min and max, with the range and first/last labels shown as text.
 */
@Composable
fun LineChart(
    values: List<Double>,
    labels: List<String>,
    unit: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    val min = values.min()
    val max = values.max()
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "${formatNumber(min)}–${formatNumber(max)} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val pad = 6.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            val range = (max - min).takeIf { it > 0 } ?: 1.0

            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = pad + h * f
                drawLine(gridColor, Offset(pad, y), Offset(pad + w, y), strokeWidth = 1.dp.toPx())
            }

            val points = values.mapIndexed { i, v ->
                val x = if (values.size == 1) pad + w / 2 else pad + w * i / (values.size - 1)
                val y = pad + h - (((v - min) / range) * h).toFloat()
                Offset(x, y)
            }
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                // Soft gradient under the line.
                val fill = Path().apply {
                    addPath(path)
                    lineTo(points.last().x, pad + h)
                    lineTo(points.first().x, pad + h)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)), startY = pad, endY = pad + h))
                drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            points.forEach {
                drawCircle(color, radius = 5.dp.toPx(), center = it)
                drawCircle(Color.White, radius = 2.dp.toPx(), center = it)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(labels.firstOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (labels.size > 1) Text(labels.last(), style = MaterialTheme.typography.labelSmall)
        }
    }
}
