package com.fithall.app

import com.fithall.app.ai.AiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.domain.CheckInCalculator
import com.fithall.app.domain.MacroCalculator
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.CheckInStatus
import com.fithall.app.model.Equipment
import com.fithall.app.model.Exercise
import com.fithall.app.model.ExerciseLog
import com.fithall.app.model.Experience
import com.fithall.app.model.FoodItem
import com.fithall.app.model.Goal
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.MealEntry
import com.fithall.app.model.SetLog
import com.fithall.app.model.Sex
import com.fithall.app.model.TargetAdjustment
import com.fithall.app.model.TrainingChange
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WeeklyCheckIn
import com.fithall.app.model.WeightEntry
import com.fithall.app.model.WorkoutDay
import com.fithall.app.model.WorkoutLog
import com.fithall.app.model.WorkoutPlan
import com.fithall.app.platform.todayKey
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckInTest {

    private val profile = UserProfile(
        "Sam", 30, Sex.MALE, 180, 80.0, ActivityLevel.MODERATE, Experience.INTERMEDIATE,
        Goal.LOSE_FAT, Equipment.FULL_GYM, 3, 60,
    )

    private val plan = WorkoutPlan(
        title = "Upper/Lower", summary = "", durationWeeks = 8, progression = "", safetyNotes = "",
        days = listOf(
            WorkoutDay("Day 1", "Upper", 60, "", listOf(Exercise("Bench Press", 3, "6-8", 150, ""), Exercise("Row", 3, "8-10", 90, ""))),
            WorkoutDay("Day 2", "Lower", 60, "", listOf(Exercise("Squat", 3, "5", 180, ""))),
        ),
    )

    private fun bench(day: String, millis: Long, kg: Double, reps: Int) = WorkoutLog(
        id = day, dayKey = day, startedAtMillis = millis, title = "Day 1",
        exercises = listOf(ExerciseLog("Bench Press", sets = listOf(SetLog(kg, reps, true)))),
    )

    // ---- Calculations ----

    @Test
    fun weightRateIsSlopePerWeek() {
        // Exactly -0.1 kg/day = -0.7 kg/week.
        val weights = (0..14 step 2).map { d -> WeightEntry("2026-10-" + (d + 1).toString().padStart(2, '0'), 80.0 - 0.1 * d) }
        val rate = CheckInCalculator.weightRateKgPerWeek(weights, "2026-10-15")
        assertNotNull(rate)
        assertTrue(abs(rate - -0.7) < 1e-9, "rate was $rate")
    }

    @Test
    fun weightRateNeedsEnoughSpread() {
        val close = listOf(WeightEntry("2026-10-09", 80.0), WeightEntry("2026-10-10", 79.0))
        assertNull(CheckInCalculator.weightRateKgPerWeek(close, "2026-10-10"))
        assertNull(CheckInCalculator.weightRateKgPerWeek(listOf(WeightEntry("2026-10-01", 80.0)), "2026-10-10"))
    }

    @Test
    fun adherenceAveragesLoggedDaysOnly() {
        fun meal(day: String, kcal: Int, protein: Int) =
            MealEntry(day + kcal, 0, day, MealAnalysis("m", listOf(FoodItem("f", "1", kcal, protein, 0, 0)), "high", ""))
        val meals = listOf(
            meal("2026-10-09", 1000, 60), meal("2026-10-09", 1000, 60), // 2000 kcal day
            meal("2026-10-10", 1600, 100),
            meal("2026-09-20", 5000, 10), // outside the window
        )
        val a = CheckInCalculator.nutritionAdherence(meals, "2026-10-10")
        assertEquals(2, a.loggedDays)
        assertEquals(1800, a.avgCalories)
        assertEquals(110, a.avgProteinG)
    }

    @Test
    fun detectsStalledLifts() {
        val stalled = listOf(
            bench("2026-09-20", 1, 80.0, 5),
            bench("2026-09-25", 2, 80.0, 5),
            bench("2026-10-01", 3, 80.0, 4),
            bench("2026-10-08", 4, 77.5, 5),
        )
        assertTrue(CheckInCalculator.exerciseTrends(stalled, "2026-10-10").single().stalled)

        val improving = stalled.dropLast(1) + bench("2026-10-08", 4, 82.5, 5)
        assertFalse(CheckInCalculator.exerciseTrends(improving, "2026-10-10").single().stalled)
    }

    @Test
    fun dueAfterAWeekOrWhenFirstDataArrives() {
        val past = WeeklyCheckIn("1", "2026-10-01", 0, result())
        assertFalse(CheckInCalculator.isDue(listOf(past), "2026-10-07", emptyList(), emptyList(), emptyList()))
        assertTrue(CheckInCalculator.isDue(listOf(past), "2026-10-08", emptyList(), emptyList(), emptyList()))
        assertFalse(CheckInCalculator.isDue(emptyList(), "2026-10-08", emptyList(), emptyList(), emptyList()))
        assertTrue(CheckInCalculator.isDue(emptyList(), "2026-10-08", listOf(bench("2026-10-07", 1, 60.0, 8)), emptyList(), emptyList()))
    }

    @Test
    fun adjustmentsAreBoundedPerStepAndInTotal() {
        assertEquals(TargetAdjustment(300, 30), CheckInCalculator.boundedAdjustment(TargetAdjustment(), 900, 80))
        assertEquals(TargetAdjustment(-1000, -60), CheckInCalculator.boundedAdjustment(TargetAdjustment(-900, -50), -300, -30))
    }

    @Test
    fun targetsNeverDropBelowMinimum() {
        val small = profile.copy(sex = Sex.FEMALE, weightKg = 50.0, heightCm = 155, activityLevel = ActivityLevel.SEDENTARY)
        val t = MacroCalculator.targets(small, TargetAdjustment(calories = -1000))
        assertEquals(1200, t.calories)
    }

    @Test
    fun appliesMatchingTrainingChangesOnly() {
        val changes = listOf(
            TrainingChange("day 1", "bench press", "Incline Dumbbell Press", 4, "8-10", 120, "Bench stalled"),
            TrainingChange("Day 2", "Squat", "Squat", 4, "4", 0, "More practice"), // rest 0 -> keep old
            TrainingChange("Day 3", "Deadlift", "Deadlift", 3, "5", 180, "No such day"),
        )
        val (updated, applied) = CheckInCalculator.applyTrainingChanges(plan, changes)
        assertEquals(2, applied)
        val press = updated.days[0].exercises[0]
        assertEquals("Incline Dumbbell Press", press.name)
        assertEquals(4, press.sets)
        assertEquals("Row", updated.days[0].exercises[1].name)
        val squat = updated.days[1].exercises[0]
        assertEquals(4, squat.sets)
        assertEquals(180, squat.restSeconds)
    }

    @Test
    fun reportContainsTheKeyFacts() {
        val report = CheckInCalculator.buildReport(
            profile = profile,
            targets = MacroCalculator.targets(profile),
            adjustment = TargetAdjustment(-150, 0),
            meals = emptyList(),
            logs = listOf(bench("2026-10-08", 1, 80.0, 5)),
            weights = listOf(WeightEntry("2026-10-01", 80.0), WeightEntry("2026-10-08", 79.3)),
            plan = plan,
            previous = null,
            today = "2026-10-10",
        )
        assertTrue("-0.7 kg/week" in report, report)
        assertTrue("No meals logged." in report)
        assertTrue("Workouts in the last 7 days: 1 (planned: 3 per week)" in report)
        assertTrue("-150 kcal" in report)
        assertTrue("Day 1 (Upper): Bench Press 3x6-8" in report)
        assertTrue("Minimum allowed intake: 1500 kcal" in report)
    }

    // ---- ViewModel flow with a fake AI ----

    private class FakeAi(private val result: CheckInResult) : AiService {
        var lastReport: String? = null
        override suspend fun weeklyCheckIn(report: String): CheckInResult { lastReport = report; return result }
        override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String) = error("unused")
        override suspend fun analyzeMealDescription(description: String) = error("unused")
        override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String) = error("unused")
        override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets) = error("unused")
        override suspend fun coachReply(context: String, history: List<ChatMessage>) = error("unused")
    }

    private fun result(calories: Int = 0, protein: Int = 0, changes: List<TrainingChange> = emptyList()) = CheckInResult(
        headline = "On track", summary = "Good week.", wins = listOf("3 workouts"), concerns = emptyList(),
        calorieChange = calories, proteinChange = protein, nutritionReason = "Loss is a bit fast.",
        trainingChanges = changes, focusForNextWeek = listOf("Log meals daily"),
    )

    @Test
    fun applyingACheckInUpdatesTargetsAndPlan() {
        val repo = AppRepository(InMemoryKeyValueStore()).apply {
            profile = this@CheckInTest.profile
            workoutPlan = plan
            // Pending check-in as if the AI had already answered. Dated today, so applying it
            // leaves no check-in due whatever day the test runs.
            history.saveCheckIn(
                WeeklyCheckIn(
                    "c1", todayKey(), 1,
                    result(
                        calories = 150,
                        changes = listOf(
                            TrainingChange("Day 1", "Bench Press", "Incline Bench", 3, "8", 120, ""),
                            TrainingChange("Day 2", "Squat", "Front Squat", 3, "6", 180, ""),
                        ),
                    ),
                )
            )
        }
        val vm = AppViewModel(repo, FakeAi(result()))
        val before = vm.state.value.targets!!.calories

        // Accept the nutrition change and only the first training change.
        vm.applyCheckIn("c1", applyNutrition = true, trainingChangeIndexes = setOf(0))

        val state = vm.state.value
        assertEquals(before + 150, state.targets!!.calories)
        assertEquals(TargetAdjustment(150, 0), repo.targetAdjustment)
        assertEquals("Incline Bench", repo.workoutPlan!!.days[0].exercises[0].name)
        assertEquals("Squat", repo.workoutPlan!!.days[1].exercises[0].name)
        val applied = repo.history.checkIns().single()
        assertEquals(CheckInStatus.APPLIED, applied.status)
        assertEquals(150, applied.appliedCalorieChange)
        assertEquals(1, applied.appliedTrainingChanges)
        assertNull(state.pendingCheckIn)
        assertFalse(state.checkInDue)
    }

    @Test
    fun dismissingKeepsEverythingAsIs() {
        val repo = AppRepository(InMemoryKeyValueStore()).apply {
            profile = this@CheckInTest.profile
            history.saveCheckIn(WeeklyCheckIn("c1", "2026-10-10", 1, result(calories = -200)))
        }
        val vm = AppViewModel(repo, FakeAi(result()))
        vm.dismissCheckIn("c1")
        assertEquals(CheckInStatus.DISMISSED, repo.history.checkIns().single().status)
        assertTrue(repo.targetAdjustment.isZero)
    }

    @Test
    fun resetClearsAdjustment() {
        val repo = AppRepository(InMemoryKeyValueStore()).apply {
            profile = this@CheckInTest.profile
            targetAdjustment = TargetAdjustment(-250, 20)
        }
        val vm = AppViewModel(repo, FakeAi(result()))
        vm.resetAdjustment()
        assertEquals(MacroCalculator.targets(profile), vm.state.value.targets)
        assertTrue(repo.targetAdjustment.isZero)
    }
}
