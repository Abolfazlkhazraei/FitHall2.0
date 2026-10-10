package com.fithall.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fithall.app.AppViewModel
import com.fithall.app.domain.MacroCalculator
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.Equipment
import com.fithall.app.model.Experience
import com.fithall.app.model.Goal
import com.fithall.app.model.Sex
import com.fithall.app.model.TargetAdjustment
import com.fithall.app.model.UserProfile
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(onSave: (UserProfile) -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Welcome to FitHall", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tell us about yourself. Your AI coach uses this to build a workout plan and nutrition " +
                "targets that fit your level and your goal.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        ProfileForm(initial = null, saveLabel = "Create my plan", onSave = onSave)
    }
}

@Composable
fun ProfileScreen(profile: UserProfile, adjustment: TargetAdjustment, viewModel: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Your profile", style = MaterialTheme.typography.headlineSmall)
        val targets = MacroCalculator.targets(profile, adjustment)
        Text(
            "Maintenance ≈ ${MacroCalculator.tdee(profile).roundToInt()} kcal/day · " +
                "Target ${targets.calories} kcal · P ${targets.proteinG} g · C ${targets.carbsG} g · F ${targets.fatG} g",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!adjustment.isZero) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Includes weekly check-in adjustments: " + listOfNotNull(
                        adjustment.calories.takeIf { it != 0 }?.let { "${if (it > 0) "+" else ""}$it kcal" },
                        adjustment.proteinG.takeIf { it != 0 }?.let { "${if (it > 0) "+" else ""}$it g protein" },
                    ).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::resetAdjustment) { Text("Reset") }
            }
        }
        Spacer(Modifier.height(8.dp))
        ProfileForm(
            initial = profile,
            saveLabel = "Save & regenerate plans",
            onSave = {
                viewModel.saveProfile(it)
                viewModel.generateWorkoutPlan()
                viewModel.generateNutritionPlan()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileForm(initial: UserProfile?, saveLabel: String, onSave: (UserProfile) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var age by rememberSaveable { mutableStateOf(initial?.age?.toString() ?: "") }
    var sex by rememberSaveable { mutableStateOf(initial?.sex ?: Sex.MALE) }
    var height by rememberSaveable { mutableStateOf(initial?.heightCm?.toString() ?: "") }
    var weight by rememberSaveable { mutableStateOf(initial?.weightKg?.toString() ?: "") }
    var activity by rememberSaveable { mutableStateOf(initial?.activityLevel ?: ActivityLevel.MODERATE) }
    var experience by rememberSaveable { mutableStateOf(initial?.experience ?: Experience.BEGINNER) }
    var goal by rememberSaveable { mutableStateOf(initial?.goal ?: Goal.BUILD_MUSCLE) }
    var equipment by rememberSaveable { mutableStateOf(initial?.equipment ?: Equipment.FULL_GYM) }
    var days by rememberSaveable { mutableStateOf((initial?.trainingDaysPerWeek ?: 3).toFloat()) }
    var minutes by rememberSaveable { mutableStateOf(initial?.sessionMinutes ?: 60) }
    var diet by rememberSaveable { mutableStateOf(initial?.dietaryNotes ?: "") }
    var injuries by rememberSaveable { mutableStateOf(initial?.injuries ?: "") }

    val ageValue = age.toIntOrNull()?.takeIf { it in 13..100 }
    val heightValue = height.toIntOrNull()?.takeIf { it in 100..250 }
    val weightValue = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..300.0 }
    val valid = name.isNotBlank() && ageValue != null && heightValue != null && weightValue != null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(age, { age = it }, "Age", Modifier.weight(1f), error = age.isNotEmpty() && ageValue == null)
            NumberField(height, { height = it }, "Height (cm)", Modifier.weight(1f), error = height.isNotEmpty() && heightValue == null)
            NumberField(weight, { weight = it }, "Weight (kg)", Modifier.weight(1f), decimal = true, error = weight.isNotEmpty() && weightValue == null)
        }

        Section("Sex (for calorie calculation)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { FilterChip(sex == it, { sex = it }, label = { Text(it.label) }) }
        }

        Section("Main goal")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.entries.forEach { FilterChip(goal == it, { goal = it }, label = { Text(it.label) }) }
        }

        Section("Daily activity level")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityLevel.entries.forEach { FilterChip(activity == it, { activity = it }, label = { Text(it.label) }) }
        }
        Text(activity.description, style = MaterialTheme.typography.bodySmall)

        Section("Training experience")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Experience.entries.forEach { FilterChip(experience == it, { experience = it }, label = { Text(it.label) }) }
        }
        Text(experience.description, style = MaterialTheme.typography.bodySmall)

        Section("Equipment")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.entries.forEach { FilterChip(equipment == it, { equipment = it }, label = { Text(it.label) }) }
        }

        Section("Training days per week: ${days.roundToInt()}")
        Slider(value = days, onValueChange = { days = it }, valueRange = 2f..6f, steps = 3)

        Section("Session length")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 45, 60, 75, 90).forEach { FilterChip(minutes == it, { minutes = it }, label = { Text("$it min") }) }
        }

        OutlinedTextField(
            diet, { diet = it },
            label = { Text("Dietary preferences / allergies (optional)") },
            placeholder = { Text("e.g. vegetarian, no nuts, halal") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            injuries, { injuries = it },
            label = { Text("Injuries or limitations (optional)") },
            placeholder = { Text("e.g. lower back pain, bad left knee") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                onSave(
                    UserProfile(
                        name = name.trim(),
                        age = ageValue!!,
                        sex = sex,
                        heightCm = heightValue!!,
                        weightKg = weightValue!!,
                        activityLevel = activity,
                        experience = experience,
                        goal = goal,
                        equipment = equipment,
                        trainingDaysPerWeek = days.roundToInt(),
                        sessionMinutes = minutes,
                        dietaryNotes = diet.trim(),
                        injuries = injuries.trim(),
                    )
                )
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(saveLabel) }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    decimal: Boolean = false,
    error: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }) },
        label = { Text(label) },
        singleLine = true,
        isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
