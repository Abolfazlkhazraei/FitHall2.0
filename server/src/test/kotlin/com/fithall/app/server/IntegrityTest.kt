package com.fithall.app.server

import com.fithall.app.ai.AiService
import com.fithall.app.api.Api
import com.fithall.app.api.ApiError
import com.fithall.app.api.ChallengeResponse
import com.fithall.app.api.ErrorCodes
import com.fithall.app.api.MealTextRequest
import com.fithall.app.api.RegisterRequest
import com.fithall.app.api.RegisterResponse
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.FoodItem
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.NutritionPlan
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WorkoutPlan
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegrityRulesTest {

    private val now = 1_760_000_000_000L
    private val config = IntegrityConfig(mode = IntegrityMode.ENFORCE, packageName = "com.fithall.app")

    /** A verdict as Google's decodeIntegrityToken returns it for a good install. */
    private fun payload(
        nonce: String = "abc123",
        appVerdict: String = "PLAY_RECOGNIZED",
        device: String = """["MEETS_DEVICE_INTEGRITY"]""",
        licensing: String = "LICENSED",
        timestamp: Long = now - 5_000,
        packageName: String = "com.fithall.app",
    ) = Json { ignoreUnknownKeys = true }.decodeFromString(
        IntegrityPayload.serializer(),
        """
        {
          "requestDetails": {"requestPackageName": "$packageName", "nonce": "$nonce", "timestampMillis": "$timestamp"},
          "appIntegrity": {"appRecognitionVerdict": "$appVerdict", "packageName": "$packageName",
                           "certificateSha256Digest": ["6a6a1474b5cbbb2b1aa57e0bc3"], "versionCode": "1"},
          "deviceIntegrity": {"deviceRecognitionVerdict": $device},
          "accountDetails": {"appLicensingVerdict": "$licensing"},
          "environmentDetails": {"playProtectVerdict": "NO_ISSUES"}
        }
        """.trimIndent(),
    )

    private fun check(p: IntegrityPayload, c: IntegrityConfig = config, nonce: String = "abc123") =
        IntegrityRules.evaluate(p, nonce, c, now)

    @Test
    fun genuineInstallPasses() {
        assertEquals(IntegrityVerdict(true, emptyList()), check(payload()))
        // Play may add base64 padding to the nonce.
        assertTrue(check(payload(nonce = "abc123=="), nonce = "abc123").passed)
    }

    @Test
    fun catchesEachProblem() {
        assertEquals(listOf("nonce_mismatch"), check(payload(nonce = "other")).failures)
        assertEquals(listOf("stale_token"), check(payload(timestamp = now - 11 * 60_000)).failures)
        assertEquals(listOf("app_not_recognized:UNRECOGNIZED_VERSION"), check(payload(appVerdict = "UNRECOGNIZED_VERSION")).failures)
        assertTrue("wrong_package" in check(payload(packageName = "com.evil.clone")).failures)
        assertEquals(listOf("device_integrity:none"), check(payload(device = "[]")).failures)
        assertEquals(listOf("device_integrity:MEETS_BASIC_INTEGRITY"), check(payload(device = """["MEETS_BASIC_INTEGRITY"]""")).failures)
    }

    @Test
    fun optionalRules() {
        val basic = """["MEETS_BASIC_INTEGRITY"]"""
        assertTrue(check(payload(device = basic), config.copy(requireDeviceIntegrity = false)).passed)

        assertTrue(check(payload(licensing = "UNLICENSED")).passed, "licensing is not required by default")
        assertFalse(check(payload(licensing = "UNLICENSED"), config.copy(requireLicensed = true)).passed)

        assertTrue(check(payload(), config.copy(certificateDigests = setOf("6a6a1474b5cbbb2b1aa57e0bc3"))).passed)
        assertEquals(listOf("wrong_certificate"), check(payload(), config.copy(certificateDigests = setOf("ffff"))).failures)
    }
}

class IntegrityRegistrationTest {

