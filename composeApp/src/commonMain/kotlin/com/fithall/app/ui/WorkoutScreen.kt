package com.fithall.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fithall.app.AppState
import com.fithall.app.AppViewModel
import com.fithall.app.model.WorkoutDay

@Composable
fun WorkoutScreen(state: AppState, viewModel: AppViewModel) {
    val plan = state.workoutPlan
    val profile = state.profile ?: return
    state.activeWorkout?.let { session ->
        ActiveWorkoutScreen(session, state.workoutLogs, profile.units, viewModel)
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Workout plan", style = MaterialTheme.typography.headlineSmall) }
        item {
            Text(
                "${profile.experience.label} · ${profile.goal.label} · ${profile.trainingDaysPerWeek} days/week · " +
                    "${profile.sessionMinutes} min · ${profile.equipment.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Above the plan rather than instead of it, so a failed regeneration doesn't hide the current plan.
        state.workoutPlanFailure?.takeIf { !state.generatingWorkout }?.let { failure ->
            item { ErrorCard(failure, onRetry = viewModel::generateWorkoutPlan, onDismiss = viewModel::dismissFailures) }
        }
        when {
            state.generatingWorkout -> item {
                LoadingCard("Your AI coach is designing a plan for your level and goal. This can take up to a minute…")
            }
            plan == null -> item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Get a training plan built around your experience, activity level, equipment and goal.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = viewModel::generateWorkoutPlan, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate my plan")
                    }
                }
            }
            else -> {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(plan.title, style = MaterialTheme.typography.titleLarge)
                            Text("${plan.durationWeeks}-week program", style = MaterialTheme.typography.labelLarge)
                            Text(plan.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                itemsIndexed(plan.days) { index, day ->
                    WorkoutDayCard(day, initiallyExpanded = index == 0, onStart = { viewModel.startWorkout(day) })
                }
                item {
                    OutlinedButton(onClick = viewModel::startEmptyWorkout, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Log a custom workout")
                    }
                }
                item { InfoCard("How to progress", plan.progression) }
                if (plan.safetyNotes.isNotBlank()) item { InfoCard("Safety", plan.safetyNotes) }
                item {
                    OutlinedButton(onClick = viewModel::generateWorkoutPlan, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate a new plan")
                    }
                }
                item {
                    Text(
                        "Want changes? Ask the Coach tab, e.g. \"swap squats for something knee-friendly\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutDayCard(day: WorkoutDay, initiallyExpanded: Boolean, onStart: () -> Unit) {
    var expanded by rememberSaveable(day.dayName) { mutableStateOf(initiallyExpanded) }
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.clickable { expanded = !expanded }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.dayName, style = MaterialTheme.typography.titleMedium)
                    Text("${day.focus} · ${day.durationMinutes} min", style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (expanded) {
                if (day.warmup.isNotBlank()) {
                    Text("Warm-up: ${day.warmup}", style = MaterialTheme.typography.bodySmall)
                }
                day.exercises.forEach { ex ->
                    HorizontalDivider()
                    Row {
                        Text(ex.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${ex.sets} × ${ex.reps}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "Rest ${ex.restSeconds}s" + if (ex.notes.isNotBlank()) " · ${ex.notes}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start this workout")
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
