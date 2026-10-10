package com.fithall.app.server

import com.fithall.app.ai.AiService
import com.fithall.app.api.Api
import com.fithall.app.api.MealTextRequest
import com.fithall.app.api.RegisterResponse
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.FoodItem
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.NutritionPlan
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WorkoutPlan
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CostTrackerTest {

    private var now = 1_760_054_400_000L // 2025-10-10T00:00:00Z
    private val tracker = CostTracker(UsageLimits(deviceUnitsPerDay = 40), clock = { now })

    private fun event(
        op: AiOperation?,
        device: String? = "d1",
        input: Long = 1_000_000,
        output: Long = 0,
        cacheRead: Long = 0,
        model: String = "claude-opus-5-5",
    ) = UsageEvent(op, device, model, input, output, cacheRead, 0, durationMillis = 10, stopReason = "end_turn")

    private fun close(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-9, "expected $expected, got $actual")

    @Test
    fun pricesTokensByModel() {
        val opus = ModelPricing.forModel("claude-opus-5-5")!!
        // 2,000 input ($4/M) + 1,000 output ($20/M) + 10,000 cache reads ($0.20/M) = 0.008 + 0.02 + 0.002
        close(0.030, ModelPricing.costUsd(event(AiOperation.MEAL_TEXT, input = 2_000, output = 1_000, cacheRead = 10_000), opus))
        // A suffixed id still matches its model; the longest prefix wins (opus-5-5, not opus-5).
        assertEquals(opus, ModelPricing.forModel("claude-opus-5-5-20261001"))
        assertEquals(5.0, ModelPricing.forModel("claude-opus-5")!!.input)
        assertNull(ModelPricing.forModel("some-other-model"))
    }

    @Test
    fun aggregatesPerOperationAndPerUnit() {
        tracker.record(event(AiOperation.MEAL_PHOTO, input = 500_000))                  // $2.00, 2 units
        tracker.record(event(AiOperation.MEAL_PHOTO, input = 250_000, device = "d2"))   // $1.00
        tracker.record(event(AiOperation.WORKOUT_PLAN, input = 0, output = 100_000))    // $2.00, 4 units

        val report = tracker.report()
        val today = report.days.single()
        assertEquals("2025-10-10", today.day)
        assertEquals(3, today.requests)
        close(5.0, today.costUsd)
        assertEquals(2, today.activeDevices)

        val photo = today.operations.single { it.operation == "MEAL_PHOTO" }
        assertEquals(2, photo.requests)
        assertEquals(750_000, photo.inputTokens)
        close(1.5, photo.avgCostUsd)
        close(0.75, photo.costPerUnitUsd)

        // $5 over 2*2 + 4 = 8 units, times the 40-unit allowance.
        close(0.625, report.avgCostPerUnitUsd!!)
        close(25.0, report.maxCostPerDevicePerDayUsd!!)

        val perDevice = assertNotNull(today.perDevice)
        close(1.0, perDevice.p50) // d2
        close(4.0, perDevice.max) // d1
    }

    @Test
    fun keepsDaysSeparateAndForgetsDevicesOvernight() {
        tracker.record(event(AiOperation.MEAL_TEXT))
        now += 86_400_000
        tracker.record(event(AiOperation.MEAL_TEXT, device = "d9"))
        val report = tracker.report()
        assertEquals(listOf("2025-10-11", "2025-10-10"), report.days.map { it.day })
        assertNotNull(report.days[0].perDevice)
        assertNull(report.days[1].perDevice, "per-device costs are only kept for the current day")
        assertEquals(1, report.days[0].activeDevices)
    }

    @Test
    fun dropsDaysOutsideTheWindow() {
        tracker.record(event(AiOperation.MEAL_TEXT))
        now += 31L * 86_400_000
        tracker.record(event(AiOperation.MEAL_TEXT))
        assertEquals(1, tracker.report().days.size)
    }

    @Test
    fun countsUnpricedModelsWithoutGuessingTheirCost() {
        tracker.record(event(AiOperation.COACH_REPLY, model = "claude-mystery-9"))
        val today = tracker.report().days.single()
        assertEquals(1, today.unpricedRequests)
        close(0.0, today.costUsd)
    }

    @Test
    fun reportContainsNoDeviceIds() {
        tracker.record(event(AiOperation.MEAL_TEXT, device = "secret-device-id"))
        val json = Json.encodeToString(UsageReport.serializer(), tracker.report())
        assertFalse("secret-device-id" in json)
    }
}

class UsageAttributionTest {

    /** Records the context each AI call ran in, like ClaudeAiService does. */
    private class RecordingAi(private val tracker: CostTracker) : AiService {
        override suspend fun analyzeMealDescription(description: String): MealAnalysis {
            val request = currentCoroutineContext()[AiRequestContext]
            tracker.record(UsageEvent(request?.operation, request?.deviceId, "claude-opus-5-5", 1_000, 500, durationMillis = 1, stopReason = "end_turn"))
            return MealAnalysis("m", listOf(FoodItem("f", "1", 100, 1, 1, 1)), "high", "")
        }
        override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String) = error("unused")
        override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String): WorkoutPlan = error("unused")
        override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets): NutritionPlan = error("unused")
        override suspend fun coachReply(context: String, history: List<ChatMessage>) = error("unused")
        override suspend fun weeklyCheckIn(report: String): CheckInResult = error("unused")
    }

    private val adminToken = "a".repeat(32)
    private val config = ServerConfig(anthropicApiKey = "unused", tokenSecret = "s".repeat(32), adminToken = adminToken)

    @Test
    fun callsAreAttributedAndReportedToTheAdminOnly() {
        val tracker = CostTracker(config.limits)
        testApplication {
            application { fitHallModule(config, RecordingAi(tracker), costs = tracker) }
            val client = createClient { install(ContentNegotiation) { json() } }
            val token = client.post(Api.REGISTER).body<RegisterResponse>().token
            repeat(2) {
                client.post(Api.MEAL_TEXT) {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(MealTextRequest("toast"))
                }
            }

            assertEquals(HttpStatusCode.Unauthorized, client.get(ADMIN_USAGE_PATH).status)
            assertEquals(HttpStatusCode.Unauthorized, client.get(ADMIN_USAGE_PATH) { bearerAuth(token) }.status, "device tokens aren't admin tokens")

            val report = client.get(ADMIN_USAGE_PATH) { bearerAuth(adminToken) }.body<UsageReport>()
            val today = report.days.single()
            assertEquals(1, today.activeDevices)
            assertEquals("MEAL_TEXT", today.operations.single().operation)
            assertEquals(2, today.operations.single().requests)
        }
    }

    @Test
    fun adminReportIsOffWithoutAToken() = testApplication {
        application { fitHallModule(config.copy(adminToken = null), RecordingAi(CostTracker(config.limits))) }
        val response = client.get(ADMIN_USAGE_PATH) { bearerAuth("anything") }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertFalse("days" in response.bodyAsText())
    }
}
