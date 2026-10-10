package com.example.fithall.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.fithall.AppState
import com.example.fithall.AppViewModel
import com.example.fithall.ScanState
import com.example.fithall.model.MacroTargets
import com.example.fithall.model.MealAnalysis
import com.example.fithall.model.MealEntry
import com.example.fithall.platform.rememberMealPhotoLauncher
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
fun NutritionScreen(state: AppState, viewModel: AppViewModel) {
    val launcher = rememberMealPhotoLauncher { bytes -> viewModel.analyzeMeal(bytes) }

    when (val scan = state.scan) {
        ScanState.Idle -> Unit
        else -> {
            ScanResultScreen(scan, viewModel)
            return
        }
    }

    val targets = state.targets ?: return
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Today", style = MaterialTheme.typography.headlineSmall) }
        item { MacroSummaryCard(eaten = state.todaysTotals, targets = targets) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = launcher::takePhoto, enabled = launcher.canTakePhoto, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Snap meal")
                }
                OutlinedButton(onClick = launcher::pickFromGallery, enabled = launcher.canPickFromGallery, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text("From gallery")
                }
            }
        }

        item { Text("Meals logged today", style = MaterialTheme.typography.titleMedium) }
        if (state.todaysMeals.isEmpty()) {
            item {
                Text(
                    "Nothing logged yet. Take a photo of your plate and the AI will estimate its macros.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.todaysMeals.asReversed(), key = { it.id }) { meal ->
            MealRow(meal, onDelete = { viewModel.deleteMeal(meal.id) })
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI meal plan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::generateNutritionPlan, enabled = !state.generatingNutrition) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.nutritionPlan == null) "Generate" else "Regenerate")
                }
            }
        }
        item { NutritionPlanSection(state) }
    }
}

@Composable
private fun MacroSummaryCard(eaten: MacroTargets, targets: MacroTargets) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val left = targets.calories - eaten.calories
            Text(
                if (left >= 0) "$left kcal left" else "${-left} kcal over",
                style = MaterialTheme.typography.titleLarge,
            )
            MacroBar("Calories", eaten.calories, targets.calories, "kcal", MacroColors.calories)
            MacroBar("Protein", eaten.proteinG, targets.proteinG, "g", MacroColors.protein)
            MacroBar("Carbs", eaten.carbsG, targets.carbsG, "g", MacroColors.carbs)
            MacroBar("Fat", eaten.fatG, targets.fatG, "g", MacroColors.fat)
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Int, target: Int, unit: String, color: Color) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("$value / $target $unit", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { if (target == 0) 0f else (value.toFloat() / target).coerceIn(0f, 1f) },
            color = color,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun MealRow(meal: MealEntry, onDelete: () -> Unit) {
    val t = meal.analysis.totals
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meal.analysis.mealName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${t.calories} kcal · P ${t.proteinG} g · C ${t.carbsG} g · F ${t.fatG} g",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete meal") }
        }
    }
}

@Composable
private fun NutritionPlanSection(state: AppState) {
    val plan = state.nutritionPlan
    when {
        state.generatingNutrition -> LoadingCard("Building a meal plan that hits your targets…")
        plan == null -> Text(
            "Generate a one-day meal plan built around your calorie and macro targets.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plan.summary, style = MaterialTheme.typography.bodyMedium)
                plan.meals.forEach { meal ->
                    HorizontalDivider()
                    Text(meal.name, style = MaterialTheme.typography.titleSmall)
                    Text(meal.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${meal.calories} kcal · P ${meal.proteinG} g · C ${meal.carbsG} g · F ${meal.fatG} g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (plan.tips.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Tips", style = MaterialTheme.typography.titleSmall)
                    plan.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
fun LoadingCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---- Scan result ----

@Composable
private fun ScanResultScreen(scan: ScanState, viewModel: AppViewModel) {
    val photo = when (scan) {
        is ScanState.Analyzing -> scan.photo
        is ScanState.Result -> scan.photo
        is ScanState.Failed -> scan.photo
        ScanState.Idle -> return
    }
    val bitmap: ImageBitmap? = remember(photo) { runCatching { photo.decodeToImageBitmap() }.getOrNull() }
    var note by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Meal scan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::cancelScan) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
            }
        }
        if (bitmap != null) {
            item {
                Image(
                    bitmap, contentDescription = "Meal photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
        }
        when (scan) {
            is ScanState.Analyzing -> item { LoadingCard("Identifying foods and estimating portions…") }
            is ScanState.Failed -> item {
                Text(scan.message, color = MaterialTheme.colorScheme.error)
            }
            is ScanState.Result -> item { AnalysisCard(scan.analysis, onLog = viewModel::logScannedMeal) }
            ScanState.Idle -> Unit
        }
        if (scan !is ScanState.Analyzing) {
            item {
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("Add a correction (optional)") },
                    placeholder = { Text("e.g. 200 g chicken, cooked without oil") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FilledTonalButton(onClick = { viewModel.retryScan(note) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Re-analyze")
                }
            }
        }
    }
}

@Composable
private fun AnalysisCard(analysis: MealAnalysis, onLog: (MealAnalysis) -> Unit) {
    // Let the user drop foods the AI misidentified before logging.
    var removed by remember(analysis) { mutableStateOf(setOf<Int>()) }
    val edited = analysis.copy(items = analysis.items.filterIndexed { i, _ -> i !in removed })
    val t = edited.totals

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(analysis.mealName, style = MaterialTheme.typography.titleLarge)
            Text(
                "${t.calories} kcal · P ${t.proteinG} g · C ${t.carbsG} g · F ${t.fatG} g",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Confidence: ${analysis.confidence}", style = MaterialTheme.typography.labelMedium)
            analysis.items.forEachIndexed { index, item ->
                if (index in removed) return@forEachIndexed
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${item.name} · ${item.portion}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${item.calories} kcal · P ${item.proteinG} · C ${item.carbsG} · F ${item.fatG}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { removed = removed + index }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ${item.name}")
                    }
                }
            }
            if (analysis.notes.isNotBlank()) {
                HorizontalDivider()
                Text(analysis.notes, style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Button(
                    onClick = { onLog(edited) },
                    enabled = edited.items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Log this meal") }
            }
        }
    }
}
