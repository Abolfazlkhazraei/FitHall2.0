package com.fithall.app.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.fithall.app.AppState
import com.fithall.app.AppViewModel
import com.fithall.app.domain.ProgressCalculator

/**
 * The home screen: today's numbers at a glance, the next workout, the coach and today's meals,
 * each one tap from the full feature.
 */
@Composable
fun TodayScreen(
    state: AppState,
    viewModel: AppViewModel,
    navigate: (Destination) -> Unit,
    onQuickAdd: () -> Unit,
) {
    val profile = state.profile ?: return
    val targets = state.targets ?: return
    val eaten = state.todaysTotals
    val stats = remember(state.workoutLogs, state.today) { ProgressCalculator.weeklyStats(state.workoutLogs, state.today) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeroHeader(
                title = profile.name.takeIf { it.isNotBlank() }?.let { "Hi, ${it.substringBefore(' ')}" } ?: "Today",
                subtitle = longDate(state.today),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (stats.weekStreak > 0) {
                            Row(
                                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Palette.surface)
                                    .border(1.dp, Palette.line, RoundedCornerShape(22.dp)).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.LocalFireDepartment, null, tint = Palette.lime, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (stats.weekStreak == 1) "1 week" else "${stats.weekStreak} weeks",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        IconButton(
                            onClick = { navigate(Destination.PROFILE) },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Palette.surface).border(1.dp, Palette.line, CircleShape),
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = "Profile and settings")
                        }
                    }
                },
            ) {
                TodayRings(
                    caloriesEaten = eaten.calories,
                    caloriesTarget = targets.calories,
                    proteinEaten = eaten.proteinG,
                    proteinTarget = targets.proteinG,
                    workouts = stats.workoutsLast7Days,
                    workoutsTarget = profile.trainingDaysPerWeek,
                    onClick = { navigate(Destination.EAT) },
                )
            }
        }

        if (state.checkInDue || state.pendingCheckIn != null) {
            item {
                Padded {
                    AccentRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = if (state.pendingCheckIn != null) "Your check-in results are ready" else "Your weekly check-in is ready",
                        body = "Review your week and update your targets",
                        container = Palette.blue,
                        onClick = { navigate(Destination.PROGRESS) },
                    )
                }
            }
        }

        item { Padded { UpNextCard(state, stats.workoutsLast7Days, viewModel, navigate) } }

        item {
            Padded {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.medium).background(Palette.surface)
                        .border(1.dp, Palette.line, MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) { navigate(Destination.COACH) }
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Palette.blue, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Ask your coach anything…", color = Palette.muted, modifier = Modifier.weight(1f))
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.raised), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        item {
            Padded {
                SectionHeader("Meals today") {
                    Text(
                        "See all",
                        color = Palette.lime,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { navigate(Destination.EAT) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        }
        item {
            Padded {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.todaysMeals.takeLast(2).asReversed().forEach { meal ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 104.dp).clip(MaterialTheme.shapes.medium).background(Palette.surface)
                                .border(1.dp, Palette.line, MaterialTheme.shapes.medium)
                                .clickable(role = Role.Button) { navigate(Destination.EAT) }.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(meal.analysis.mealName, style = MaterialTheme.typography.labelMedium, color = Palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            Text("${meal.analysis.totals.calories}", style = MaterialTheme.typography.titleLarge)
                            Text("kcal", style = MaterialTheme.typography.labelSmall, color = Palette.muted)
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = 104.dp).clip(MaterialTheme.shapes.medium)
                            .border(1.5.dp, Color(0xFF3A3F46), MaterialTheme.shapes.medium)
                            .clickable(role = Role.Button, onClick = onQuickAdd).padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Palette.lime, modifier = Modifier.size(26.dp))
                        Text(if (state.todaysMeals.isEmpty()) "Log your first meal" else "Log a meal", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Three nested rings: calories (lime), protein (blue) and workouts this week (white). */
@Composable
private fun TodayRings(
    caloriesEaten: Int,
    caloriesTarget: Int,
    proteinEaten: Int,
    proteinTarget: Int,
    workouts: Int,
    workoutsTarget: Int,
    onClick: () -> Unit,
) {
    fun ratio(a: Int, b: Int) = if (b <= 0) 0f else a.toFloat() / b
    val left = caloriesTarget - caloriesEaten
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "$caloriesEaten of $caloriesTarget kcal, $proteinEaten of $proteinTarget g protein, " +
                    "$workouts of $workoutsTarget workouts this week. Opens Eat."
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(ratio(caloriesEaten, caloriesTarget), 164.dp, 14.dp, if (left >= 0) Palette.lime else MacroColors.carbs, Palette.lime.copy(alpha = 0.12f))
            ProgressRing(ratio(proteinEaten, proteinTarget), 128.dp, 14.dp, Palette.blue, Palette.blue.copy(alpha = 0.14f))
            ProgressRing(ratio(workouts, workoutsTarget), 92.dp, 14.dp, Palette.text, Palette.text.copy(alpha = 0.10f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${kotlin.math.abs(left)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (left >= 0) "kcal left" else "kcal over", style = MaterialTheme.typography.labelSmall, color = Palette.muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RingLegend(Palette.lime, "Calories", "$caloriesEaten", "/ $caloriesTarget")
            RingLegend(Palette.blue, "Protein", "$proteinEaten", "/ $proteinTarget g")
            RingLegend(Palette.text, "Workouts", "$workouts", "/ $workoutsTarget this week")
        }
    }
}

@Composable
private fun RingLegend(color: Color, label: String, value: String, of: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.muted)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(4.dp))
            Text(of, style = MaterialTheme.typography.labelMedium, color = Palette.muted, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** The lime card: resume the workout in progress, start the next plan day, or get a plan. */
@Composable
private fun UpNextCard(state: AppState, doneThisWeek: Int, viewModel: AppViewModel, navigate: (Destination) -> Unit) {
    val plan = state.workoutPlan
    val active = state.activeWorkout
    val days = plan?.days.orEmpty()
    val nextIndex = if (days.isEmpty()) 0 else doneThisWeek % days.size
    val next = days.getOrNull(nextIndex)

    val (label, title, detail, button) = when {
        active != null -> listOf("In progress", active.title, "${active.exercises.size} exercises", "Resume workout")
        next != null -> listOf(
            "Up next · Day ${nextIndex + 1} of ${days.size}",
            next.focus,
            "${next.exercises.size} exercises · about ${next.durationMinutes} min",
            "Start workout",
        )
        state.generatingWorkout -> listOf("Training", "Building your plan…", "Your coach is designing it now", "Open Train")
        else -> listOf("Training", "Get your plan", "Built around your level, equipment and goal", "Create my plan")
    }
    val onClick = {
        when {
            active != null -> Unit
            next != null -> viewModel.startWorkout(next)
            !state.generatingWorkout -> viewModel.generateWorkoutPlan()
        }
        navigate(Destination.TRAIN)
    }

    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Palette.lime).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.onLime, letterSpacing = 0.06.em)
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.onLime, fontWeight = FontWeight.ExtraBold)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Palette.onLime)
            }
            Icon(Icons.Filled.FitnessCenter, null, tint = Palette.onLime, modifier = Modifier.size(36.dp))
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = Palette.onLime,
            contentColor = Palette.lime,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (next != null || active != null) Icons.Filled.PlayArrow else Icons.Filled.AutoAwesome, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(button, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A tappable accent row: icon, title, one line of detail and a chevron. */
@Composable
fun AccentRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = contentOn(container)
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clip(MaterialTheme.shapes.medium).background(container)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = content)
            Text(body, style = MaterialTheme.typography.bodySmall, color = content)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = content)
    }
}
