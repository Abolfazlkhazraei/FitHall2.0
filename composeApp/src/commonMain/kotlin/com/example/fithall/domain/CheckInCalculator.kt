package com.example.fithall.domain

import com.example.fithall.ai.AiPrompts
import com.example.fithall.model.CheckInStatus
import com.example.fithall.model.Exercise
import com.example.fithall.model.MacroTargets
import com.example.fithall.model.MealEntry
import com.example.fithall.model.TargetAdjustment
import com.example.fithall.model.TrainingChange
import com.example.fithall.model.UserProfile
import com.example.fithall.model.WeeklyCheckIn
import com.example.fithall.model.WeightEntry
import com.example.fithall.model.WorkoutLog
import com.example.fithall.model.WorkoutPlan
import com.example.fithall.domain.ProgressCalculator.formatNumber
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlin.math.roundToInt

data class NutritionAdherence(val loggedDays: Int, val avgCalories: Int, val avgProteinG: Int)

data class ExerciseTrend(
    val name: String,
    val sessions: Int,
    val firstValue: Double,
    val lastValue: Double,
    val weighted: Boolean,
    /** No new best in the most recent sessions. */
    val stalled: Boolean,
)

/**
 * Everything the weekly check-in needs that can be computed exactly: the AI only interprets
 * these numbers and proposes changes, and every change it proposes is bounded here before use.
 */
object CheckInCalculator {

    const val INTERVAL_DAYS = 7

    /** Calories can move at most this much per check-in, whatever the AI suggests. */
    const val MAX_CALORIE_STEP = 300
    const val MAX_PROTEIN_STEP = 30

    fun daysSinceLast(checkIns: List<WeeklyCheckIn>, today: String): Int? =
        checkIns.maxByOrNull { it.createdAtMillis }?.let { LocalDate.parse(it.dayKey).daysUntil(LocalDate.parse(today)) }

    /** True when a week has passed since the last check-in, or there's never been one but there is data to review. */
    fun isDue(
        checkIns: List<WeeklyCheckIn>,
        today: String,
        logs: List<WorkoutLog>,
        weights: List<WeightEntry>,
        meals: List<MealEntry>,
    ): Boolean {
        val since = daysSinceLast(checkIns, today)
        if (since != null) return since >= INTERVAL_DAYS
        return logs.isNotEmpty() || weights.size >= 2 || meals.map { it.dayKey }.distinct().size >= 3
    }

