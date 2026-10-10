package com.example.fithall.domain

import com.example.fithall.model.ExerciseLog
import com.example.fithall.model.SetLog
import com.example.fithall.model.WorkoutDay
import com.example.fithall.model.WorkoutLog
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/** One point on an exercise's progress chart. */
data class ExercisePoint(val dayKey: String, val value: Double)

data class ExerciseProgress(
    val name: String,
    /** True when sets carry weight: values are estimated 1-rep max in kg; otherwise best reps. */
    val weighted: Boolean,
    val points: List<ExercisePoint>,
) {
    val best: Double get() = points.maxOfOrNull { it.value } ?: 0.0
    val change: Double get() = if (points.size < 2) 0.0 else points.last().value - points.first().value
}

data class WeeklyStats(
    val workoutsLast7Days: Int,
    val volumeLast7DaysKg: Double,
    val totalWorkouts: Int,
    /** Consecutive Monday-to-Sunday weeks with at least one workout. */
    val weekStreak: Int,
)

object ProgressCalculator {

    /** Epley formula. Only meaningful for sets of roughly 1-12 reps. */
    fun estimatedOneRepMax(weightKg: Double, reps: Int): Double =
        if (reps <= 1) weightKg else weightKg * (1 + reps / 30.0)

    fun weeklyStats(logs: List<WorkoutLog>, today: String): WeeklyStats {
        val todayDate = LocalDate.parse(today)
        val weekAgo = todayDate.minus(DatePeriod(days = 6))
        val recent = logs.filter { LocalDate.parse(it.dayKey) in weekAgo..todayDate }
        return WeeklyStats(
            workoutsLast7Days = recent.size,
            volumeLast7DaysKg = recent.sumOf { it.volumeKg },
            totalWorkouts = logs.size,
            weekStreak = weekStreak(logs, todayDate),
        )
    }

    private fun weekStart(date: LocalDate): LocalDate = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))

    private fun weekStreak(logs: List<WorkoutLog>, today: LocalDate): Int {
        val trainedWeeks = logs.map { weekStart(LocalDate.parse(it.dayKey)) }.toSet()
        var week = weekStart(today)
        // The current week still counts as "in progress" if nothing is logged yet.
        if (week !in trainedWeeks) week = week.minus(DatePeriod(days = 7))
        var streak = 0
        while (week in trainedWeeks) {
            streak++
            week = week.minus(DatePeriod(days = 7))
        }
        return streak
    }

    /** Names of every exercise with at least one completed set, most recently trained first. */
    fun trainedExercises(logs: List<WorkoutLog>): List<String> =
        logs.sortedByDescending { it.startedAtMillis }
            .flatMap { log -> log.exercises.filter { ex -> ex.sets.any { it.completed } }.map { it.name } }
            .distinctBy { it.lowercase() }

    fun exerciseProgress(logs: List<WorkoutLog>, name: String): ExerciseProgress {
        val sessions = logs.sortedBy { it.startedAtMillis }.mapNotNull { log ->
            log.exercises.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.sets?.filter { it.completed }?.takeIf { it.isNotEmpty() }
                ?.let { log.dayKey to it }
        }
        val weighted = sessions.any { (_, sets) -> sets.any { (it.weightKg ?: 0.0) > 0 } }
        val points = sessions.map { (day, sets) ->
            val value = if (weighted) {
                sets.maxOf { estimatedOneRepMax(it.weightKg ?: 0.0, it.reps) }
            } else {
                sets.maxOf { it.reps }.toDouble()
            }
            ExercisePoint(day, value)
        }
        return ExerciseProgress(name, weighted, points)
    }

    /** The completed sets from the most recent session of [name], used to prefill the next one. */
    fun lastSets(logs: List<WorkoutLog>, name: String): List<SetLog>? =
        logs.sortedByDescending { it.startedAtMillis }
            .firstNotNullOfOrNull { log ->
                log.exercises.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?.sets?.filter { it.completed }?.takeIf { it.isNotEmpty() }
            }

    /** Turns a plan day into a session, prefilling weights and reps from last time. */
    fun newSessionExercises(day: WorkoutDay, history: List<WorkoutLog>): List<ExerciseLog> =
        day.exercises.map { planned ->
            val previous = lastSets(history, planned.name)
            val defaultReps = firstNumber(planned.reps)
            ExerciseLog(
                name = planned.name,
                target = "${planned.sets} × ${planned.reps}",
                restSeconds = planned.restSeconds,
                sets = List(planned.sets.coerceIn(1, 10)) { i ->
                    val prev = previous?.getOrNull(i) ?: previous?.lastOrNull()
                    SetLog(weightKg = prev?.weightKg, reps = prev?.reps ?: defaultReps)
                },
            )
        }

    /** "8-10" -> 8, "12 each side" -> 12, "30 s" -> 30, "AMRAP" -> 0. */
    fun firstNumber(text: String): Int = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: 0

    /** Compact text summary for AI prompts. */
    fun historySummary(logs: List<WorkoutLog>, limit: Int = 6): String =
        logs.sortedByDescending { it.startedAtMillis }.take(limit).joinToString("\n") { log ->
            "- ${log.dayKey} ${log.title}: " + log.exercises
                .filter { ex -> ex.sets.any { it.completed } }
                .joinToString("; ") { ex ->
                    ex.name + " " + ex.sets.filter { it.completed }.joinToString(", ") { s ->
                        s.weightKg?.takeIf { it > 0 }?.let { kg -> "${formatNumber(kg)}kg x ${s.reps}" } ?: "${s.reps} reps"
                    }
                }
        }

    /** Whole number when possible, otherwise one decimal: 62.0 -> "62", 62.55 -> "62.6". */
    fun formatNumber(value: Double): String {
        val tenths = kotlin.math.round(value * 10) / 10
        return if (tenths % 1.0 == 0.0) tenths.toLong().toString() else tenths.toString()
    }
}
