package com.fithall.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fithall.app.AppViewModel
import com.fithall.app.domain.ProgressCalculator
import com.fithall.app.model.ExerciseLog
import com.fithall.app.model.SetLog
import com.fithall.app.model.UnitSystem
import com.fithall.app.model.Units
import com.fithall.app.model.WorkoutLog
import com.fithall.app.platform.nowMillis
import kotlinx.coroutines.delay

/**
 * The live workout, one exercise at a time: big steppers for the set in hand, one button to
 * complete it, a rest timer, and what comes next.
 */
@Composable
fun ActiveWorkoutScreen(
    session: WorkoutLog,
    history: List<WorkoutLog>,
    units: UnitSystem,
    viewModel: AppViewModel,
    onMinimize: () -> Unit = {},
) {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = nowMillis()
            delay(1000)
        }
    }
    var restEndsAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var restTotal by rememberSaveable { mutableLongStateOf(0L) }
    var finishing by remember { mutableStateOf(false) }
    var addingExercise by remember { mutableStateOf(false) }
    val exercises = session.exercises
    var current by rememberSaveable {
        mutableIntStateOf(exercises.indexOfFirst { ex -> ex.sets.any { !it.completed } }.coerceAtLeast(0))
    }
    if (current > exercises.lastIndex) current = exercises.lastIndex.coerceAtLeast(0)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Filled.KeyboardArrowDown, "Minimise workout", onMinimize)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    session.title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.muted,
                    letterSpacing = 0.06.em,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Text(clock(now - session.startedAtMillis), style = MaterialTheme.typography.titleLarge)
            }
            Surface(
                onClick = { finishing = true },
                shape = RoundedCornerShape(22.dp),
                color = Palette.surface,
                contentColor = Palette.lime,
                border = androidx.compose.foundation.BorderStroke(1.dp, Palette.line),
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Finish", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (exercises.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text("Add your first exercise", style = MaterialTheme.typography.headlineSmall)
                Text("Log any exercise and its sets as you go.", color = Palette.muted, textAlign = TextAlign.Center)
                LimeButton("Add an exercise", Icons.Filled.Add) { addingExercise = true }
            }
        } else {
            ExerciseSegments(exercises, current, onSelect = { current = it })
            val exercise = exercises[current]
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ExerciseFocus(
                    exercise = exercise,
                    index = current,
                    count = exercises.size,
                    units = units,
                    lastTime = remember(history, exercise.name) { ProgressCalculator.lastSets(history, exercise.name) },
                    onPrevious = { current = (current - 1).coerceAtLeast(0) },
                    onNext = { current = (current + 1).coerceAtMost(exercises.lastIndex) },
                    onSetChange = { setIndex, transform -> viewModel.updateSet(current, setIndex, transform) },
                    onSetCompleted = {
                        if (exercise.restSeconds > 0) {
                            restTotal = exercise.restSeconds * 1000L
                            restEndsAt = nowMillis() + restTotal
                        }
                    },
                    onAddSet = { viewModel.addSet(current) },
                    onRemoveSet = { viewModel.removeSet(current, it) },
                    onRemoveExercise = { viewModel.removeExercise(current) },
                    onAddExercise = { addingExercise = true },
                )

                restEndsAt?.let { endsAt ->
                    val left = endsAt - now
                    if (left > 0) {
                        RestCard(left, restTotal, onAdd = { restEndsAt = endsAt + 30_000; restTotal += 30_000 }, onSkip = { restEndsAt = null })
                    }
                }

                exercises.getOrNull(current + 1)?.let { next ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(role = Role.Button) { current += 1 }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Up next", style = MaterialTheme.typography.labelMedium, color = Palette.muted)
                            Text(
                                listOf(next.name, next.target).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.muted)
                    }
                }
            }
        }
    }

    if (addingExercise) {
        TextEntryDialog(
            title = "Add an exercise",
            label = "Exercise name",
            keyboardType = KeyboardType.Text,
            confirm = "Add",
            onConfirm = { name ->
                viewModel.addExercise(name)
                current = exercises.size // the new exercise, once the session updates
                addingExercise = false
            },
            onDismiss = { addingExercise = false },
        )
    }
    if (finishing) {
        FinishDialog(
            doneSets = exercises.sumOf { ex -> ex.sets.count { it.completed } },
            onFinish = { notes -> finishing = false; viewModel.finishWorkout(notes) },
            onDiscard = { finishing = false; viewModel.discardWorkout() },
            onDismiss = { finishing = false },
        )
    }
}

