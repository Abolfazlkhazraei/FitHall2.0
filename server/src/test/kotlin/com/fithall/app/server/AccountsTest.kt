package com.fithall.app.server

import com.fithall.app.ai.AiService
import com.fithall.app.api.Api
import com.fithall.app.api.ApiError
import com.fithall.app.api.ErrorCodes
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
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountsTest {

    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val otherKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val now = 1_800_000_000L
    private val verifier = FirebaseTokenVerifier("fithall-app", { mapOf("k1" to keys.public) }, nowSeconds = { now })

    private fun b64(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())

    private fun token(
        sub: String = "uid1",
        aud: String = "fithall-app",
        iss: String = "https://securetoken.google.com/fithall-app",
        exp: Long = now + 600,
        verified: Boolean = true,
        kid: String = "k1",
        alg: String = "RS256",
        signWith: PrivateKey = keys.private,
    ): String {
        val head = b64("""{"alg":"$alg","kid":"$kid","typ":"JWT"}""")
        val body = b64("""{"sub":"$sub","aud":"$aud","iss":"$iss","exp":$exp,"iat":${now - 60},"auth_time":${now - 60},"email_verified":$verified}""")
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(signWith); update("$head.$body".toByteArray()) }.sign()
        return "$head.$body.${Base64.getUrlEncoder().withoutPadding().encodeToString(signature)}"
    }

    @Test
    fun acceptsOnlyGenuineCurrentTokensForThisProject() {
        assertEquals(FirebaseUser("uid1", emailVerified = true), verifier.verify(token()))
        assertEquals(false, verifier.verify(token(verified = false))?.emailVerified)
        assertNull(verifier.verify(token(signWith = otherKeys.private)), "forged signature")
        assertNull(verifier.verify(token(aud = "someone-else")), "other project")
        assertNull(verifier.verify(token(iss = "https://evil.example")), "wrong issuer")
        assertNull(verifier.verify(token(exp = now - 3600)), "expired")
        assertNull(verifier.verify(token(kid = "unknown")), "unknown key")
        assertNull(verifier.verify(token(alg = "none")), "alg none")
        assertNull(verifier.verify(token(sub = "")), "no uid")
        assertNull(verifier.verify("not.a.jwt"))
        // Changing the payload after signing breaks the signature.
        val parts = token().split('.')
        assertNull(verifier.verify("${parts[0]}.${b64("""{"sub":"admin","aud":"fithall-app"}""")}.${parts[2]}"))
    }

    // ---- The API with accounts on ----

    private val meal = MealAnalysis("Toast", listOf(FoodItem("Toast", "1 slice", 80, 3, 15, 1)), "high", "")

    private val ai = object : AiService {
        override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String) = meal
        override suspend fun analyzeMealDescription(description: String) = meal
        override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String): WorkoutPlan = error("unused")
        override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets): NutritionPlan = error("unused")
        override suspend fun coachReply(context: String, history: List<ChatMessage>): String = error("unused")
        override suspend fun weeklyCheckIn(report: String): CheckInResult = error("unused")
    }

    private val config = ServerConfig(
        anthropicApiKey = "unused",
        tokenSecret = "s".repeat(32),
        limits = UsageLimits(deviceUnitsPerDay = 2, deviceRequestsPerMinute = 100, globalUnitsPerDay = 1000, registrationsPerIpPerHour = 10),
        firebaseProjectId = "fithall-app",
    )

    private fun api(block: suspend (io.ktor.client.HttpClient) -> Unit) = testApplication {
        application { fitHallModule(config, ai, users = verifier) }
        block(createClient { install(ContentNegotiation) { json() } })
    }

    private suspend fun io.ktor.client.HttpClient.device(): String =
        post(Api.REGISTER).body<RegisterResponse>().token

    private suspend fun io.ktor.client.HttpClient.meal(device: String, user: String?) = post(Api.MEAL_TEXT) {
        bearerAuth(device)
        user?.let { header(Api.USER_TOKEN_HEADER, it) }
        contentType(ContentType.Application.Json)
        setBody(MealTextRequest("toast"))
    }

    @Test
    fun aiNeedsASignedInVerifiedAccount() = api { client ->
        val device = client.device()

        val anonymous = client.meal(device, user = null)
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertEquals(ErrorCodes.SIGN_IN_REQUIRED, anonymous.body<ApiError>().code)

        val unverified = client.meal(device, token(verified = false))
        assertEquals(HttpStatusCode.Forbidden, unverified.status)
        assertEquals(ErrorCodes.EMAIL_NOT_VERIFIED, unverified.body<ApiError>().code)

        assertEquals(HttpStatusCode.OK, client.meal(device, token()).status)
    }

    @Test
    fun limitsApplyPerAccountAcrossDevices() = api { client ->
        // Two units a day: a second install doesn't reset the same account's allowance.
        assertEquals(HttpStatusCode.OK, client.meal(client.device(), token()).status)
        assertEquals(HttpStatusCode.OK, client.meal(client.device(), token()).status)
        assertEquals(HttpStatusCode.TooManyRequests, client.meal(client.device(), token()).status)
        // Another account still has its own allowance.
        assertEquals(HttpStatusCode.OK, client.meal(client.device(), token(sub = "uid2")).status)
    }

    // ---- Web pages ----

    @Test
    fun servesPolicyTermsAndDeletionPages() = api { client ->
        val privacy = client.get(Api.PRIVACY_PAGE)
        assertEquals(HttpStatusCode.OK, privacy.status)
        assertTrue(privacy.bodyAsText().contains("<h1>FitHall Privacy Policy</h1>"))
        assertNotNull(privacy.headers["Content-Security-Policy"])
        assertTrue(client.get(Api.TERMS_PAGE).bodyAsText().contains("<h1>FitHall Terms of Use</h1>"))
        val delete = client.get(Api.DELETE_ACCOUNT_PAGE).bodyAsText()
        assertTrue(delete.contains("Profile → Account → Delete account"))
        // Without a web API key there's no form, only the in-app route.
        assertTrue(!delete.contains("<form"))
        assertEquals(HttpStatusCode.OK, client.get("${Api.DELETE_ACCOUNT_PAGE}.js").status)
    }

    @Test
    fun deletionFormAppearsWithAWebApiKey() = testApplication {
        application { fitHallModule(config.copy(firebaseWebApiKey = "web-key"), ai, users = verifier) }
        val page = client.get(Api.DELETE_ACCOUNT_PAGE).bodyAsText()
        assertTrue(page.contains("<form id=\"delete-form\" data-key=\"web-key\">"))
    }

    @Test
    fun unknownAddressesGetAFriendly404() = api { client ->
        val browser = client.get("/no-such-page") { header(HttpHeaders.Accept, "text/html,application/xhtml+xml") }
        assertEquals(HttpStatusCode.NotFound, browser.status)
        assertTrue(browser.bodyAsText().contains("Page not found"))

        val app = client.get("/v1/no-such-endpoint")
        assertEquals(HttpStatusCode.NotFound, app.status)
        assertEquals(ErrorCodes.NOT_FOUND, app.body<ApiError>().code)
    }
}
