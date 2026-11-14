package com.fithall.app

import com.fithall.app.ai.UnavailableAiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.Equipment
import com.fithall.app.model.Exercise
import com.fithall.app.model.Experience
import com.fithall.app.model.FoodItem
import com.fithall.app.model.Goal
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.MealEntry
import com.fithall.app.model.Sex
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WorkoutDay
import com.fithall.app.platform.todayKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Removing things: a meal, a weigh-in, a workout in progress. Each must go from the screen and from storage. */
class EditingHistoryTest {

    private val repo = AppRepository(InMemoryKeyValueStore())
    private fun viewModel() = AppViewModel(repo, UnavailableAiService("offline"))

    private fun meal(id: String, kcal: Int) =
        MealEntry(id, 0L, todayKey(), MealAnalysis("Meal $id", listOf(FoodItem("Food", "1 bowl", kcal, 10, 20, 5)), "high", ""))

    @Test
    fun deletingAMealRemovesItAndItsCalories() {
        repo.history.saveMeal(meal("m1", 400))
        repo.history.saveMeal(meal("m2", 250))
        val vm = viewModel()
        assertEquals(650, vm.state.value.todaysTotals.calories)

        vm.deleteMeal("m1")

        assertEquals(listOf("m2"), vm.state.value.todaysMeals.map { it.id })
        assertEquals(250, vm.state.value.todaysTotals.calories)
        assertEquals(listOf("m2"), repo.history.meals().map { it.id })
    }

    @Test
    fun aNewWeighInReplacesTodaysAndUpdatesTheProfile() {
        repo.profile = UserProfile(
            "Ana", 28, Sex.FEMALE, 168, 62.0, ActivityLevel.MODERATE, Experience.BEGINNER,
            Goal.LOSE_FAT, Equipment.FULL_GYM, 3, 45,
        )
        val vm = viewModel()
        vm.logWeight(61.5)
        vm.logWeight(61.2)

        assertEquals(listOf(61.2), vm.state.value.weights.map { it.weightKg })
        assertEquals(61.2, vm.state.value.profile?.weightKg)
        assertEquals(61.2, repo.profile?.weightKg)
    }

    @Test
    fun deletingAWeighInRemovesItEverywhere() {
        val vm = viewModel()
        vm.logWeight(80.0)
        vm.deleteWeight(todayKey())

        assertTrue(vm.state.value.weights.isEmpty())
        assertTrue(repo.history.weights().isEmpty())
    }

    @Test
    fun discardingAWorkoutSavesNothing() {
        val vm = viewModel()
        vm.startWorkout(WorkoutDay("Day 1", "Full body", 45, "", listOf(Exercise("Squat", 3, "8", 90, ""))))
        vm.updateSet(0, 0) { it.copy(weightKg = 60.0, reps = 8, completed = true) }

        vm.discardWorkout()

        assertNull(vm.state.value.activeWorkout)
        assertNull(repo.activeWorkout)
        assertTrue(repo.history.workouts().isEmpty())
        // And it doesn't come back after a restart.
        assertNull(viewModel().state.value.activeWorkout)
    }
}
