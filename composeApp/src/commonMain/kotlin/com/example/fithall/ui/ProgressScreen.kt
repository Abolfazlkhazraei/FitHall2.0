package com.example.fithall.ui

import androidx.compose.animation.animateContentSize
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
import com.example.fithall.AppState
import com.example.fithall.AppViewModel
import com.example.fithall.domain.ProgressCalculator
import com.example.fithall.model.WeightEntry
import com.example.fithall.model.WorkoutLog

@Composable
fun ProgressScreen(state: AppState, viewModel: AppViewModel) {
    val logs = state.workoutLogs
    val stats = remember(logs, state.today) { ProgressCalculator.weeklyStats(logs, state.today) }
    var deleting by remember { mutableStateOf<WorkoutLog?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Progress", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    "${stats.workoutsLast7Days}/${state.profile?.trainingDaysPerWeek ?: "-"}",
                    "workouts, last 7 days",
                    Modifier.weight(1f),
                )
                StatCard("${stats.weekStreak}", if (stats.weekStreak == 1) "week streak" else "weeks streak", Modifier.weight(1f))
                StatCard("${formatNumber(stats.volumeLast7DaysKg / 1000)} t", "lifted, last 7 days", Modifier.weight(1f))
            }
        }
        item { BodyWeightCard(state.weights, onLog = viewModel::logWeight, onDelete = viewModel::deleteWeight) }
        item { ExerciseProgressCard(logs) }

        item { Text("Workout history", style = MaterialTheme.typography.titleMedium) }
        if (logs.isEmpty()) {
            item {
                Text(
                    "No workouts logged yet. Start one from the Workouts tab and tick off sets as you go.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(logs.sortedByDescending { it.startedAtMillis }, key = { it.id }) { log ->
            HistoryCard(log, onDelete = { deleting = log })
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
private fun BodyWeightCard(weights: List<WeightEntry>, onLog: (Double) -> Unit, onDelete: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val parsed = input.replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..300.0 }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Body weight", style = MaterialTheme.typography.titleMedium)
            val latest = weights.lastOrNull()
            if (latest != null) {
                val change = latest.weightKg - weights.first().weightKg
                Text(
                    "${formatNumber(latest.weightKg)} kg" +
                        if (weights.size > 1) " · ${if (change >= 0) "+" else ""}${formatNumber(change)} kg since ${shortDate(weights.first().dayKey)}" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (weights.size >= 2) {
                LineChart(
                    values = weights.map { it.weightKg },
                    labels = weights.map { shortDate(it.dayKey) },
                    unit = "kg",
                    color = MacroColors.protein,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input,
                    { new -> input = new.filter { it.isDigit() || it == '.' || it == ',' }.take(5) },
                    label = { Text("Today's weight (kg)") },
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
                    Text("${formatNumber(entry.weightKg)} kg")
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
private fun ExerciseProgressCard(logs: List<WorkoutLog>) {
    val exercises = remember(logs) { ProgressCalculator.trainedExercises(logs) }
    if (exercises.isEmpty()) return
    var chosen by rememberSaveable { mutableStateOf(exercises.first()) }
    val selected = chosen.takeIf { it in exercises } ?: exercises.first()
    val progress = remember(logs, selected) { ProgressCalculator.exerciseProgress(logs, selected) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Exercise progress", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                exercises.take(12).forEach { name ->
                    FilterChip(selected == name, { chosen = name }, label = { Text(name) })
                }
            }
            val unit = if (progress.weighted) "kg" else "reps"
            val metric = if (progress.weighted) "Best estimated 1-rep max" else "Best reps in a set"
            Text("$metric: ${formatNumber(progress.best)} $unit", style = MaterialTheme.typography.bodyLarge)
            if (progress.points.size >= 2) {
                val change = progress.change
                Text(
                    "${if (change >= 0) "+" else ""}${formatNumber(change)} $unit over ${progress.points.size} sessions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (change >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                LineChart(
                    values = progress.points.map { it.value },
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
private fun HistoryCard(log: WorkoutLog, onDelete: () -> Unit) {
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
                            log.volumeKg.takeIf { it > 0 }?.let { "${formatNumber(it)} kg" },
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
                            if (s.weightKg != null && s.weightKg > 0) "${formatNumber(s.weightKg)}×${s.reps}" else "${s.reps} reps"
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