/** One bar per exercise: lime when done, outlined when current. Tapping jumps to it. */
@Composable
private fun ExerciseSegments(exercises: List<ExerciseLog>, current: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        exercises.forEachIndexed { i, ex ->
            val done = ex.sets.isNotEmpty() && ex.sets.all { it.completed }
            Box(
                Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { contentDescription = "Exercise ${i + 1}: ${ex.name}${if (done) ", done" else ""}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(if (i == current) 6.dp else 4.dp).clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                done -> Palette.lime
                                i == current -> Palette.text
                                else -> Palette.line
                            }
                        ),
                )
            }
        }
    }
}

@Composable
private fun ExerciseFocus(
    exercise: ExerciseLog,
    index: Int,
    count: Int,
    units: UnitSystem,
    lastTime: List<SetLog>?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetChange: (Int, (SetLog) -> SetLog) -> Unit,
    onSetCompleted: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onRemoveExercise: () -> Unit,
    onAddExercise: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(
                    "Exercise ${index + 1} of $count",
                    exercise.target.ifBlank { null }?.let { "target $it" },
                    exercise.restSeconds.takeIf { it > 0 }?.let { "rest ${it}s" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.muted,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPrevious, enabled = index > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous exercise")
            }
            IconButton(onClick = onNext, enabled = index < count - 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next exercise")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Add a set") }, onClick = { menu = false; onAddSet() })
                    DropdownMenuItem(text = { Text("Add an exercise") }, onClick = { menu = false; onAddExercise() })
                    DropdownMenuItem(text = { Text("Remove this exercise") }, onClick = { menu = false; onRemoveExercise() })
                }
            }
        }
        Text(exercise.name, style = MaterialTheme.typography.headlineLarge)
        if (lastTime != null) {
            Text(
                "Last time: " + lastTime.joinToString(", ") { formatSet(it.weightKg, it.reps, units) },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.blue,
            )
        }
    }

    val active = exercise.sets.indexOfFirst { !it.completed }
    exercise.sets.forEachIndexed { setIndex, set ->
        if (setIndex == active) return@forEachIndexed
        SetRow(setIndex, set, units, onToggle = { onSetChange(setIndex) { it.copy(completed = !it.completed) } }, onRemove = { onRemoveSet(setIndex) })
    }

    if (active >= 0) {
        CurrentSetCard(
            setNumber = active + 1,
            initialWeightKg = exercise.sets[active].weightKg
                ?: exercise.sets.getOrNull(active - 1)?.weightKg
                ?: lastTime?.getOrNull(active)?.weightKg,
            initialReps = exercise.sets[active].reps.takeIf { it > 0 }
                ?: exercise.sets.getOrNull(active - 1)?.reps?.takeIf { it > 0 }
                ?: lastTime?.getOrNull(active)?.reps?.takeIf { it > 0 }
                ?: targetReps(exercise.target),
            units = units,
            onComplete = { weightKg, reps ->
                onSetChange(active) { it.copy(weightKg = weightKg, reps = reps, completed = true) }
                onSetCompleted()
            },
            key = "${exercise.name}-$active",
        )
    } else {
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Palette.surface)
                .border(1.dp, Palette.line, MaterialTheme.shapes.extraLarge).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = Palette.lime)
                Spacer(Modifier.width(8.dp))
                Text(if (exercise.sets.isEmpty()) "No sets yet" else "All sets done", style = MaterialTheme.typography.titleMedium)
            }
            if (index < count - 1) LimeButton("Next exercise", Icons.AutoMirrored.Filled.KeyboardArrowRight, onClick = onNext)
            Surface(
                onClick = onAddSet,
                shape = RoundedCornerShape(18.dp),
                color = Palette.raised,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add a set", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** A logged or planned set. Tapping a done set reopens it; × removes it. */
@Composable
private fun SetRow(index: Int, set: SetLog, units: UnitSystem, onToggle: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small).background(Palette.surface)
            .clickable(enabled = set.completed, role = Role.Button, onClickLabel = "Edit set", onClick = onToggle)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Set ${index + 1}", color = Palette.muted, modifier = Modifier.width(56.dp))
        Text(
            if (set.completed || set.reps > 0) setLabel(set, units) else "Planned",
            fontWeight = if (set.completed) FontWeight.SemiBold else FontWeight.Normal,
            color = if (set.completed) Palette.text else Palette.muted,
            modifier = Modifier.weight(1f),
        )
        if (set.completed) Icon(Icons.Filled.Check, contentDescription = "Done", tint = Palette.lime, modifier = Modifier.size(22.dp))
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove set ${index + 1}", tint = Palette.muted) }
    }
}

