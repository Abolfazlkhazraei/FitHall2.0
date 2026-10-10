package com.fithall.app

import com.fithall.app.ai.UnavailableAiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.domain.CheckInCalculator
import com.fithall.app.domain.MacroCalculator
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.Equipment
import com.fithall.app.model.Experience
import com.fithall.app.model.Goal
import com.fithall.app.model.Safety
import com.fithall.app.model.Sex
import com.fithall.app.model.TargetAdjustment
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WeeklyCheckIn
import com.fithall.app.model.WeightEntry
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafetyTest {

    // 170 cm: BMI 18.5 is about 53.5 kg.
    private val healthy = UserProfile(
        "Lee", 25, Sex.FEMALE, 170, 65.0, ActivityLevel.LIGHT, Experience.BEGINNER,
        Goal.LOSE_FAT, Equipment.BODYWEIGHT, 3, 30,
    )
    private val underweight = healthy.copy(weightKg = 50.0) // BMI 17.3

    @Test
    fun bmi() {
        assertEquals(22.49, (Safety.bmi(healthy) * 100).roundToInt() / 100.0)
        assertTrue(Safety.isUnderweight(underweight))
    }

    @Test
    fun underweightUsersNeverGetADeficit() {
        assertEquals(Goal.MAINTAIN, MacroCalculator.effectiveGoal(underweight))
        val maintenance = MacroCalculator.tdee(underweight).roundToInt()
        assertEquals(maintenance, MacroCalculator.targets(underweight).calories)
        // Earlier check-in cuts don't apply either; increases still do.
        assertEquals(maintenance, MacroCalculator.targets(underweight, TargetAdjustment(calories = -300)).calories)
        assertEquals(maintenance + 200, MacroCalculator.targets(underweight, TargetAdjustment(calories = 200)).calories)
        assertNotNull(MacroCalculator.safetyNotice(underweight))
    }

    @Test
    fun healthyWeightLossIsUnchanged() {
        assertEquals(Goal.LOSE_FAT, MacroCalculator.effectiveGoal(healthy))
        assertTrue(MacroCalculator.targets(healthy).calories < MacroCalculator.tdee(healthy))
        assertNull(MacroCalculator.safetyNotice(healthy))
        assertNull(MacroCalculator.safetyNotice(underweight.copy(goal = Goal.BUILD_MUSCLE)))
    }

    @Test
    fun calorieCutsBlockedWhenUnderweightOrLosingTooFast() {
        assertNotNull(CheckInCalculator.calorieCutBlockedReason(underweight, emptyList(), "2026-10-10"))

        // 65 kg losing 1 kg/week is ~1.5% per week: too fast.
        val fast = listOf(WeightEntry("2026-09-26", 67.0), WeightEntry("2026-10-03", 66.0), WeightEntry("2026-10-10", 65.0))
        val reason = assertNotNull(CheckInCalculator.calorieCutBlockedReason(healthy, fast, "2026-10-10"))
        assertTrue("dropping" in reason, reason)

        // 0.4 kg/week is fine.
        val steady = listOf(WeightEntry("2026-09-26", 65.8), WeightEntry("2026-10-10", 65.0))
        assertNull(CheckInCalculator.calorieCutBlockedReason(healthy, steady, "2026-10-10"))
    }

    @Test
    fun boundedAdjustmentIgnoresCutsWhenBlocked() {
        assertEquals(TargetAdjustment(0, 0), CheckInCalculator.boundedAdjustment(TargetAdjustment(), -200, 0, allowCalorieCut = false))
        assertEquals(TargetAdjustment(150, 10), CheckInCalculator.boundedAdjustment(TargetAdjustment(), 150, 10, allowCalorieCut = false))
        assertEquals(TargetAdjustment(-200, 0), CheckInCalculator.boundedAdjustment(TargetAdjustment(), -200, 0))
    }

    @Test
    fun checkInReportStatesTheSafetyRule() {
        val report = CheckInCalculator.buildReport(
            profile = underweight, targets = MacroCalculator.targets(underweight), adjustment = TargetAdjustment(),
            meals = emptyList(), logs = emptyList(), weights = emptyList(), plan = null, previous = null, today = "2026-10-10",
        )
        assertTrue("SAFETY RULE: calories must not be reduced" in report, report)
        assertTrue("BMI: 17.3" in report, report)
    }

    @Test
    fun applyingACheckInCannotCutCaloriesForAnUnderweightUser() {
        val result = CheckInResult("h", "s", emptyList(), emptyList(), -250, 0, "Slow progress", emptyList(), emptyList())
        val repo = AppRepository(InMemoryKeyValueStore()).apply {
            profile = underweight
            history.saveCheckIn(WeeklyCheckIn("c1", "2026-10-10", 1, result))
        }
        val vm = AppViewModel(repo, UnavailableAiService("offline"))
        val before = vm.state.value.targets!!.calories
        vm.applyCheckIn("c1", applyNutrition = true, trainingChangeIndexes = emptySet())
        assertEquals(before, vm.state.value.targets!!.calories)
        assertTrue(repo.targetAdjustment.isZero)
    }
}
