package com.example.fithall.model

import kotlinx.serialization.Serializable

@Serializable
enum class Sex(val label: String) { MALE("Male"), FEMALE("Female") }

@Serializable
enum class ActivityLevel(val label: String, val description: String, val multiplier: Double) {
    SEDENTARY("Sedentary", "Desk job, little exercise", 1.2),
    LIGHT("Lightly active", "Exercise 1-3 days/week", 1.375),
    MODERATE("Moderately active", "Exercise 3-5 days/week", 1.55),
    VERY("Very active", "Hard exercise 6-7 days/week", 1.725),
    ATHLETE("Athlete", "Physical job or 2x/day training", 1.9),
}

@Serializable
enum class Experience(val label: String, val description: String) {
    BEGINNER("Beginner", "Less than 6 months of consistent training"),
    INTERMEDIATE("Intermediate", "6 months to 2 years"),
    ADVANCED("Advanced", "More than 2 years"),
}

@Serializable
enum class Goal(val label: String) {
    LOSE_FAT("Lose fat"),
    BUILD_MUSCLE("Build muscle"),
    GAIN_STRENGTH("Get stronger"),
    IMPROVE_ENDURANCE("Improve endurance"),
    MAINTAIN("Stay healthy & maintain"),
}

@Serializable
enum class Equipment(val label: String) {
    FULL_GYM("Full gym"),
    HOME_DUMBBELLS("Home: dumbbells & bench"),
    BODYWEIGHT("Bodyweight only"),
}

@Serializable
data class UserProfile(
    val name: String,
    val age: Int,
    val sex: Sex,
    val heightCm: Int,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
    val experience: Experience,
    val goal: Goal,
    val equipment: Equipment,
    val trainingDaysPerWeek: Int,
    val sessionMinutes: Int,
    val dietaryNotes: String = "",
    val injuries: String = "",
)

@Serializable
data class MacroTargets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
) {
    operator fun plus(other: MacroTargets) = MacroTargets(
        calories + other.calories,
        proteinG + other.proteinG,
        carbsG + other.carbsG,
        fatG + other.fatG,
    )

    companion object {
        val ZERO = MacroTargets(0, 0, 0, 0)
    }
}

// ---- Nutrition ----

@Serializable
data class FoodItem(
    val name: String,
    val portion: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

@Serializable
data class MealAnalysis(
    val mealName: String,
    val items: List<FoodItem>,
    /** "high", "medium" or "low": how sure the model is about portions and identification. */
    val confidence: String,
    val notes: String,
) {
    val totals: MacroTargets
        get() = items.fold(MacroTargets.ZERO) { acc, item ->
            acc + MacroTargets(item.calories, item.proteinG, item.carbsG, item.fatG)
        }
}

@Serializable
data class MealEntry(
    val id: String,
    val timestampMillis: Long,
    /** Local date as yyyy-MM-dd, used to group meals per day. */
    val dayKey: String,
    val analysis: MealAnalysis,
)

@Serializable
data class PlannedMeal(
    val name: String,
    val description: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

@Serializable
data class NutritionPlan(
    val summary: String,
    val meals: List<PlannedMeal>,
    val tips: List<String>,
)

// ---- Workouts ----

@Serializable
data class Exercise(
    val name: String,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
    val notes: String,
)

@Serializable
data class WorkoutDay(
    val dayName: String,
    val focus: String,
    val durationMinutes: Int,
    val warmup: String,
    val exercises: List<Exercise>,
)

@Serializable
data class WorkoutPlan(
    val title: String,
    val summary: String,
    val durationWeeks: Int,
    val days: List<WorkoutDay>,
    val progression: String,
    val safetyNotes: String,
)

// ---- Workout logging & progress ----

@Serializable
data class SetLog(
    /** Null for bodyweight or timed exercises. */
    val weightKg: Double? = null,
    val reps: Int = 0,
    val completed: Boolean = false,
)

@Serializable
data class ExerciseLog(
    val name: String,
    /** What the plan asked for, e.g. "3 × 8-10"; empty for exercises added during the session. */
    val target: String = "",
    val restSeconds: Int = 0,
    val sets: List<SetLog>,
)

@Serializable
data class WorkoutLog(
    val id: String,
    val dayKey: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val title: String,
    val exercises: List<ExerciseLog>,
    val notes: String = "",
) {
    val completedSets: Int get() = exercises.sumOf { ex -> ex.sets.count { it.completed } }

    /** Total kg lifted across completed sets. */
    val volumeKg: Double
        get() = exercises.sumOf { ex -> ex.sets.filter { it.completed }.sumOf { (it.weightKg ?: 0.0) * it.reps } }

    val durationMinutes: Int?
        get() = finishedAtMillis?.let { ((it - startedAtMillis) / 60_000).toInt() }
}

@Serializable
data class WeightEntry(
    val dayKey: String,
    val weightKg: Double,
)

// ---- Coach chat ----

@Serializable
enum class ChatRole { USER, ASSISTANT }

@Serializable
data class ChatMessage(val role: ChatRole, val text: String)
