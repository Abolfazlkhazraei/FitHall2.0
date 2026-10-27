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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.runtime.LaunchedEffect
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
fun NutritionScreen(
    state: AppState,
    viewModel: AppViewModel,
    /** A way of logging chosen elsewhere (the quick-add sheet), started once this screen is showing. */
    request: MealInput? = null,
    onRequestHandled: () -> Unit = {},
) {
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

    LaunchedEffect(request) {
        when (request) {
            MealInput.PHOTO -> launcher.takePhoto()
            MealInput.GALLERY -> launcher.pickFromGallery()
            MealInput.BARCODE -> barcodeScanner.scan()
            MealInput.DESCRIBE -> describing = true
            null -> return@LaunchedEffect
        }
        onRequestHandled()
    }

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
    val eaten = state.todaysTotals
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeroHeader(
                title = "Eat",
                subtitle = longDate(state.today),
            ) {
                CalorieSummary(eaten, targets)
            }
        }
        state.profile?.let(MacroCalculator::safetyNotice)?.let { notice -> item { Padded { SafetyNotice(notice) } } }
        item {
            Padded {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(Icons.Filled.CameraAlt, "Snap", Palette.lime, launcher::takePhoto, Modifier.weight(1f), launcher.canTakePhoto)
                    ActionTile(Icons.Filled.PhotoLibrary, "Gallery", Palette.text, launcher::pickFromGallery, Modifier.weight(1f), launcher.canPickFromGallery)
                    ActionTile(Icons.Filled.Mic, "Say it", Palette.blue, { describing = true }, Modifier.weight(1f))
                    ActionTile(Icons.Filled.QrCodeScanner, "Barcode", Palette.text, barcodeScanner::scan, Modifier.weight(1f), barcodeScanner.isAvailable)
                }
            }
        }

        item {
            Padded {
                SectionHeader("Today's meals") {
                    if (state.todaysMeals.isNotEmpty()) Pill("${eaten.calories} kcal", MacroColors.calories)
                }
            }
        }
        if (state.todaysMeals.isEmpty()) {
            item {
                Padded {
                    EmptyState(
                        Icons.Filled.Restaurant,
                        "Nothing logged yet",
                        "Snap your plate, say what you ate, or scan a barcode.",
                        accent = MacroColors.carbs,
                    )
                }
            }
        }
        items(state.todaysMeals.asReversed(), key = { it.id }) { meal ->
            Padded { MealRow(meal, onSave = viewModel::updateMeal, onDelete = { viewModel.deleteMeal(meal.id) }) }
        }

        item {
            Padded {
                SectionHeader("AI meal plan") {
                    TextButton(onClick = viewModel::generateNutritionPlan, enabled = !state.generatingNutrition) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.nutritionPlan == null) "Generate" else "Regenerate")
                    }
                }
            }
        }
        state.nutritionPlanFailure?.let { failure ->
            item { Padded { ErrorCard(failure, onRetry = viewModel::generateNutritionPlan, onDismiss = viewModel::dismissFailures) } }
        }
        item { Padded { NutritionPlanSection(state) } }
    }
}

private val weekdayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** "2026-10-11" -> "Sunday, Oct 11". */
internal fun longDate(dayKey: String): String = runCatching {
    val date = kotlinx.datetime.LocalDate.parse(dayKey)
    "${weekdayNames[date.dayOfWeek.ordinal]}, ${shortDate(dayKey)}"
}.getOrDefault(dayKey)

/** Inside the hero header: a big calorie ring and three macro rings. */
@Composable
private fun CalorieSummary(eaten: MacroTargets, targets: MacroTargets) {
    val left = targets.calories - eaten.calories
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ProgressRing(
            progress = if (targets.calories == 0) 0f else eaten.calories.toFloat() / targets.calories,
            size = 140.dp,
            stroke = 14.dp,
            color = if (left >= 0) Palette.lime else MacroColors.carbs,
            trackColor = Palette.lime.copy(alpha = 0.14f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${kotlin.math.abs(left)}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Brand.onGradient,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    if (left >= 0) "kcal left" else "kcal over",
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.onGradientMuted,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${eaten.calories} / ${targets.calories} kcal", style = MaterialTheme.typography.titleSmall, color = Brand.onGradient)
            Text("eaten today", style = MaterialTheme.typography.labelSmall, color = Brand.onGradientMuted)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        MacroRing("Protein", eaten.proteinG, targets.proteinG, MacroColors.protein)
        MacroRing("Carbs", eaten.carbsG, targets.carbsG, MacroColors.carbs)
        MacroRing("Fat", eaten.fatG, targets.fatG, MacroColors.fat)
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
    ContentCard(onClick = { editing = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Restaurant, MacroColors.carbs, size = 44.dp)
            HSpace(12.dp)
            Column(Modifier.weight(1f)) {
                Text(meal.analysis.mealName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "P ${t.proteinG} g · C ${t.carbsG} g · F ${t.fatG} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill("${t.calories} kcal", MacroColors.calories)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete meal", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(plan.summary, style = MaterialTheme.typography.bodyMedium)
            plan.meals.forEach { meal ->
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(meal.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Pill("${meal.calories} kcal", MacroColors.calories)
                    }
                    Text(meal.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "P ${meal.proteinG} g · C ${meal.carbsG} g · F ${meal.fatG} g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (plan.tips.isNotEmpty()) {
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.tertiary, size = 32.dp)
                        HSpace(10.dp)
                        Text("Tips", style = MaterialTheme.typography.titleMedium)
                    }
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
        Modifier.fillMaxSize().statusBarsPadding(),
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
