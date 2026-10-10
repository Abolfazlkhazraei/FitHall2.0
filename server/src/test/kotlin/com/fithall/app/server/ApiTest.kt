package com.fithall.app.server

import com.fithall.app.ai.AiErrorKind
import com.fithall.app.ai.AiException
import com.fithall.app.ai.AiService
import com.fithall.app.api.Api
import com.fithall.app.api.ApiError
import com.fithall.app.api.CoachRequest
import com.fithall.app.api.CoachResponse
import com.fithall.app.api.ErrorCodes
import com.fithall.app.api.MealPhotoRequest
import com.fithall.app.api.MealTextRequest
import com.fithall.app.api.RegisterResponse
import com.fithall.app.api.WorkoutPlanRequest
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.ChatRole
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.Equipment
import com.fithall.app.model.Experience
import com.fithall.app.model.FoodItem
import com.fithall.app.model.Goal
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.NutritionPlan
import com.fithall.app.model.Sex
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WorkoutPlan
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApiTest {

    private val meal = MealAnalysis("Toast", listOf(FoodItem("Toast", "1 slice", 80, 3, 15, 1)), "high", "")

    /** Records calls; behaviour per call is configurable. */
    private class FakeAi(val meal: MealAnalysis) : AiService {
        var calls = 0
        var failWith: AiException? = null
        private fun <T> answer(value: T): T { calls++; failWith?.let { throw it }; return value }
        override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String) = answer(meal)
        override suspend fun analyzeMealDescription(description: String) = answer(meal.copy(mealName = description))
        override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String) =
            answer(WorkoutPlan("Plan for ${profile.name}", "", 4, emptyList(), "", ""))
        override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets) = answer(NutritionPlan("", emptyList(), emptyList()))
        override suspend fun coachReply(context: String, history: List<ChatMessage>) = answer("Eat more protein.")
        override suspend fun weeklyCheckIn(report: String): CheckInResult = error("unused")
    }

    private val config = ServerConfig(
        anthropicApiKey = "unused",
        tokenSecret = "s".repeat(32),
        limits = UsageLimits(deviceUnitsPerDay = 6, deviceRequestsPerMinute = 100, globalUnitsPerDay = 1000, registrationsPerIpPerHour = 3),
    )

    private val profile = UserProfile(
        "Ana", 28, Sex.FEMALE, 168, 62.0, ActivityLevel.MODERATE, Experience.BEGINNER,
        Goal.BUILD_MUSCLE, Equipment.HOME_DUMBBELLS, 3, 45,
    )

    private fun api(ai: FakeAi = FakeAi(meal), block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { fitHallModule(config, ai) }
        val client = createClient { install(ContentNegotiation) { json() } }
        block(client)
    }

    private suspend fun HttpClient.register(): String =
        post(Api.REGISTER).body<RegisterResponse>().token

    private suspend inline fun <reified T> HttpClient.postJson(path: String, token: String?, body: T): HttpResponse =
        post(path) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private val jpegBase64 = Base64.getEncoder().encodeToString(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3))

    @Test
    fun healthCheck() = api { client ->
        assertEquals("ok", client.get(Api.HEALTH).bodyAsText())
    }

    @Test
    fun aiEndpointsNeedAValidToken() = api { client ->
        val noToken = client.postJson(Api.MEAL_TEXT, null, MealTextRequest("eggs"))
        assertEquals(HttpStatusCode.Unauthorized, noToken.status)
        assertEquals(ErrorCodes.UNAUTHORIZED, noToken.body<ApiError>().code)

        val forged = client.postJson(Api.MEAL_TEXT, "v1.abc.def", MealTextRequest("eggs"))
        assertEquals(HttpStatusCode.Unauthorized, forged.status)
    }

    @Test
    fun registeredDeviceCanAnalyzeAMealPhoto() = api { client ->
        val token = client.register()
        val response = client.postJson(Api.MEAL_PHOTO, token, MealPhotoRequest(jpegBase64, "no butter"))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(meal, response.body<MealAnalysis>())
    }

    @Test
    fun allEndpointsRoundTrip() = api { client ->
        val token = client.register()
        assertEquals("eggs", client.postJson(Api.MEAL_TEXT, token, MealTextRequest(" eggs ")).body<MealAnalysis>().mealName)
        assertEquals("Plan for Ana", client.postJson(Api.WORKOUT_PLAN, token, WorkoutPlanRequest(profile)).body<WorkoutPlan>().title)
        val coach = client.postJson(Api.COACH_REPLY, token, CoachRequest("ctx", listOf(ChatMessage(ChatRole.USER, "hi"))))
        assertEquals("Eat more protein.", coach.body<CoachResponse>().reply)
    }

    @Test
    fun invalidInputIsRejectedWithoutCallingTheAi() = api(FakeAi(meal)) { client ->
        val token = client.register()
        val notJpeg = Base64.getEncoder().encodeToString("hello".toByteArray())
        assertEquals(HttpStatusCode.BadRequest, client.postJson(Api.MEAL_PHOTO, token, MealPhotoRequest(notJpeg)).status)
        assertEquals(HttpStatusCode.BadRequest, client.postJson(Api.MEAL_TEXT, token, MealTextRequest("  ")).status)
        assertEquals(HttpStatusCode.BadRequest, client.postJson(Api.WORKOUT_PLAN, token, WorkoutPlanRequest(profile.copy(age = 5))).status)
        val endsWithAssistant = CoachRequest("", listOf(ChatMessage(ChatRole.USER, "hi"), ChatMessage(ChatRole.ASSISTANT, "yo")))
        assertEquals(HttpStatusCode.BadRequest, client.postJson(Api.COACH_REPLY, token, endsWithAssistant).status)
        val malformed = client.post(Api.MEAL_TEXT) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"nope": 1}""")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    @Test
    fun dailyAllowanceIsEnforcedPerDevice() = api { client ->
        val token = client.register()
        // MEAL_PHOTO costs 2 units; the allowance is 6.
        repeat(3) { assertEquals(HttpStatusCode.OK, client.postJson(Api.MEAL_PHOTO, token, MealPhotoRequest(jpegBase64)).status) }
        val limited = client.postJson(Api.MEAL_PHOTO, token, MealPhotoRequest(jpegBase64))
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals(ErrorCodes.DAILY_LIMIT, limited.body<ApiError>().code)
        assertNotNull(limited.headers[HttpHeaders.RetryAfter])

        // Another device has its own allowance.
        assertEquals(HttpStatusCode.OK, client.postJson(Api.MEAL_PHOTO, client.register(), MealPhotoRequest(jpegBase64)).status)
    }

    @Test
    fun aiFailuresMapToStatusCodesAndOutagesAreRefunded() {
        val ai = FakeAi(meal)
        api(ai) { client ->
            val token = client.register()

            ai.failWith = AiException("declined", AiErrorKind.REFUSED)
            val refused = client.postJson(Api.MEAL_TEXT, token, MealTextRequest("x"))
            assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
            assertEquals(ErrorCodes.REFUSED, refused.body<ApiError>().code)

            ai.failWith = AiException("secret key detail", AiErrorKind.CONFIGURATION)
            val misconfigured = client.postJson(Api.MEAL_TEXT, token, MealTextRequest("x"))
            assertEquals(HttpStatusCode.ServiceUnavailable, misconfigured.status)
            assertEquals("The AI service isn't available right now.", misconfigured.body<ApiError>().message)

            // Outages and config errors are refunded; only the refused call (1 unit) counted so far.
            // So after 10 failed photo calls, two photos (4 units) still fit in the 6-unit allowance.
            ai.failWith = AiException("down", AiErrorKind.UNAVAILABLE)
            repeat(10) { assertEquals(HttpStatusCode.BadGateway, client.postJson(Api.MEAL_PHOTO, token, MealPhotoRequest(jpegBase64)).status) }
            ai.failWith = null
            repeat(2) { assertEquals(HttpStatusCode.OK, client.postJson(Api.MEAL_PHOTO, token, MealPhotoRequest(jpegBase64)).status) }
        }
    }

    @Test
    fun registrationIsRateLimitedPerIp() = api { client ->
        repeat(3) { assertEquals(HttpStatusCode.OK, client.post(Api.REGISTER).status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.post(Api.REGISTER).status)
    }
}
