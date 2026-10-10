package com.fithall.app

import com.fithall.app.ai.UnavailableAiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.Equipment
import com.fithall.app.model.Exercise
import com.fithall.app.model.Experience
import com.fithall.app.model.Goal
import com.fithall.app.model.Sex
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WorkoutDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutSessionTest {

    private val day = WorkoutDay(
        "Day 1", "Full body", 45, "5 min bike",
        listOf(Exercise("Goblet Squat", 3, "10", 90, ""), Exercise("Push-up", 2, "12", 60, "")),
    )

    private fun viewModel(repo: AppRepository = AppRepository(InMemoryKeyValueStore())) =
        AppViewModel(repo, UnavailableAiService("offline"))

    @Test
    fun finishingKeepsOnlyCompletedSetsAndPersists() {
        val repo = AppRepository(InMemoryKeyValueStore())
        val vm = viewModel(repo)
        vm.startWorkout(day)
        vm.updateSet(0, 0) { it.copy(weightKg = 20.0, completed = true) }
        vm.updateSet(0, 1) { it.copy(weightKg = 20.0, reps = 8, completed = true) }

        assertNotNull(repo.activeWorkout, "session should be saved while in progress")
        assertTrue(vm.finishWorkout("felt good"))

        val saved = repo.history.workouts().single()
        assertEquals(listOf("Goblet Squat"), saved.exercises.map { it.name })
        assertEquals(listOf(10, 8), saved.exercises.single().sets.map { it.reps })
        assertEquals(360.0, saved.volumeKg)
        assertEquals("felt good", saved.notes)
        assertNull(repo.activeWorkout)
        assertNull(vm.state.value.activeWorkout)
    }

    @Test
    fun cannotFinishWithoutCompletedSets() {
        val vm = viewModel()
        vm.startWorkout(day)
        assertFalse(vm.finishWorkout(""))
        assertNotNull(vm.state.value.activeWorkout)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun activeSessionSurvivesRestart() {
        val repo = AppRepository(InMemoryKeyValueStore())
        viewModel(repo).apply {
            startWorkout(day)
            updateSet(1, 0) { it.copy(completed = true) }
        }
        val restored = viewModel(repo).state.value.activeWorkout
        assertNotNull(restored)
        assertTrue(restored.exercises[1].sets[0].completed)
    }

    @Test
    fun addAndRemoveSetsAndExercises() {
        val vm = viewModel()
        vm.startEmptyWorkout()
        vm.addExercise("Deadlift")
        vm.addSet(0)
        assertEquals(4, vm.state.value.activeWorkout!!.exercises[0].sets.size)
        vm.removeSet(0, 0)
        assertEquals(3, vm.state.value.activeWorkout!!.exercises[0].sets.size)
        vm.removeExercise(0)
        assertTrue(vm.state.value.activeWorkout!!.exercises.isEmpty())
    }

    @Test
    fun loggingWeightUpdatesProfileAndKeepsOneEntryPerDay() {
        val repo = AppRepository(InMemoryKeyValueStore())
        repo.profile = UserProfile(
            "A", 30, Sex.FEMALE, 165, 70.0, ActivityLevel.LIGHT, Experience.BEGINNER,
            Goal.LOSE_FAT, Equipment.BODYWEIGHT, 3, 30,
        )
        val vm = viewModel(repo)
        vm.logWeight(69.5)
        vm.logWeight(69.2)
        assertEquals(listOf(69.2), repo.history.weights().map { it.weightKg })
        assertEquals(69.2, repo.profile!!.weightKg)
        assertEquals(69.2, vm.state.value.profile!!.weightKg)
    }
}