    /**
     * Least-squares slope of body weight over the last [windowDays], in kg per week. Needs at
     * least two entries spanning [minSpanDays], otherwise there isn't a trend worth acting on.
     */
    fun weightRateKgPerWeek(weights: List<WeightEntry>, today: String, windowDays: Int = 28, minSpanDays: Int = 5): Double? {
        val end = LocalDate.parse(today)
        val start = end.minus(DatePeriod(days = windowDays))
        val points = weights
            .map { LocalDate.parse(it.dayKey) to it.weightKg }
            .filter { (date, _) -> date in start..end }
        if (points.size < 2) return null
        val xs = points.map { (date, _) -> start.daysUntil(date).toDouble() }
        if (xs.max() - xs.min() < minSpanDays) return null
        val ys = points.map { it.second }
        val meanX = xs.average()
        val meanY = ys.average()
        val num = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) }
        val den = xs.sumOf { (it - meanX) * (it - meanX) }
        return num / den * 7
    }

    fun nutritionAdherence(meals: List<MealEntry>, today: String, days: Int = 7): NutritionAdherence {
        val end = LocalDate.parse(today)
        val start = end.minus(DatePeriod(days = days - 1))
        val byDay = meals.filter { LocalDate.parse(it.dayKey) in start..end }.groupBy { it.dayKey }
        if (byDay.isEmpty()) return NutritionAdherence(0, 0, 0)
        val totals = byDay.values.map { dayMeals -> dayMeals.fold(MacroTargets.ZERO) { acc, m -> acc + m.analysis.totals } }
        return NutritionAdherence(
            loggedDays = byDay.size,
            avgCalories = totals.map { it.calories }.average().roundToInt(),
            avgProteinG = totals.map { it.proteinG }.average().roundToInt(),
        )
    }

    fun exerciseTrends(logs: List<WorkoutLog>, today: String, windowDays: Int = 35): List<ExerciseTrend> {
        val start = LocalDate.parse(today).minus(DatePeriod(days = windowDays))
        val recent = logs.filter { LocalDate.parse(it.dayKey) >= start }
        return ProgressCalculator.trainedExercises(recent).mapNotNull { name ->
            val progress = ProgressCalculator.exerciseProgress(recent, name)
            val values = progress.points.map { it.value }
            if (values.isEmpty()) return@mapNotNull null
            // With 3+ sessions: stalled if the latest sessions (up to 3) never beat the best before them.
            val stalled = values.size >= 3 && run {
                val recentCount = minOf(3, values.size - 1)
                values.takeLast(recentCount).max() <= values.dropLast(recentCount).max()
            }
            ExerciseTrend(name, values.size, values.first(), values.last(), progress.weighted, stalled)
        }
    }

    /** Plain-text report the AI reviews. Kept factual: the model is told to rely on it. */
    fun buildReport(
        profile: UserProfile,
        targets: MacroTargets,
        adjustment: TargetAdjustment,
        meals: List<MealEntry>,
        logs: List<WorkoutLog>,
        weights: List<WeightEntry>,
        plan: WorkoutPlan?,
        previous: WeeklyCheckIn?,
        today: String,
    ): String = buildString {
        appendLine("Date: $today")
        appendLine()
        appendLine("## Profile")
        appendLine(AiPrompts.profileSummary(profile))
        appendLine()

        appendLine("## Current daily targets")
        appendLine("${targets.calories} kcal, ${targets.proteinG} g protein, ${targets.carbsG} g carbs, ${targets.fatG} g fat")
        if (!adjustment.isZero) {
            appendLine("(Includes earlier check-in adjustments of ${signed(adjustment.calories)} kcal and ${signed(adjustment.proteinG)} g protein.)")
        }
        appendLine("Minimum allowed intake: ${MacroCalculator.minimumCalories(profile)} kcal")
        appendLine()

        appendLine("## Body weight")
        val rate = weightRateKgPerWeek(weights, today)
        val recentWeights = weights.takeLast(8)
        if (recentWeights.isEmpty()) {
            appendLine("No weigh-ins logged.")
        } else {
            appendLine("Recent weigh-ins: " + recentWeights.joinToString(", ") { "${it.dayKey} ${formatNumber(it.weightKg)} kg" })
            if (rate != null) {
                val pct = rate / profile.weightKg * 100
                appendLine("Trend over the last 4 weeks: ${signedDecimal(rate)} kg/week (${signedDecimal(pct)}% of body weight per week)")
            } else {
                appendLine("Not enough weigh-ins over enough days to measure a trend.")
            }
        }
        appendLine()

        appendLine("## Nutrition, last 7 days")
        val nutrition = nutritionAdherence(meals, today)
        if (nutrition.loggedDays == 0) {
            appendLine("No meals logged.")
        } else {
            appendLine("Days with meals logged: ${nutrition.loggedDays} of 7")
            appendLine("Average on logged days: ${nutrition.avgCalories} kcal, ${nutrition.avgProteinG} g protein")
            appendLine("(Days may be partially logged, so averages can understate real intake.)")
        }
        appendLine()

        appendLine("## Training")
        val stats = ProgressCalculator.weeklyStats(logs, today)
        appendLine("Workouts in the last 7 days: ${stats.workoutsLast7Days} (planned: ${profile.trainingDaysPerWeek} per week)")
        appendLine("Week streak: ${stats.weekStreak}, total workouts logged: ${stats.totalWorkouts}")
        val trends = exerciseTrends(logs, today)
        if (trends.isNotEmpty()) {
            appendLine("Exercise trends over the last 5 weeks (estimated 1-rep max in kg, or best reps for bodyweight):")
            trends.forEach { t ->
                val unit = if (t.weighted) "kg" else "reps"
                appendLine(
                    "- ${t.name}: ${t.sessions} sessions, ${formatNumber(t.firstValue)} -> ${formatNumber(t.lastValue)} $unit" +
                        if (t.stalled) " (STALLED: no new best in recent sessions)" else ""
                )
            }
        }
        val recentSessions = ProgressCalculator.historySummary(logs)
        if (recentSessions.isNotBlank()) {
            appendLine("Recent sessions:")
            appendLine(recentSessions)
        }
        appendLine()

        appendLine("## Current workout plan")
        if (plan == null) {
            appendLine("No plan yet.")
        } else {
            appendLine(plan.title)
            plan.days.forEach { day ->
                appendLine("${day.dayName} (${day.focus}): " + day.exercises.joinToString("; ") { "${it.name} ${it.sets}x${it.reps}, rest ${it.restSeconds}s" })
            }
        }

        if (previous != null) {
            appendLine()
            appendLine("## Last check-in (${previous.dayKey}, ${previous.status.name.lowercase()})")
            appendLine(previous.result.summary)
            if (previous.status == CheckInStatus.APPLIED) {
                appendLine("Applied: ${signed(previous.appliedCalorieChange)} kcal, ${signed(previous.appliedProteinChange)} g protein, ${previous.appliedTrainingChanges} exercise changes")
            }
        }
    }.trim()

    /** Bounds a proposed change: per-check-in step limits, then overall limits on the running total. */
    fun boundedAdjustment(current: TargetAdjustment, calorieChange: Int, proteinChange: Int): TargetAdjustment {
        val calories = (current.calories + calorieChange.coerceIn(-MAX_CALORIE_STEP, MAX_CALORIE_STEP))
            .coerceIn(-MacroCalculator.MAX_CALORIE_ADJUSTMENT, MacroCalculator.MAX_CALORIE_ADJUSTMENT)
        val protein = (current.proteinG + proteinChange.coerceIn(-MAX_PROTEIN_STEP, MAX_PROTEIN_STEP))
            .coerceIn(-MacroCalculator.MAX_PROTEIN_ADJUSTMENT, MacroCalculator.MAX_PROTEIN_ADJUSTMENT)
        return TargetAdjustment(calories, protein)
    }

    /**
     * Applies exercise swaps/re-prescriptions by matching day and exercise names. Changes that
     * don't match anything in the plan are skipped. Returns the new plan and how many applied.
     */
    fun applyTrainingChanges(plan: WorkoutPlan, changes: List<TrainingChange>): Pair<WorkoutPlan, Int> {
        var applied = 0
        var days = plan.days
        changes.forEach { change ->
            var matched = false
            days = days.map { day ->
                if (matched || !day.dayName.equals(change.dayName, ignoreCase = true)) return@map day
                val index = day.exercises.indexOfFirst { it.name.equals(change.currentExercise, ignoreCase = true) }
                if (index < 0) return@map day
                matched = true
                val old = day.exercises[index]
                val updated = Exercise(
                    name = change.newExercise.ifBlank { old.name },
                    sets = change.sets.takeIf { it in 1..10 } ?: old.sets,
                    reps = change.reps.ifBlank { old.reps },
                    restSeconds = change.restSeconds.takeIf { it in 15..600 } ?: old.restSeconds,
                    notes = change.reason.ifBlank { old.notes },
                )
                day.copy(exercises = day.exercises.toMutableList().also { it[index] = updated })
            }
            if (matched) applied++
        }
        return plan.copy(days = days) to applied
    }

    private fun signed(value: Int) = if (value > 0) "+$value" else "$value"
    /** Two decimals with a sign, e.g. "+0.45" or "-0.3". */
    private fun signedDecimal(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        return if (rounded > 0) "+$rounded" else "$rounded"
    }
}
