package com.fithall.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.fithall.app.AppState
import com.fithall.app.AppViewModel
import com.fithall.app.domain.CheckInCalculator
import com.fithall.app.domain.MacroCalculator
import com.fithall.app.model.CheckInStatus
import com.fithall.app.model.TrainingChange
import com.fithall.app.model.WeeklyCheckIn

/** Top of the Progress tab: prompt, pending review, or the latest result. */
@Composable
fun CheckInSection(state: AppState, viewModel: AppViewModel) {
    val pending = state.pendingCheckIn
    when {
        state.runningCheckIn -> LoadingCard("Your coach is reviewing your week…")
        pending != null -> PendingCheckInCard(pending, state, viewModel)
        state.checkInDue -> DueCard(onRun = viewModel::runCheckIn)
        else -> LatestCheckInCard(state, onRun = viewModel::runCheckIn)
    }
}

@Composable
private fun DueCard(onRun: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your weekly check-in is ready", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your coach will review your weight trend, meals and workouts from this week and suggest " +
                    "changes to your targets and plan. Nothing changes until you approve it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("Start check-in")
            }
        }
    }
}

@Composable
private fun PendingCheckInCard(checkIn: WeeklyCheckIn, state: AppState, viewModel: AppViewModel) {
    val result = checkIn.result
    val profile = state.profile ?: return
    val hasNutritionChange = result.calorieChange != 0 || result.proteinChange != 0
    var applyNutrition by rememberSaveable(checkIn.id) { mutableStateOf(hasNutritionChange) }
    var selectedTraining by rememberSaveable(checkIn.id) { mutableStateOf(result.trainingChanges.indices.toList()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weekly check-in · ${shortDate(checkIn.dayKey)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(result.headline, style = MaterialTheme.typography.titleLarge)
            Text(result.summary, style = MaterialTheme.typography.bodyMedium)
            BulletList("What went well", result.wins, "✓")
            BulletList("To watch", result.concerns, "!")

            HorizontalDivider()
            Text("Suggested changes", style = MaterialTheme.typography.titleMedium)

            if (hasNutritionChange) {
                val current = state.targets ?: return@Column
                val proposed = MacroCalculator.targets(
                    profile,
                    CheckInCalculator.boundedAdjustment(state.adjustment, result.calorieChange, result.proteinChange, state.canCutCalories),
                )
                ChangeRow(
                    checked = applyNutrition,
                    onCheckedChange = { applyNutrition = it },
                    title = buildString {
                        if (proposed.calories != current.calories) append("Calories ${current.calories} → ${proposed.calories} kcal")
                        if (proposed.proteinG != current.proteinG) {
                            if (isNotEmpty()) append(" · ")
                            append("Protein ${current.proteinG} → ${proposed.proteinG} g")
                        }
                        if (isEmpty()) append("Targets are already at their limit")
                    },
                    detail = result.nutritionReason,
                )
                val blocked = state.calorieCutBlockedReason
                if (result.calorieChange < 0 && blocked != null) {
                    Text(
                        "FitHall won't lower your calories while your $blocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (result.nutritionReason.isNotBlank()) {
                Text("Nutrition: keep your current targets. ${result.nutritionReason}", style = MaterialTheme.typography.bodyMedium)
            }

            result.trainingChanges.forEachIndexed { index, change ->
                ChangeRow(
                    checked = index in selectedTraining,
                    onCheckedChange = { on -> selectedTraining = if (on) selectedTraining + index else selectedTraining - index },
                    title = changeTitle(change),
                    detail = change.reason,
                )
            }
            if (!hasNutritionChange && result.trainingChanges.isEmpty()) {
                Text("No changes needed: stick with the plan.", style = MaterialTheme.typography.bodyMedium)
            }

            BulletList("Focus for next week", result.focusForNextWeek, "→")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = { viewModel.dismissCheckIn(checkIn.id) }, modifier = Modifier.weight(1f)) {
                    Text("Not now")
                }
                Button(
                    onClick = { viewModel.applyCheckIn(checkIn.id, applyNutrition && hasNutritionChange, selectedTraining.toSet()) },
                    modifier = Modifier.weight(1f),
                ) {
                    val count = selectedTraining.size + if (applyNutrition && hasNutritionChange) 1 else 0
                    Text(if (count == 0) "Done" else "Apply $count")
                }
            }
        }
    }
}

@Composable
private fun LatestCheckInCard(state: AppState, onRun: () -> Unit) {
    val latest = state.checkIns.lastOrNull { it.status != CheckInStatus.PENDING }
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(
            Modifier.clickable(enabled = latest != null) { expanded = !expanded }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Weekly check-in", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onRun) { Text("Run now") }
            }
            if (latest == null) {
                Text(
                    "Log a workout, a couple of weigh-ins or a few days of meals, and your first check-in will unlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val since = CheckInCalculator.daysSinceLast(state.checkIns, state.today) ?: 0
            val next = (CheckInCalculator.INTERVAL_DAYS - since).coerceAtLeast(0)
            Text(
                "Last: ${shortDate(latest.dayKey)} · next in $next day${if (next == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(latest.result.headline, style = MaterialTheme.typography.bodyLarge)
            if (latest.status == CheckInStatus.APPLIED) {
                val parts = listOfNotNull(
                    latest.appliedCalorieChange.takeIf { it != 0 }?.let { "${signed(it)} kcal" },
                    latest.appliedProteinChange.takeIf { it != 0 }?.let { "${signed(it)} g protein" },
                    latest.appliedTrainingChanges.takeIf { it > 0 }?.let { "$it exercise change${if (it == 1) "" else "s"}" },
                )
                if (parts.isNotEmpty()) Text("Applied: " + parts.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
            if (expanded) {
                Text(latest.result.summary, style = MaterialTheme.typography.bodyMedium)
                BulletList("Focus for this week", latest.result.focusForNextWeek, "→")
            }
        }
    }
}

@Composable
private fun ChangeRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.padding(top = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BulletList(title: String, items: List<String>, bullet: String) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        items.forEach { Text("$bullet  $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun changeTitle(change: TrainingChange): String {
    val swap = !change.newExercise.equals(change.currentExercise, ignoreCase = true) && change.newExercise.isNotBlank()
    val prescription = "${change.sets} × ${change.reps}, rest ${change.restSeconds}s"
    return if (swap) "${change.dayName}: ${change.currentExercise} → ${change.newExercise} ($prescription)"
    else "${change.dayName}: ${change.currentExercise} → $prescription"
}

private fun signed(value: Int) = if (value > 0) "+$value" else "$value"
