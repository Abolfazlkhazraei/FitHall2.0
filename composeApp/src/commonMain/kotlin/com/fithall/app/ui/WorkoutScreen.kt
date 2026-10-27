package com.fithall.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fithall.app.AppState
import com.fithall.app.AppViewModel
import com.fithall.app.model.WorkoutDay

@OptIn(ExperimentalLayoutApi::class)
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
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeroHeader(
                title = plan?.title ?: "Your training",
                subtitle = plan?.let { "${it.durationWeeks}-week program" } ?: "Workouts",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderChip(profile.experience.label)
                    HeaderChip(profile.goal.label)
                    HeaderChip("${profile.trainingDaysPerWeek} days/week")
                    HeaderChip("${profile.sessionMinutes} min")
                    HeaderChip(profile.equipment.label)
                }
            }
        }

        // Above the plan rather than instead of it, so a failed regeneration doesn't hide the current plan.
        state.workoutPlanFailure?.takeIf { !state.generatingWorkout }?.let { failure ->
            item { Padded { ErrorCard(failure, onRetry = viewModel::generateWorkoutPlan, onDismiss = viewModel::dismissFailures) } }
        }
        when {
            state.generatingWorkout -> item {
                Padded { LoadingCard("Your AI coach is designing a plan for your level and goal…") }
            }
            plan == null -> item {
                Padded {
                    ContentCard {
                        EmptyState(
                            Icons.Filled.FitnessCenter,
                            "No plan yet",
                            "Get a training plan built around your experience, activity level, equipment and goal.",
                        )
                        Button(onClick = viewModel::generateWorkoutPlan, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate my plan")
                        }
                    }
                }
            }
            else -> {
                item { Padded { Text(plan.summary, style = MaterialTheme.typography.bodyMedium) } }
                item { Padded { SectionHeader("This week") } }
                itemsIndexed(plan.days) { index, day ->
                    Padded {
                        WorkoutDayCard(index + 1, day, initiallyExpanded = index == 0, onStart = { viewModel.startWorkout(day) })
                    }
                }
                item {
                    Padded {
                        OutlinedButton(onClick = viewModel::startEmptyWorkout, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Log a custom workout")
                        }
                    }
                }
                item { Padded { InfoCard(Icons.AutoMirrored.Filled.TrendingUp, MacroColors.carbs, "How to progress", plan.progression) } }
                if (plan.safetyNotes.isNotBlank()) {
                    item { Padded { InfoCard(Icons.Filled.HealthAndSafety, MacroColors.calories, "Safety", plan.safetyNotes) } }
                }
                item {
                    Padded {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Want changes? Ask the Coach, e.g. \"swap squats for something knee-friendly\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::generateWorkoutPlan) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New plan")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Standard horizontal page padding for content below a hero header. */
@Composable
fun Padded(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp)) { content() }
}

/** A small label for use inside a hero header card. */
@Composable
fun HeaderChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Palette.raised).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Brand.onGradient, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WorkoutDayCard(number: Int, day: WorkoutDay, initiallyExpanded: Boolean, onStart: () -> Unit) {
    var expanded by rememberSaveable(day.dayName) { mutableStateOf(initiallyExpanded) }
    ContentCard(Modifier.animateContentSize(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Palette.lime),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = MaterialTheme.typography.titleMedium, color = Palette.onLime, fontWeight = FontWeight.Bold)
            }
            HSpace(12.dp)
            Column(Modifier.weight(1f)) {
                Text(day.focus, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${day.dayName} · ${day.exercises.size} exercises",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill("${day.durationMinutes} min", MaterialTheme.colorScheme.primary)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
        }
        if (expanded) {
            if (day.warmup.isNotBlank()) {
                Text("Warm-up: ${day.warmup}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            day.exercises.forEach { ex ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ex.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "Rest ${ex.restSeconds}s" + if (ex.notes.isNotBlank()) " · ${ex.notes}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Pill("${ex.sets} × ${ex.reps}", MaterialTheme.colorScheme.tertiary)
                }
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Start this workout")
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, accent: Color, title: String, body: String) {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, accent, size = 36.dp)
            HSpace(12.dp)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
