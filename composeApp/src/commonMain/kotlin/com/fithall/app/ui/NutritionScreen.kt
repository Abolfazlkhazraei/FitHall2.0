package com.fithall.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import com.fithall.app.AppState
import com.fithall.app.AppViewModel
import com.fithall.app.ScanState
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.MealEntry
import com.fithall.app.platform.rememberMealPhotoLauncher
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.input.KeyboardType
import com.fithall.app.MealSource
import com.fithall.app.data.PackagedFood
import com.fithall.app.data.PackagedFoods
import com.fithall.app.data.PortionUnit
import com.fithall.app.domain.MacroCalculator
import com.fithall.app.platform.VoiceInput
import com.fithall.app.platform.rememberBarcodeScanner
import com.fithall.app.platform.rememberVoiceInput
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
fun NutritionScreen(state: AppState, viewModel: AppViewModel) {
    val launcher = rememberMealPhotoLauncher { bytes -> viewModel.analyzeMeal(bytes) }
    var describing by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val voice = rememberVoiceInput(
        prompt = "Describe what you ate",
        onText = { spoken ->
            description = if (description.isBlank()) spoken else "${description.trimEnd()} $spoken"
            describing = true
        },
        onError = { inputError = it },
    )
    val barcodeScanner = rememberBarcodeScanner(
        onBarcode = viewModel::lookupBarcode,
        onError = { inputError = it },
    )

    if (describing) {
        DescribeMealDialog(
            text = description,
            onTextChange = { description = it },
            voice = voice,
            onAnalyze = {
                viewModel.analyzeDescription(description)
                describing = false
                description = ""
            },
            onDismiss = { describing = false },
        )
    }
    inputError?.let { message ->
        AlertDialog(
            onDismissRequest = { inputError = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { inputError = null }) { Text("OK") } },
        )
    }

    when (val scan = state.scan) {
        ScanState.Idle -> Unit
        else -> {
            ScanResultScreen(
                scan, viewModel,
                onScanAgain = barcodeScanner::scan,
                onDescribeInstead = {
                    viewModel.cancelScan()
                    describing = true
                },
            )
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
        state.profile?.let(MacroCalculator::safetyNotice)?.let { notice -> item { SafetyNotice(notice) } }
        item { MacroSummaryCard(eaten = state.todaysTotals, targets = targets) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = launcher::takePhoto, enabled = launcher.canTakePhoto, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Snap meal")
                    }
                    OutlinedButton(onClick = launcher::pickFromGallery, enabled = launcher.canPickFromGallery, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Gallery")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { describing = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Mic, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Say or type")
                    }
                    OutlinedButton(onClick = barcodeScanner::scan, enabled = barcodeScanner.isAvailable, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.QrCodeScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Barcode")
                    }
                }
            }
        }

        item { Text("Meals logged today", style = MaterialTheme.typography.titleMedium) }
        if (state.todaysMeals.isEmpty()) {
            item {
                Text(
                    "Nothing logged yet. Snap your plate, say what you ate, or scan a barcode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.todaysMeals.asReversed(), key = { it.id }) { meal ->
            MealRow(meal, onSave = viewModel::updateMeal, onDelete = { viewModel.deleteMeal(meal.id) })
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
        state.nutritionPlanFailure?.let { failure ->
            item { ErrorCard(failure, onRetry = viewModel::generateNutritionPlan, onDismiss = viewModel::dismissFailures) }
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
private fun MealRow(meal: MealEntry, onSave: (MealEntry) -> Unit, onDelete: () -> Unit) {
    val t = meal.analysis.totals
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        EditMealDialog(
            entry = meal,
            onSave = { onSave(it); editing = false },
            onDelete = { onDelete(); editing = false },
            onDismiss = { editing = false },
        )
    }
    Card(Modifier.fillMaxWidth().clickable { editing = true }) {
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

// ---- Scan result ----

@Composable
private fun ScanResultScreen(
    scan: ScanState,
    viewModel: AppViewModel,
    onScanAgain: () -> Unit,
    onDescribeInstead: () -> Unit,
) {
    val source = when (scan) {
        is ScanState.Analyzing -> scan.source
        is ScanState.Result -> scan.source
        is ScanState.Failed -> scan.source
        is ScanState.ProductFound -> MealSource.Barcode(scan.product.barcode)
        ScanState.Idle -> return
    }
    val photo = (source as? MealSource.Photo)?.jpeg
    val bitmap: ImageBitmap? = remember(photo) { photo?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    // For photos this is a correction note; for descriptions it's the editable description.
    var input by rememberSaveable(source) { mutableStateOf((source as? MealSource.Description)?.text ?: "") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = when (source) {
                    is MealSource.Photo -> "Meal scan"
                    is MealSource.Description -> "Described meal"
                    is MealSource.Barcode -> "Barcode"
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
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
        if (source is MealSource.Description && scan is ScanState.Analyzing) {
            item { Text("“${source.text}”", style = MaterialTheme.typography.bodyLarge) }
        }

        when (scan) {
            is ScanState.Analyzing -> item {
                LoadingCard(
                    if (source is MealSource.Barcode) "Looking up the product…"
                    else "Identifying foods and estimating portions…"
                )
            }
            is ScanState.Failed -> item { Text(scan.message, color = MaterialTheme.colorScheme.error) }
            is ScanState.Result -> item { AnalysisCard(scan.analysis, onLog = viewModel::logScannedMeal) }
            is ScanState.ProductFound -> item { ProductPortionCard(scan.product, onLog = viewModel::logProduct) }
            ScanState.Idle -> Unit
        }

        if (source is MealSource.Barcode) {
            if (scan is ScanState.Failed || scan is ScanState.ProductFound) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onScanAgain, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.QrCodeScanner, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan again")
                        }
                        OutlinedButton(onClick = onDescribeInstead, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Mic, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Describe it")
                        }
                    }
                }
            }
            if (scan is ScanState.Failed) {
                item {
                    TextButton(onClick = { viewModel.retryScan("") }, modifier = Modifier.fillMaxWidth()) { Text("Try the lookup again") }
                }
            }
        } else if (scan !is ScanState.Analyzing) {
            item {
                if (source is MealSource.Description) {
                    OutlinedTextField(
                        input, { input = it },
                        label = { Text("Edit what you ate") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        input, { input = it },
                        label = { Text("Add a correction (optional)") },
                        placeholder = { Text("e.g. 200 g chicken, cooked without oil") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                FilledTonalButton(onClick = { viewModel.retryScan(input) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Re-analyze")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductPortionCard(product: PackagedFood, onLog: (PackagedFood, Double, PortionUnit) -> Unit) {
    var unit by rememberSaveable(product.barcode) {
        mutableStateOf(if (product.canUseServings) PortionUnit.SERVINGS else PortionUnit.GRAMS)
    }
    var amountText by rememberSaveable(product.barcode, unit) {
        mutableStateOf(if (unit == PortionUnit.SERVINGS) "1" else (product.servingGrams?.let { formatNumber(it) } ?: "100"))
    }
    val amount = amountText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 5000 }
    val preview = amount?.let { PackagedFoods.toMeal(product, it, unit) }?.totals

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(product.displayName, style = MaterialTheme.typography.titleLarge)
            product.per100g?.let {
                Text(
                    "Per 100 g: ${formatNumber(it.calories)} kcal · P ${formatNumber(it.proteinG)} · C ${formatNumber(it.carbsG)} · F ${formatNumber(it.fatG)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            product.servingLabel?.let { Text("Serving: $it", style = MaterialTheme.typography.bodySmall) }

            if (product.canUseGrams && product.canUseServings) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(unit == PortionUnit.SERVINGS, { unit = PortionUnit.SERVINGS }, label = { Text("Servings") })
                    FilterChip(unit == PortionUnit.GRAMS, { unit = PortionUnit.GRAMS }, label = { Text("Grams") })
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { new -> amountText = new.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                label = { Text(if (unit == PortionUnit.SERVINGS) "How many servings?" else "How many grams?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (preview != null) {
                Text(
                    "${preview.calories} kcal · P ${preview.proteinG} g · C ${preview.carbsG} g · F ${preview.fatG} g",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = { amount?.let { onLog(product, it, unit) } },
                enabled = preview != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Log this") }
        }
    }
}

@Composable
private fun DescribeMealDialog(
    text: String,
    onTextChange: (String) -> Unit,
    voice: VoiceInput,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What did you eat?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("e.g. 2 scrambled eggs, a slice of toast with butter and a latte") },
                    minLines = 3,
                    trailingIcon = if (voice.isAvailable) {
                        { IconButton(onClick = voice::start) { Icon(Icons.Filled.Mic, contentDescription = "Speak") } }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (voice.isAvailable) "Type it or tap the mic and say it. Amounts help, e.g. \"a cup of rice\"."
                    else "Amounts help, e.g. \"a cup of rice\" or \"150 g chicken\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAnalyze, enabled = text.isNotBlank()) { Text("Estimate macros") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AnalysisCard(analysis: MealAnalysis, onLog: (MealAnalysis) -> Unit) {
    // Let the user fix portions and drop foods the AI misidentified before logging.
    var items by remember(analysis) { mutableStateOf(analysis.items.map { EditableItem(it) }) }
    val edited = analysis.withItems(items)
    val t = edited.totals

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(analysis.mealName, style = MaterialTheme.typography.titleLarge)
            Text(
                "${t.calories} kcal · P ${t.proteinG} g · C ${t.carbsG} g · F ${t.fatG} g",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Confidence: ${analysis.confidence} · tap a food to change its portion",
                style = MaterialTheme.typography.labelMedium,
            )
            MealItemsEditor(items) { items = it }
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
