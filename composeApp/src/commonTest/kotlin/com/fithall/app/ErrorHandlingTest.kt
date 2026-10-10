package com.fithall.app

import com.fithall.app.ai.AiErrorKind
import com.fithall.app.ai.AiException
import com.fithall.app.ai.AiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.ChatRole
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.Equipment
import com.fithall.app.model.Experience
import com.fithall.app.model.Goal
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.NutritionPlan
import com.fithall.app.model.Sex
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WorkoutLog
import com.fithall.app.model.WorkoutPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ErrorHandlingTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val profile = UserProfile("Sam", 30, Sex.MALE, 180, 80.0, ActivityLevel.MODERATE, Experience.BEGINNER, Goal.MAINTAIN, Equipment.FULL_GYM, 3, 60)

    private class FakeConnectivity(online: Boolean) : Connectivity {
        override val isOnline = MutableStateFlow(online)
    }

    /** Fails with [failures] in order, then succeeds. Counts calls. */
    private class FlakyAi(vararg failures: AiException) : AiService {
        private val pending = failures.toMutableList()
        var calls = 0
        private fun <T> next(value: T): T {
            calls++
            if (pending.isNotEmpty()) throw pending.removeAt(0)
            return value
        }
        override suspend fun coachReply(context: String, history: List<ChatMessage>) = next("Here's an idea.")
        override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String) =
            next(WorkoutPlan("Plan", "", 4, emptyList(), "", ""))
        override suspend fun weeklyCheckIn(report: String) =
            next(CheckInResult("h", "s", emptyList(), emptyList(), 0, 0, "", emptyList(), emptyList()))
        override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String): MealAnalysis = next(error("unused"))
        override suspend fun analyzeMealDescription(description: String): MealAnalysis = next(MealAnalysis("m", emptyList(), "low", ""))
        override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets): NutritionPlan = error("unused")
    }

    private fun repo() = AppRepository(InMemoryKeyValueStore()).apply {
        profile = this@ErrorHandlingTest.profile
        history.saveWorkout(WorkoutLog("w", "2026-10-01", 1, title = "Day 1", exercises = emptyList()))
    }

    // ---- Messages ----

    @Test
    fun messagesAreSafeAndRetryOnlyWhenItCanHelp() {
        val offline = ErrorMessages.forFailure(AiException("anything"), online = false)
        assertEquals(ErrorMessages.OFFLINE, offline.message)
        assertTrue(offline.canRetry)

        assertTrue(ErrorMessages.forFailure(AiException("x", AiErrorKind.NETWORK), true).canRetry)
        assertTrue(ErrorMessages.forFailure(AiException("Busy", AiErrorKind.RATE_LIMITED), true).canRetry)

        val limit = ErrorMessages.forFailure(AiException("You've used today's AI allowance.", AiErrorKind.DAILY_LIMIT), true)
        assertEquals("You've used today's AI allowance.", limit.message)
        assertFalse(limit.canRetry)
        assertFalse(ErrorMessages.forFailure(AiException("Declined", AiErrorKind.REFUSED), true).canRetry)

        val config = ErrorMessages.forFailure(AiException("ANTHROPIC_API_KEY invalid", AiErrorKind.CONFIGURATION), true)
        assertFalse("ANTHROPIC" in config.message, "server configuration details are never shown")

        val crash = ErrorMessages.forFailure(IllegalStateException("java.lang.NullPointerException at Foo.kt:12"), true)
        assertEquals("Something went wrong. Please try again.", crash.message)
    }

    // ---- Offline ----

    @Test
    fun offlineRequestsFailImmediatelyWithoutCallingTheServer() {
        val ai = FlakyAi()
        val vm = AppViewModel(repo(), ai, connectivity = FakeConnectivity(online = false))

        vm.generateWorkoutPlan()
        vm.analyzeDescription("eggs")

        assertEquals(0, ai.calls)
        val failure = vm.state.value.workoutPlanFailure!!
        assertEquals(ErrorMessages.OFFLINE, failure.message)
        assertTrue(failure.canRetry)
        assertFalse(vm.state.value.generatingWorkout)
        assertEquals(ErrorMessages.OFFLINE, assertIs<ScanState.Failed>(vm.state.value.scan).message, "the description is kept for retrying")
    }

    @Test
    fun retryingAPlanAfterReconnectingWorks() {
        val connectivity = FakeConnectivity(online = false)
        val ai = FlakyAi()
        val vm = AppViewModel(repo(), ai, connectivity = connectivity)
        vm.generateWorkoutPlan()
        assertTrue(vm.state.value.workoutPlanFailure != null)

        connectivity.isOnline.value = true
        vm.generateWorkoutPlan()
        assertNull(vm.state.value.workoutPlanFailure, "a new attempt clears the old error")
        assertEquals("Plan", vm.state.value.workoutPlan?.title)
    }

    // ---- Coach ----

    @Test
    fun failedCoachReplyKeepsTheMessageAndRetriesWithoutDuplicating() {
        val ai = FlakyAi(AiException("Couldn't reach FitHall's servers.", AiErrorKind.NETWORK))
        val repo = repo()
        val vm = AppViewModel(repo, ai)

        vm.sendCoachMessage("What should I eat tonight?")
        assertEquals(listOf(ChatRole.USER), vm.state.value.chat.map { it.role })
        assertTrue(vm.state.value.coachFailure!!.canRetry)

        vm.retryCoachReply()
        assertNull(vm.state.value.coachFailure)
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), vm.state.value.chat.map { it.role })
        assertEquals(2, repo.history.chat().size, "the question is stored once")
        assertEquals(2, ai.calls)
    }

    @Test
    fun retryDoesNothingWhenTheLastMessageWasAnswered() {
        val ai = FlakyAi()
        val vm = AppViewModel(repo(), ai)
        vm.sendCoachMessage("hi")
        vm.retryCoachReply()
        assertEquals(1, ai.calls)
    }

    // ---- Check-in ----

    @Test
    fun dailyLimitOnACheckInIsNotRetryable() {
        val ai = FlakyAi(AiException("You've used today's AI allowance. It resets at midnight UTC.", AiErrorKind.DAILY_LIMIT))
        val vm = AppViewModel(repo(), ai)
        vm.runCheckIn()
        val failure = vm.state.value.checkInFailure!!
        assertFalse(failure.canRetry)
        assertFalse(vm.state.value.runningCheckIn)

        vm.dismissFailures()
        assertNull(vm.state.value.checkInFailure)
    }
}
