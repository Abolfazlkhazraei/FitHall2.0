package com.fithall.app

import com.fithall.app.ai.AiPrompts
import com.fithall.app.ai.UnavailableAiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.data.PrivacyConsent
import com.fithall.app.data.PrivacyPolicy
import com.fithall.app.domain.Amount
import com.fithall.app.domain.CheckInReminder
import com.fithall.app.domain.Portions
import com.fithall.app.domain.ReminderScheduler
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.CheckInStatus
import com.fithall.app.model.Equipment
import com.fithall.app.model.Experience
import com.fithall.app.model.FoodItem
import com.fithall.app.model.Goal
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.MealEntry
import com.fithall.app.model.Sex
import com.fithall.app.model.UnitSystem
import com.fithall.app.model.Units
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WeeklyCheckIn
import com.fithall.app.model.WorkoutLog
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortionsTest {

    private val chicken = FoodItem("Chicken breast", "150 g", 248, 46, 0, 5)

    @Test
    fun parsesCommonPortionFormats() {
        assertEquals(Amount(150.0, "g"), Portions.parse("150 g"))
        assertEquals(Amount(1.5, "cups"), Portions.parse("1.5 cups"))
        assertEquals(Amount(1.5, "tbsp"), Portions.parse("1,5 tbsp"))
        assertEquals(Amount(0.5, "cup"), Portions.parse("1/2 cup"))
        assertEquals(Amount(1.5, "slices"), Portions.parse("1 1/2 slices"))
        assertEquals(Amount(200.0, "ml"), Portions.parse("~200 ml"))
        assertEquals(Amount(2.0, "eggs"), Portions.parse("approx. 2 eggs"))
        assertEquals(Amount(1.0, "medium (approx. 120 g)"), Portions.parse("1 medium (approx. 120 g)"))
        assertNull(Portions.parse("a handful"))
    }

    @Test
    fun scalingUpdatesMacrosAndPortion() {
        val factor = Portions.factorFor(chicken, 200.0)!!
        val bigger = Portions.scale(chicken, factor)
        assertEquals("200 g", bigger.portion)
        assertEquals(331, bigger.calories)
        assertEquals(61, bigger.proteinG)
        assertEquals(chicken, Portions.scale(chicken, 1.0))
    }

    @Test
    fun portionsWithoutANumberUseAMultiplier() {
        val handful = FoodItem("Almonds", "a handful", 160, 6, 6, 14)
        val doubled = Portions.scale(handful, 2.0)
        assertEquals("2 × a handful", doubled.portion)
        assertEquals(320, doubled.calories)
    }

    @Test
    fun scalingFromTheOriginalDoesNotDrift() {
        // Edit 150 g -> 37 g -> 150 g: going back must give exactly the original numbers.
        val small = Portions.scale(chicken, Portions.factorFor(chicken, 37.0)!!)
        assertEquals("37 g", small.portion)
        val back = Portions.scale(chicken, Portions.factorFor(chicken, 150.0)!!)
        assertEquals(chicken, back)
    }

    @Test
    fun editingALoggedMealPersists() {
        val repo = AppRepository(InMemoryKeyValueStore())
        val meal = MealEntry("m1", 1, "2026-10-10", MealAnalysis("Lunch", listOf(chicken), "high", ""))
        repo.history.saveMeal(meal)
        val vm = AppViewModel(repo, UnavailableAiService("offline"))
        val edited = meal.copy(analysis = meal.analysis.copy(items = listOf(Portions.scale(chicken, 2.0))))

        vm.updateMeal(edited)

        assertEquals(496, repo.history.meals().single().analysis.totals.calories)
        assertEquals(edited, vm.state.value.meals.single())
    }
}

class UnitsTest {

    @Test
    fun convertsHeightAndWeight() {
        assertEquals(5 to 11, Units.cmToFeetInches(180))
        assertEquals(180, Units.feetInchesToCm(5, 11))
        assertEquals(6 to 0, Units.cmToFeetInches(183))
        val lb = Units.kgToDisplay(80.0, UnitSystem.IMPERIAL)
        assertTrue(abs(lb - 176.37) < 0.01, "$lb")
        assertTrue(abs(Units.displayToKg(lb, UnitSystem.IMPERIAL) - 80.0) < 1e-9)
        assertEquals(80.0, Units.kgToDisplay(80.0, UnitSystem.METRIC))
    }

