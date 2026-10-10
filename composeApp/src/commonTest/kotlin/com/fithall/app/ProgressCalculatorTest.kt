package com.fithall.app

import com.fithall.app.domain.ProgressCalculator
import com.fithall.app.model.Exercise
import com.fithall.app.model.ExerciseLog
import com.fithall.app.model.SetLog
import com.fithall.app.model.WorkoutDay
import com.fithall.app.model.WorkoutLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressCalculatorTest {

    private fun log(day: String, millis: Long, vararg exercises: ExerciseLog) =
        WorkoutLog(id = day, dayKey = day, startedAtMillis = millis, finishedAtMillis = millis + 45 * 60_000, title = "W", exercises = exercises.toList())

    private fun bench(vararg sets: Pair<Double, Int>) =
        ExerciseLog("Bench Press", sets = sets.map { (kg, reps) -> SetLog(kg, reps, completed = true) })

    @Test
    fun epleyOneRepMax() {
        assertEquals(100.0, ProgressCalculator.estimatedOneRepMax(100.0, 1))
        assertEquals(100.0 * (1 + 10 / 30.0), ProgressCalculator.estimatedOneRepMax(100.0, 10))
    }

    @Test
    fun volumeAndDurationCountCompletedSetsOnly() {
        val w = log(
            "2026-10-05", 0,
            ExerciseLog("Squat", sets = listOf(SetLog(100.0, 5, true), SetLog(100.0, 5, false))),
        )
        assertEquals(500.0, w.volumeKg)
        assertEquals(1, w.completedSets)
        assertEquals(45, w.durationMinutes)
    }

    @Test
    fun weeklyStatsAndStreak() {
        // 2026-10-10 is a Saturday. Weeks start Monday: Oct 5, Sep 28, Sep 21.
        val logs = listOf(
            log("2026-10-06", 3, bench(60.0 to 10)),
            log("2026-09-29", 2, bench(60.0 to 8)),
            log("2026-09-22", 1, bench(57.5 to 8)),
            log("2026-09-01", 0, bench(50.0 to 8)),
        )
        val stats = ProgressCalculator.weeklyStats(logs, "2026-10-10")
        assertEquals(1, stats.workoutsLast7Days)
        assertEquals(600.0, stats.volumeLast7DaysKg)
        assertEquals(4, stats.totalWorkouts)
        assertEquals(3, stats.weekStreak)
    }

    @Test
    fun streakIgnoresCurrentWeekUntilSomethingIsLogged() {
        val logs = listOf(log("2026-10-02", 0, bench(60.0 to 10))) // previous week
        assertEquals(1, ProgressCalculator.weeklyStats(logs, "2026-10-05").weekStreak)
        assertEquals(0, ProgressCalculator.weeklyStats(logs, "2026-10-14").weekStreak)
    }

    @Test
    fun exerciseProgressUsesBestSetPerSession() {
        val logs = listOf(
            log("2026-10-01", 1, bench(60.0 to 8, 70.0 to 3)),
            log("2026-10-08", 2, bench(65.0 to 8)),
        )
        val progress = ProgressCalculator.exerciseProgress(logs, "bench press")
        assertTrue(progress.weighted)
        assertEquals(listOf("2026-10-01", "2026-10-08"), progress.points.map { it.dayKey })
        // 70 kg x 3 (77 kg e1RM) beats 60 kg x 8 (76 kg) in the first session.
        assertEquals(ProgressCalculator.estimatedOneRepMax(70.0, 3), progress.points[0].value)
        assertEquals(ProgressCalculator.estimatedOneRepMax(65.0, 8), progress.best)
    }

    @Test
    fun bodyweightExercisesTrackReps() {
        val pushups = ExerciseLog("Push-up", sets = listOf(SetLog(null, 15, true), SetLog(null, 12, true)))
        val progress = ProgressCalculator.exerciseProgress(listOf(log("2026-10-01", 1, pushups)), "Push-up")
        assertEquals(false, progress.weighted)
        assertEquals(15.0, progress.best)
    }

    @Test
    fun newSessionPrefillsFromLastTime() {
        val history = listOf(
            log("2026-10-01", 1, bench(60.0 to 8, 60.0 to 7)),
            log("2026-10-04", 2, bench(62.5 to 8, 62.5 to 8)),
        )
        val day = WorkoutDay(
            "Day 1", "Push", 60, "",
            listOf(Exercise("Bench Press", 3, "8-10", 120, ""), Exercise("Plank", 3, "30 s", 60, "")),
        )
        val (bench, plank) = ProgressCalculator.newSessionExercises(day, history)
        assertEquals(listOf(62.5, 62.5, 62.5), bench.sets.map { it.weightKg })
        assertEquals(listOf(8, 8, 8), bench.sets.map { it.reps })
        assertTrue(bench.sets.none { it.completed })
        assertEquals("3 × 8-10", bench.target)
        assertEquals(listOf(30, 30, 30), plank.sets.map { it.reps })
        assertNull(plank.sets.first().weightKg)
    }

    @Test
    fun parsesRepTargets() {
        assertEquals(8, ProgressCalculator.firstNumber("8-10"))
        assertEquals(12, ProgressCalculator.firstNumber("12 each side"))
        assertEquals(0, ProgressCalculator.firstNumber("AMRAP"))
    }

    @Test
    fun formatsNumbers() {
        assertEquals("62", ProgressCalculator.formatNumber(62.0))
        assertEquals("62.5", ProgressCalculator.formatNumber(62.5))
        assertEquals("1.3", ProgressCalculator.formatNumber(1.26))
    }
}
