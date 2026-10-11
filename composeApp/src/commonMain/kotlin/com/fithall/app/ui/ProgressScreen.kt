package com.fithall.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fithall.app.AppState
import com.fithall.app.AppViewModel
import com.fithall.app.domain.ProgressCalculator
import com.fithall.app.model.UnitSystem
import com.fithall.app.model.Units
import com.fithall.app.model.WeightEntry
import com.fithall.app.model.WorkoutLog

@Composable
fun ProgressScreen(state: AppState, viewModel: AppViewModel) {
    val logs = state.workoutLogs
    val units = state.profile?.units ?: UnitSystem.METRIC
    val stats = remember(logs, state.today) { ProgressCalculator.weeklyStats(logs, state.today) }
    var deleting by remember { mutableStateOf<WorkoutLog?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeroHeader(title = "Progress", subtitle = "Your last 7 days") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderStat(
                        Icons.Filled.FitnessCenter,
                        "${stats.workoutsLast7Days}/${state.profile?.trainingDaysPerWeek ?: "-"}",
                        "workouts",
                        Modifier.weight(1f),
                    )
                    HeaderStat(
                        Icons.Filled.LocalFireDepartment,
                        "${stats.weekStreak}",
                        if (stats.weekStreak == 1) "week streak" else "weeks streak",
                        Modifier.weight(1f),
                    )
                    HeaderStat(Icons.Filled.Insights, formatVolume(stats.volumeLast7DaysKg, units), "lifted", Modifier.weight(1f))
                }
            }
        }
        item { Padded { CheckInSection(state, viewModel) } }
        item { Padded { BodyWeightCard(state.weights, units, onLog = viewModel::logWeight, onDelete = viewModel::deleteWeight) } }
        item { Padded { ExerciseProgressCard(logs, units) } }

        item { Padded { SectionHeader("Workout history") } }
        if (logs.isEmpty()) {
            item {
                Padded {
                    EmptyState(
                        Icons.Filled.FitnessCenter,
                        "No workouts yet",
                        "Start one from the Workouts tab and tick off sets as you go.",
                    )
                }
            }
        }
        items(logs.sortedByDescending { it.startedAtMillis }, key = { it.id }) { log ->
            Padded { HistoryCard(log, units, onDelete = { deleting = log }) }
        }
    }

    deleting?.let { log ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this workout?") },
            text = { Text("${shortDate(log.dayKey)} · ${log.title}") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteWorkoutLog(log.id); deleting = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** A stat inside the hero header: white on a translucent tile. */
@Composable
private fun HeaderStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = Brand.onGradient, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Brand.onGradient, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.onGradientMuted, maxLines = 1)
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BodyWeightCard(weights: List<WeightEntry>, units: UnitSystem, onLog: (Double) -> Unit, onDelete: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val parsed = input.replace(',', '.').toDoubleOrNull()?.let { Units.displayToKg(it, units) }?.takeIf { it in 30.0..300.0 }
    val unit = Units.weightUnit(units)

    ContentCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Body weight", style = MaterialTheme.typography.titleMedium)
            val latest = weights.lastOrNull()
            if (latest != null) {
                val change = latest.weightKg - weights.first().weightKg
                Text(
                    formatWeight(latest.weightKg, units) +
                        if (weights.size > 1) " · ${if (change >= 0) "+" else ""}${formatWeight(change, units)} since ${shortDate(weights.first().dayKey)}" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (weights.size >= 2) {
                LineChart(
                    values = weights.map { Units.kgToDisplay(it.weightKg, units) },
                    labels = weights.map { shortDate(it.dayKey) },
                    unit = unit,
                    color = MacroColors.protein,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input,
                    { new -> input = new.filter { it.isDigit() || it == '.' || it == ',' }.take(5) },
                    label = { Text("Today's weight ($unit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { parsed?.let(onLog); input = "" }, enabled = parsed != null) { Text("Log") }
            }
            Text(
                "Logging your weight also updates your calorie and macro targets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            weights.takeLast(5).asReversed().forEach { entry ->
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shortDate(entry.dayKey), modifier = Modifier.weight(1f))
                    Text(formatWeight(entry.weightKg, units))
                    IconButton(onClick = { onDelete(entry.dayKey) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete weight for ${shortDate(entry.dayKey)}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseProgressCard(logs: List<WorkoutLog>, units: UnitSystem) {
    val exercises = remember(logs) { ProgressCalculator.trainedExercises(logs) }
    if (exercises.isEmpty()) return
    var chosen by rememberSaveable { mutableStateOf(exercises.first()) }
    val selected = chosen.takeIf { it in exercises } ?: exercises.first()
    val progress = remember(logs, selected) { ProgressCalculator.exerciseProgress(logs, selected) }

    ContentCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Exercise progress", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                exercises.take(12).forEach { name ->
                    FilterChip(selected == name, { chosen = name }, label = { Text(name) })
                }
            }
            val unit = if (progress.weighted) Units.weightUnit(units) else "reps"
            fun shown(value: Double) = if (progress.weighted) Units.kgToDisplay(value, units) else value
            val metric = if (progress.weighted) "Best estimated 1-rep max" else "Best reps in a set"
            Text("$metric: ${formatNumber(shown(progress.best))} $unit", style = MaterialTheme.typography.bodyLarge)
            if (progress.points.size >= 2) {
                val change = shown(progress.change)
                Text(
                    "${if (change >= 0) "+" else ""}${formatNumber(change)} $unit over ${progress.points.size} sessions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (change >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                LineChart(
                    values = progress.points.map { shown(it.value) },
                    labels = progress.points.map { shortDate(it.dayKey) },
                    unit = unit,
                )
            } else {
                Text(
                    "Log this exercise again to see a trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(log: WorkoutLog, units: UnitSystem, onDelete: () -> Unit) {
    var expanded by rememberSaveable(log.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.clickable { expanded = !expanded }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(log.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            shortDate(log.dayKey),
                            log.durationMinutes?.let { "$it min" },
                            "${log.completedSets} sets",
                            log.volumeKg.takeIf { it > 0 }?.let { formatWeight(it, units) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete workout") }
            }
            if (expanded) {
                log.exercises.forEach { ex ->
                    Text(
                        "${ex.name}: " + ex.sets.joinToString(", ") { s ->
                            formatSet(s.weightKg, s.reps, units, repsSuffix = " reps")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                    )
                }
                if (log.notes.isNotBlank()) {
                    Text(log.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, end = 12.dp))
                }
            }
        }
    }
}