    @Test
    fun profilesSavedBeforeUnitsExistedLoadAsMetric() {
        val old = """{"name":"Sam","age":30,"sex":"MALE","heightCm":180,"weightKg":80.0,"activityLevel":"MODERATE","experience":"BEGINNER","goal":"MAINTAIN","equipment":"FULL_GYM","trainingDaysPerWeek":3,"sessionMinutes":60}"""
        assertEquals(UnitSystem.METRIC, Json.decodeFromString(UserProfile.serializer(), old).units)
    }

    @Test
    fun theAiIsToldToUseImperialUnits() {
        val p = UserProfile("Sam", 30, Sex.MALE, 180, 80.0, ActivityLevel.MODERATE, Experience.BEGINNER, Goal.MAINTAIN, Equipment.FULL_GYM, 3, 60)
        assertTrue("pounds" !in AiPrompts.profileSummary(p))
        assertTrue("Preferred units: pounds, feet and inches" in AiPrompts.profileSummary(p.copy(units = UnitSystem.IMPERIAL)))
    }
}

class CheckInReminderTest {

    private val profile = UserProfile("Sam", 30, Sex.MALE, 180, 80.0, ActivityLevel.MODERATE, Experience.BEGINNER, Goal.MAINTAIN, Equipment.FULL_GYM, 3, 60)
    private val result = CheckInResult("h", "s", emptyList(), emptyList(), 0, 0, "", emptyList(), emptyList())

    private fun repo(remindersOn: Boolean = true) = AppRepository(InMemoryKeyValueStore()).apply {
        profile = this@CheckInReminderTest.profile
        consent = PrivacyConsent(PrivacyPolicy.VERSION, 0)
        checkInReminders = remindersOn
        history.saveWorkout(WorkoutLog("w", "2026-10-01", 1, title = "Day 1", exercises = emptyList()))
    }

    @Test
    fun remindsOncePerCheckInPeriod() {
        val repo = repo()
        // First check-in: due as soon as there's data.
        assertEquals("first", CheckInReminder.reminderToSend(repo, "2026-10-02"))
        repo.lastReminderKey = "first"
        assertNull(CheckInReminder.reminderToSend(repo, "2026-10-03"), "already reminded for this period")

        // After a check-in, the next reminder comes 7 days later, keyed by that check-in.
        repo.history.saveCheckIn(WeeklyCheckIn("c1", "2026-10-03", 100, result, CheckInStatus.APPLIED))
        assertNull(CheckInReminder.reminderToSend(repo, "2026-10-09"))
        assertEquals("c1", CheckInReminder.reminderToSend(repo, "2026-10-10"))
    }

    @Test
    fun noReminderWhenOffWithoutConsentOrWithAPendingCheckIn() {
        assertNull(CheckInReminder.reminderToSend(repo(remindersOn = false), "2026-10-02"))
        assertNull(CheckInReminder.reminderToSend(repo().apply { consent = null }, "2026-10-02"))
        val pending = repo().apply { history.saveCheckIn(WeeklyCheckIn("c1", "2026-09-20", 1, result)) }
        assertNull(CheckInReminder.reminderToSend(pending, "2026-10-02"), "the results are already waiting in the app")
    }

    private class RecordingScheduler : ReminderScheduler {
        val calls = mutableListOf<String>()
        override fun schedule() { calls += "schedule" }
        override fun cancel() { calls += "cancel" }
    }

    @Test
    fun settingSchedulesAndDeletingDataCancels() {
        val scheduler = RecordingScheduler()
        val repo = AppRepository(InMemoryKeyValueStore())
        val vm = AppViewModel(repo, UnavailableAiService("offline"), reminders = scheduler)
        assertTrue(scheduler.calls.isEmpty(), "nothing scheduled until the user opts in")

        vm.setCheckInReminders(true)
        assertTrue(repo.checkInReminders)
        // A restart keeps the schedule going.
        AppViewModel(repo, UnavailableAiService("offline"), reminders = scheduler)
        assertEquals(listOf("schedule", "schedule"), scheduler.calls)

        vm.deleteAllData()
        assertEquals("cancel", scheduler.calls.last())
        assertTrue(!repo.checkInReminders)
    }
}
