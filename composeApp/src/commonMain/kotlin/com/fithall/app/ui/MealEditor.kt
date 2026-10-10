package com.fithall.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fithall.app.domain.Portions
import com.fithall.app.model.FoodItem
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.MealEntry

/** A food being edited: its estimate as it came in, and how much of it the user says they ate. */
data class EditableItem(val original: FoodItem, val factor: Double = 1.0) {
    val current: FoodItem get() = Portions.scale(original, factor)
}

fun List<EditableItem>.totals(): MacroTargets = fold(MacroTargets.ZERO) { acc, item ->
    val f = item.current
    acc + MacroTargets(f.calories, f.proteinG, f.carbsG, f.fatG)
}

/** Rows of foods; tap one to change its portion, or remove it. */
@Composable
fun MealItemsEditor(items: List<EditableItem>, onChange: (List<EditableItem>) -> Unit) {
    var editing by remember { mutableStateOf<Int?>(null) }
    items.forEachIndexed { index, item ->
        val food = item.current
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { editing = index }) {
            Column(Modifier.weight(1f)) {
                Text("${food.name} · ${food.portion}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${food.calories} kcal · P ${food.proteinG} · C ${food.carbsG} · F ${food.fatG}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { editing = index }) { Icon(Icons.Filled.Edit, contentDescription = "Change portion of ${food.name}") }
            IconButton(onClick = { onChange(items.filterIndexed { i, _ -> i != index }) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove ${food.name}")
            }
        }
    }
    editing?.let { index ->
        items.getOrNull(index)?.let { item ->
            PortionDialog(
                item = item,
                onSave = { factor -> onChange(items.mapIndexed { i, it -> if (i == index) it.copy(factor = factor) else it }); editing = null },
                onDismiss = { editing = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortionDialog(item: EditableItem, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    val amount = remember(item.original) { Portions.parse(item.original.portion) }
    // With a parseable amount ("150 g") the user edits the amount; otherwise a multiplier.
    var text by remember(item) {
        mutableStateOf(formatNumber(if (amount != null) amount.value * item.factor else item.factor))
    }
    val entered = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 100_000 }
    val factor = entered?.let { if (amount != null) it / amount.value else it }?.takeIf { it <= 20 }
    val preview = factor?.let { Portions.scale(item.original, it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.original.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estimated: ${item.original.portion}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' || it == ',' }.take(7) },
                    label = { Text(if (amount != null) "How much did you eat?" else "How many portions?") },
                    suffix = { Text(if (amount != null) amount.unit else "×") },
                    singleLine = true,
                    isError = factor == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5 to "½×", 1.0 to "1×", 1.5 to "1.5×", 2.0 to "2×").forEach { (f, label) ->
                        AssistChip(
                            onClick = { text = formatNumber(if (amount != null) amount.value * f else f) },
                            label = { Text(label) },
                        )
                    }
                }
                if (preview != null) {
                    Text(
                        "${preview.portion}: ${preview.calories} kcal · P ${preview.proteinG} g · C ${preview.carbsG} g · F ${preview.fatG} g",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { factor?.let(onSave) }, enabled = factor != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Edit a meal that's already logged: change portions or remove foods. */
@Composable
fun EditMealDialog(entry: MealEntry, onSave: (MealEntry) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var items by remember(entry.id) { mutableStateOf(entry.analysis.items.map { EditableItem(it) }) }
    val totals = items.totals()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.analysis.mealName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${totals.calories} kcal · P ${totals.proteinG} g · C ${totals.carbsG} g · F ${totals.fatG} g",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                MealItemsEditor(items) { items = it }
                if (items.isEmpty()) Text("All foods removed. Save to delete this meal.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (items.isEmpty()) onDelete()
                else onSave(entry.copy(analysis = entry.analysis.copy(items = items.map { it.current })))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The analysis with the user's edits applied. */
fun MealAnalysis.withItems(items: List<EditableItem>): MealAnalysis = copy(items = items.map { it.current })