/** "60 kg × 8", or "12 reps" for bodyweight. */
private fun setLabel(set: SetLog, units: UnitSystem): String =
    set.weightKg?.takeIf { it > 0 }
        ?.let { "${formatNumber(Units.kgToDisplay(it, units))} ${Units.weightUnit(units)} × ${set.reps}" }
        ?: "${set.reps} reps"

@Composable
private fun CurrentSetCard(
    setNumber: Int,
    initialWeightKg: Double?,
    initialReps: Int,
    units: UnitSystem,
    onComplete: (weightKg: Double?, reps: Int) -> Unit,
    key: String,
) {
    // Shown in the user's units; null means bodyweight.
    var weight by rememberSaveable(key) { mutableStateOf(initialWeightKg?.takeIf { it > 0 }?.let { Units.kgToDisplay(it, units) }) }
    var reps by rememberSaveable(key) { mutableIntStateOf(initialReps.coerceAtLeast(1)) }
    var editing by remember { mutableStateOf<String?>(null) }
    val step = if (units == UnitSystem.METRIC) 2.5 else 5.0

    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Palette.surface)
            .border(1.dp, Palette.lime.copy(alpha = 0.35f), MaterialTheme.shapes.extraLarge).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("SET $setNumber · NOW", style = MaterialTheme.typography.labelMedium, color = Palette.lime, letterSpacing = 0.06.em)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper(
                label = "Weight (${Units.weightUnit(units)})",
                value = weight?.let(::formatNumber) ?: "BW",
                valueDescription = weight?.let { "${formatNumber(it)} ${Units.weightUnit(units)}" } ?: "Bodyweight",
                onMinus = { weight = weight?.let { (it - step).takeIf { w -> w > 0 } } },
                onPlus = { weight = (weight ?: 0.0) + step },
                onEdit = { editing = "weight" },
                minusEnabled = weight != null,
                modifier = Modifier.weight(1f),
            )
            Stepper(
                label = "Reps",
                value = "$reps",
                valueDescription = "$reps reps",
                onMinus = { reps = (reps - 1).coerceAtLeast(1) },
                onPlus = { reps += 1 },
                onEdit = { editing = "reps" },
                minusEnabled = reps > 1,
                modifier = Modifier.weight(1f),
            )
        }
        LimeButton("Complete set", Icons.Filled.Check, height = 60) {
            onComplete(weight?.let { Units.displayToKg(it, units) }, reps)
        }
    }

    when (editing) {
        "weight" -> TextEntryDialog(
            title = "Weight (${Units.weightUnit(units)})",
            label = "Leave empty for bodyweight",
            initial = weight?.let(::formatNumber).orEmpty(),
            keyboardType = KeyboardType.Decimal,
            confirm = "Set",
            allowEmpty = true,
            onConfirm = { text ->
                weight = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 1000 }
                editing = null
            },
            onDismiss = { editing = null },
        )
        "reps" -> TextEntryDialog(
            title = "Reps",
            label = "Reps",
            initial = "$reps",
            keyboardType = KeyboardType.Number,
            confirm = "Set",
            onConfirm = { text ->
                text.toIntOrNull()?.takeIf { it in 1..999 }?.let { reps = it }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    valueDescription: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onEdit: () -> Unit,
    minusEnabled: Boolean,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.muted)
        Text(
            value,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Palette.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Type a value", onClick = onEdit)
                .semantics { contentDescription = valueDescription }
                .padding(vertical = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton(Icons.Filled.Remove, "Less", minusEnabled, onMinus)
            StepButton(Icons.Filled.Add, "More", true, onPlus)
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = Palette.raised,
        contentColor = if (enabled) Palette.text else Palette.muted.copy(alpha = 0.5f),
        modifier = Modifier.size(width = 60.dp, height = 48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description) }
    }
}

