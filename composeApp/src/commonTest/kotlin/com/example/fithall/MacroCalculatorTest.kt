package com.example.fithall

import com.example.fithall.data.AppRepository
import com.example.fithall.data.InMemoryKeyValueStore
import com.example.fithall.domain.MacroCalculator
import com.example.fithall.model.ActivityLevel
import com.example.fithall.model.Equipment
import com.example.fithall.model.Experience
import com.example.fithall.model.FoodItem
import com.example.fithall.model.Goal
import com.example.fithall.model.MealAnalysis
import com.example.fithall.model.Sex
import com.example.fithall.model.UserProfile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacroCalculatorTest {

    private val profile = UserProfile(
        name = "Test",
        age = 30,
        sex = Sex.MALE,
        heightCm = 180,
        weightKg = 80.0,
        activityLevel = ActivityLevel.MODERATE,
        experience = Experience.BEGINNER,
        goal = Goal.MAINTAIN,
        equipment = Equipment.FULL_GYM,
        trainingDaysPerWeek = 3,
        sessionMinutes = 60,
    )

    @Test
    fun bmrUsesMifflinStJeor() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780.0, MacroCalculator.bmr(profile))
        assertEquals(1780.0 - 166, MacroCalculator.bmr(profile.copy(sex = Sex.FEMALE)))
    }

    @Test
    fun macrosAddUpToCalories() {
        Goal.entries.forEach { goal ->
            val t = MacroCalculator.targets(profile.copy(goal = goal))
            val fromMacros = t.proteinG * 4 + t.carbsG * 4 + t.fatG * 9
            assertTrue(abs(fromMacros - t.calories) <= 10, "$goal: $fromMacros vs ${t.calories}")
        }
    }

    @Test
    fun fatLossEatsLessThanMuscleGain() {
        val cut = MacroCalculator.targets(profile.copy(goal = Goal.LOSE_FAT))
        val bulk = MacroCalculator.targets(profile.copy(goal = Goal.BUILD_MUSCLE))
        assertTrue(cut.calories < bulk.calories)
        assertTrue(cut.proteinG >= bulk.proteinG)
    }

    @Test
    fun mealTotalsSumItems() {
        val meal = MealAnalysis(
            mealName = "Bowl",
            items = listOf(FoodItem("Rice", "1 cup", 200, 4, 45, 0), FoodItem("Chicken", "150 g", 250, 45, 0, 6)),
            confidence = "high",
            notes = "",
        )
        assertEquals(450, meal.totals.calories)
        assertEquals(49, meal.totals.proteinG)
    }

    @Test
    fun repositoryRoundTripsProfile() {
        val repo = AppRepository(InMemoryKeyValueStore())
        repo.profile = profile
        assertEquals(profile, repo.profile)
        repo.profile = null
        assertNull(repo.profile)
    }
}
