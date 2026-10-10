package com.fithall.app

import com.fithall.app.ai.AiErrorKind
import com.fithall.app.ai.AiException
import com.fithall.app.ai.AiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.data.PrivacyConsent
import com.fithall.app.data.PrivacyPolicy
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.Equipment
import com.fithall.app.model.Experience
import com.fithall.app.model.Goal
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.NutritionPlan
import com.fithall.app.model.Sex
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WorkoutPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CrashReportingTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class RecordingReporter : CrashReporter {
        val enabled = mutableListOf<Boolean>()
        val recorded = mutableListOf<Throwable>()
        override fun setEnabled(enabled: Boolean) { this.enabled += enabled }
        override fun recordNonFatal(error: Throwable) { recorded += error }
    }

    private class FailingAi(private val error: Exception) : AiService {
        override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String): WorkoutPlan = throw error
        override suspend fun coachReply(context: String, history: List<ChatMessage>): String = throw error
        override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String): MealAnalysis = error("unused")
        override suspend fun analyzeMealDescription(description: String): MealAnalysis = error("unused")
        override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets): NutritionPlan = error("unused")
        override suspend fun weeklyCheckIn(report: String): CheckInResult = error("unused")
    }

    private val profile = UserProfile("Sam", 30, Sex.MALE, 180, 80.0, ActivityLevel.MODERATE, Experience.BEGINNER, Goal.MAINTAIN, Equipment.FULL_GYM, 3, 60)

    @Test
    fun offUntilTheUserOptsIn() {
        val reporter = RecordingReporter()
        val repo = AppRepository(InMemoryKeyValueStore())
        val vm = AppViewModel(repo, FailingAi(IllegalStateException()), crashReporter = reporter)
        assertEquals(listOf(false), reporter.enabled, "nothing is sent before consent")

        vm.acceptConsent(crashReports = false)
        assertEquals(false, reporter.enabled.last())
        assertFalse(repo.consent!!.crashReports)

        vm.setCrashReports(true)
        assertEquals(true, reporter.enabled.last())
        assertTrue(repo.consent!!.crashReports)
        // Remembered across restarts.
        val restarted = RecordingReporter()
        AppViewModel(repo, FailingAi(IllegalStateException()), crashReporter = restarted)
        assertEquals(listOf(true), restarted.enabled)
    }

    @Test
    fun anOutdatedConsentTurnsReportingOff() {
        val repo = AppRepository(InMemoryKeyValueStore()).apply {
            consent = PrivacyConsent(PrivacyPolicy.VERSION - 1, 0, crashReports = true)
        }
        val reporter = RecordingReporter()
        val vm = AppViewModel(repo, FailingAi(IllegalStateException()), crashReporter = reporter)
        assertEquals(listOf(false), reporter.enabled, "the new policy has to be accepted first")
        assertTrue(vm.state.value.needsConsent)
    }

    @Test
    fun deletingDataTurnsReportingOff() {
        val reporter = RecordingReporter()
        val vm = AppViewModel(AppRepository(InMemoryKeyValueStore()), FailingAi(IllegalStateException()), crashReporter = reporter)
        vm.acceptConsent(crashReports = true)
        vm.deleteAllData()
        assertEquals(false, reporter.enabled.last())
    }

    @Test
    fun onlyUnexpectedErrorsAreReported() {
        val bug = IllegalStateException("unexpected")
        val reporter = RecordingReporter()
        val repo = AppRepository(InMemoryKeyValueStore()).apply { profile = this@CrashReportingTest.profile }
        AppViewModel(repo, FailingAi(bug), crashReporter = reporter).generateWorkoutPlan()
        assertEquals(listOf<Throwable>(bug), reporter.recorded)

        val expected = RecordingReporter()
        AppViewModel(repo, FailingAi(AiException("offline", AiErrorKind.NETWORK)), crashReporter = expected).generateWorkoutPlan()
        assertTrue(expected.recorded.isEmpty(), "network errors, limits and refusals aren't bugs")
    }
}
