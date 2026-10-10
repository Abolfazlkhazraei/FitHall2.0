package com.example.fithall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fithall.AppViewModel
import com.example.fithall.domain.ProgressCalculator
import com.example.fithall.model.ExerciseLog
import com.example.fithall.model.SetLog
import com.example.fithall.model.WorkoutLog
import com.example.fithall.platform.nowMillis
import kotlinx.coroutines.delay

@Composable
fun ActiveWorkoutScreen(session: WorkoutLog, history: List<WorkoutLog>, viewModel: AppViewModel) {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = nowMillis()
            delay(1000)
        }
    }
    var restEndsAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var newExercise by rememberSaveable { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }

    val totalSets = session.exercises.sumOf { it.sets.size }
    val doneSets = session.exercises.sumOf { ex -> ex.sets.count { it.completed } }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Header stays visible while scrolling through exercises.
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${clock(now - session.startedAtMillis)} · $doneSets/$totalSets sets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { confirmDiscard = true }) { Icon(Icons.Filled.Close, contentDescription = "Discard workout") }
            }
            LinearProgressIndicator(
                progress = { if (totalSets == 0) 0f else doneSets.toFloat() / totalSets },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
            )
        }
        restEndsAt?.let { endsAt ->
            val left = endsAt - now
            if (left > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Rest ${clock(left)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { restEndsAt = endsAt + 30_000 }) { Text("+30s") }
                        TextButton(onClick = { restEndsAt = null }) { Text("Skip") }
                    }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(session.exercises, key = { i, ex -> "$i-${ex.name}" }) { exIndex, exercise ->
                ExerciseCard(
                    exercise = exercise,
                    lastTime = remember(history, exercise.name) { ProgressCalculator.lastSets(history, exercise.name) },
                    onSetChange = { setIndex, transform -> viewModel.updateSet(exIndex, setIndex, transform) },
                    onSetCompleted = { if (exercise.restSeconds > 0) restEndsAt = nowMillis() + exercise.restSeconds * 1000L },
                    onAddSet = { viewModel.addSet(exIndex) },
                    onRemoveSet = { setIndex -> viewModel.removeSet(exIndex, setIndex) },
                    onRemoveExercise = { viewModel.removeExercise(exIndex) },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newExercise, { newExercise = it },
                        label = { Text("Add an exercise") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.addExercise(newExercise); newExercise = "" },
                        enabled = newExercise.isNotBlank(),
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add exercise") }
                }
            }
            item {
                OutlinedTextField(
                    notes, { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("How did it feel?") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = { viewModel.finishWorkout(notes) },
                    enabled = doneSets > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Finish workout") }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = { Text("Sets you've logged in this session won't be saved.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.discardWorkout() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseLog,
    lastTime: List<SetLog>?,
    onSetChange: (Int, (SetLog) -> SetLog) -> Unit,
    onSetCompleted: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onRemoveExercise: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    val target = listOfNotNull(
                        exercise.target.ifBlank { null },
                        exercise.restSeconds.takeIf { it > 0 }?.let { "rest ${it}s" },
                    ).joinToString(" · ")
                    if (target.isNotEmpty()) Text("Target $target", style = MaterialTheme.typography.bodySmall)
                    if (lastTime != null) {
                        Text(
                            "Last time: " + lastTime.joinToString(", ") { s ->
                                if (s.weightKg != null && s.weightKg > 0) "${formatNumber(s.weightKg)}×${s.reps}" else "${s.reps}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onRemoveExercise) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${exercise.name}") }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Set", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
                Text("kg", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text("Reps", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("Done", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
                Spacer(Modifier.width(40.dp))
            }
            exercise.sets.forEachIndexed { setIndex, set ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${setIndex + 1}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(36.dp))
                    NumberCell(
                        value = set.weightKg,
                        decimal = true,
                        onValue = { kg -> onSetChange(setIndex) { it.copy(weightKg = kg) } },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    NumberCell(
                        value = set.reps.takeIf { it > 0 }?.toDouble(),
                        decimal = false,
                        onValue = { reps -> onSetChange(setIndex) { it.copy(reps = reps?.toInt() ?: 0) } },
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(
                        checked = set.completed,
                        enabled = set.reps > 0,
                        onCheckedChange = { checked ->
                            onSetChange(setIndex) { it.copy(completed = checked) }
                            if (checked) onSetCompleted()
                        },
                        modifier = Modifier.width(48.dp),
                    )
                    IconButton(onClick = { onRemoveSet(setIndex) }, modifier = Modifier.width(40.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove set ${setIndex + 1}")
                    }
                }
            }
            OutlinedButton(onClick = onAddSet, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Add set")
            }
        }
    }
}

/**
 * Text field over a numeric model value. It keeps its own text so partial input like "62."
 * survives, and only resyncs when the model changes to something the text doesn't represent.
 */
@Composable
private fun NumberCell(value: Double?, decimal: Boolean, onValue: (Double?) -> Unit, modifier: Modifier) {
    fun format(v: Double?) = v?.let { if (decimal) formatNumber(it) else it.toInt().toString() }.orEmpty()
    fun parse(text: String) = text.replace(',', '.').toDoubleOrNull()

    var text by remember { mutableStateOf(format(value)) }
    LaunchedEffect(value) { if (parse(text) != value) text = format(value) }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            val cleaned = new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)
            text = cleaned
            onValue(parse(cleaned))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = modifier,
    )
}

private fun clock(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val mmss = "${m.toString().padStart(if (h > 0) 2 else 1, '0')}:${s.toString().padStart(2, '0')}"
    return if (h > 0) "$h:$mmss" else mmss
}