@Composable
private fun RestCard(leftMillis: Long, totalMillis: Long, onAdd: () -> Unit, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Palette.blueDeep)
            .border(1.dp, Palette.blueLine, MaterialTheme.shapes.medium).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(
            progress = if (totalMillis <= 0) 0f else leftMillis.toFloat() / totalMillis,
            size = 44.dp,
            stroke = 5.dp,
            color = Palette.blue,
            trackColor = Palette.blueLine,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Rest", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(clock(leftMillis), style = MaterialTheme.typography.titleLarge)
        }
        TextButton(onClick = onAdd) { Text("+30 s", color = Palette.text) }
        TextButton(onClick = onSkip) { Text("Skip", color = Palette.text) }
    }
}

@Composable
private fun LimeButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, height: Int = 52, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Palette.lime,
        contentColor = Palette.onLime,
        modifier = Modifier.fillMaxWidth().height(height.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(Palette.surface).border(1.dp, Palette.line, CircleShape),
    ) { Icon(icon, contentDescription = description) }
}

@Composable
private fun TextEntryDialog(
    title: String,
    label: String,
    keyboardType: KeyboardType,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    allowEmpty: Boolean = false,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it.take(60) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = allowEmpty || text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FinishDialog(doneSets: Int, onFinish: (String) -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    var notes by rememberSaveable { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = { Text("Sets you've logged in this session won't be saved.") },
            confirmButton = { TextButton(onClick = onDiscard) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep it") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (doneSets > 0) "Finish workout?" else "No sets logged yet") },
        text = {
            Column(Modifier.widthIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (doneSets > 0) {
                    Text("$doneSets ${if (doneSets == 1) "set" else "sets"} done.")
                    OutlinedTextField(
                        notes, { notes = it },
                        label = { Text("Notes (optional)") },
                        placeholder = { Text("How did it feel?") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Complete at least one set to save this workout, or discard it.")
                }
                TextButton(onClick = { confirmDiscard = true }) { Text("Discard workout", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onFinish(notes) }, enabled = doneSets > 0) { Text("Finish") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep going") } },
    )
}

/** The lower bound of a target like "3 × 8-10" (8), or 10 when there isn't one. */
internal fun targetReps(target: String): Int =
    Regex("""[×x]\s*(\d+)""").find(target)?.groupValues?.get(1)?.toIntOrNull() ?: 10

private fun clock(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val mmss = "${m.toString().padStart(if (h > 0) 2 else 1, '0')}:${s.toString().padStart(2, '0')}"
    return if (h > 0) "$h:$mmss" else mmss
}