    private class StubAi : AiService {
        override suspend fun analyzeMealDescription(description: String) =
            MealAnalysis("m", listOf(FoodItem("f", "1", 100, 1, 1, 1)), "high", "")
        override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String) = error("unused")
        override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String): WorkoutPlan = error("unused")
        override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets): NutritionPlan = error("unused")
        override suspend fun coachReply(context: String, history: List<ChatMessage>) = error("unused")
        override suspend fun weeklyCheckIn(report: String): CheckInResult = error("unused")
    }

    /** Passes tokens that say "good:<nonce>", fails everything else, and records what it saw. */
    private class FakeVerifier : IntegrityVerifier {
        val seen = mutableListOf<Pair<String, String>>()
        var throws = false
        override suspend fun verify(integrityToken: String, expectedNonce: String): IntegrityVerdict {
            seen += integrityToken to expectedNonce
            if (throws) error("Google is down")
            return if (integrityToken == "good:$expectedNonce") IntegrityVerdict(true, emptyList())
            else IntegrityVerdict.failed("device_integrity:none")
        }
    }

    private fun config(mode: IntegrityMode, limitedUnits: Int = 2) = ServerConfig(
        anthropicApiKey = "unused",
        tokenSecret = "s".repeat(32),
        limits = UsageLimits(deviceUnitsPerDay = 10, limitedUnitsPerDay = limitedUnits),
        integrity = IntegrityConfig(mode = mode),
    )

    private fun api(config: ServerConfig, verifier: IntegrityVerifier, block: suspend (HttpClient) -> Unit) = testApplication {
        application { fitHallModule(config, StubAi(), integrity = verifier) }
        block(createClient { install(ContentNegotiation) { json() } })
    }

    private suspend fun HttpClient.challenge(): String = post(Api.CHALLENGE).body<ChallengeResponse>().nonce

    private suspend fun HttpClient.register(request: RegisterRequest) = post(Api.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    private suspend fun HttpClient.mealCalls(token: String, times: Int) = (1..times).map {
        post(Api.MEAL_TEXT) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(MealTextRequest("toast"))
        }.status
    }

    @Test
    fun enforceGivesVerifiedDevicesFullAccess() {
        val verifier = FakeVerifier()
        api(config(IntegrityMode.ENFORCE), verifier) { client ->
            val nonce = client.challenge()
            val response = client.register(RegisterRequest(nonce, "good:$nonce")).body<RegisterResponse>()
            assertTrue(response.fullAccess)
            assertEquals(listOf("good:$nonce" to nonce), verifier.seen)
            // Full allowance: 10 units of 1-unit calls.
            assertTrue(client.mealCalls(response.token, 10).all { it == HttpStatusCode.OK })
        }
    }

    @Test
    fun enforceGivesUnverifiedDevicesTheLimitedAllowance() = api(config(IntegrityMode.ENFORCE, limitedUnits = 2), FakeVerifier()) { client ->
        val nonce = client.challenge()
        val response = client.register(RegisterRequest(nonce, "bad")).body<RegisterResponse>()
        assertFalse(response.fullAccess)
        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK, HttpStatusCode.TooManyRequests), client.mealCalls(response.token, 3))

        // No token at all (e.g. no Play services) is treated the same way.
        val noToken = client.register(RegisterRequest(client.challenge(), null)).body<RegisterResponse>()
        assertFalse(noToken.fullAccess)
    }

    @Test
    fun enforceWithNoLimitedAllowanceRefusesUnverifiedDevices() = api(config(IntegrityMode.ENFORCE, limitedUnits = 0), FakeVerifier()) { client ->
        val response = client.register(RegisterRequest(client.challenge(), "bad"))
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(ErrorCodes.INTEGRITY_FAILED, response.body<ApiError>().code)
    }

    @Test
    fun nonceCannotBeReusedToReplayAGoodVerdict() {
        val verifier = FakeVerifier()
        api(config(IntegrityMode.ENFORCE), verifier) { client ->
            val nonce = client.challenge()
            assertTrue(client.register(RegisterRequest(nonce, "good:$nonce")).body<RegisterResponse>().fullAccess)
            assertFalse(client.register(RegisterRequest(nonce, "good:$nonce")).body<RegisterResponse>().fullAccess)
            assertEquals(1, verifier.seen.size, "the replayed nonce is rejected before calling Google")
        }
    }

    @Test
    fun googleOutageFallsBackToLimitedNotRefused() {
        val verifier = FakeVerifier().apply { throws = true }
        api(config(IntegrityMode.ENFORCE), verifier) { client ->
            val nonce = client.challenge()
            val response = client.register(RegisterRequest(nonce, "good:$nonce"))
            assertEquals(HttpStatusCode.OK, response.status)
            assertFalse(response.body<RegisterResponse>().fullAccess)
        }
    }

    @Test
    fun monitorChecksButAlwaysGivesFullAccess() {
        val verifier = FakeVerifier()
        api(config(IntegrityMode.MONITOR), verifier) { client ->
            val response = client.register(RegisterRequest(client.challenge(), "bad")).body<RegisterResponse>()
            assertTrue(response.fullAccess)
            assertEquals(1, verifier.seen.size)
        }
    }

    @Test
    fun offSkipsChecksAndAcceptsAnEmptyBody() {
        val verifier = FakeVerifier()
        api(config(IntegrityMode.OFF), verifier) { client ->
            val response = client.post(Api.REGISTER)
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.body<RegisterResponse>().fullAccess)
            assertTrue(verifier.seen.isEmpty())
        }
    }
}
