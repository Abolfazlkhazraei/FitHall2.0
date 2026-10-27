package com.fithall.app

import com.fithall.app.auth.AuthException
import com.fithall.app.auth.AuthState
import com.fithall.app.auth.AuthValidation
import com.fithall.app.auth.AuthViewModel
import com.fithall.app.auth.FirebaseAuthService
import com.fithall.app.data.InMemoryAccountStorage
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.Equipment
import com.fithall.app.model.Experience
import com.fithall.app.model.Goal
import com.fithall.app.model.Sex
import com.fithall.app.model.UserProfile
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountsTest {

    // ---- Form validation ----

    @Test
    fun emailValidation() {
        assertNull(AuthValidation.email(" ana@example.com "))
        assertNotNull(AuthValidation.email(""))
        assertNotNull(AuthValidation.email("ana@example"))
        assertNotNull(AuthValidation.email("ana example.com"))
        assertNotNull(AuthValidation.email("a".repeat(250) + "@x.com"))
    }

    @Test
    fun newPasswordsNeedLengthLettersAndNumbers() {
        assertNull(AuthValidation.newPassword("strong123"))
        assertNotNull(AuthValidation.newPassword("abc123"))
        assertNotNull(AuthValidation.newPassword("onlyletters"))
        assertNotNull(AuthValidation.newPassword("12345678"))
        assertNotNull(AuthValidation.newPassword("a1".repeat(65)))
        assertNotNull(AuthValidation.confirmation("strong123", "strong124"))
    }

    @Test
    fun invalidFormsNeverReachFirebase() {
        val requests = mutableListOf<HttpRequestData>()
        val vm = AuthViewModel(firebase(requests = requests) { error("no request expected") })
        vm.signUp("ana@example.com", "strong123", "different1", acceptedTerms = true)
        assertEquals("The passwords don't match.", vm.ui.value.error)
        vm.signUp("ana@example.com", "strong123", "strong123", acceptedTerms = false)
        assertEquals("Please accept the Terms of Use and Privacy Policy.", vm.ui.value.error)
        vm.signIn("not-an-email", "x")
        assertTrue(vm.ui.value.error!!.startsWith("Enter a valid email"))
        assertTrue(requests.isEmpty())
    }

    // ---- Firebase REST client ----

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(body, status, jsonHeaders)
    private fun MockRequestHandleScope.firebaseError(code: String) = json("""{"error":{"code":400,"message":"$code"}}""", HttpStatusCode.BadRequest)
    private fun HttpRequestData.bodyText() = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
    private fun HttpRequestData.method() = url.encodedPath.substringAfterLast('/')

    private val session = """{"localId":"uid1","email":"ana@example.com","idToken":"id1","refreshToken":"r1","expiresIn":"3600"}"""

    private fun firebase(
        store: InMemoryKeyValueStore = InMemoryKeyValueStore(),
        requests: MutableList<HttpRequestData> = mutableListOf(),
        clock: () -> Long = { 0L },
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ) = FirebaseAuthService("key", store, mapOf("X-Android-Package" to "com.fithall.app"), clock, MockEngine { request ->
        requests += request
        handler(request)
    })

    @Test
    fun signUpKeepsTheSessionAndSendsAVerificationEmail() = runTest {
        val store = InMemoryKeyValueStore()
        val requests = mutableListOf<HttpRequestData>()
        val auth = firebase(store, requests) { request ->
            when (request.method()) {
                "accounts:signUp" -> json(session)
                "accounts:sendOobCode" -> json("""{"email":"ana@example.com"}""")
                else -> error("unexpected ${request.url}")
            }
        }
        auth.signUp(" ana@example.com ", "strong123")

        val state = auth.state.value as AuthState.SignedIn
        assertEquals("uid1", state.user.uid)
        assertEquals(false, state.user.emailVerified)
        assertTrue(requests[0].bodyText().contains("\"email\":\"ana@example.com\""))
        assertTrue(requests[1].bodyText().contains("VERIFY_EMAIL"))
        assertEquals("com.fithall.app", requests[0].headers["X-Android-Package"])
        // A new instance (app restart) is still signed in.
        assertTrue(firebase(store) { error("offline") }.state.value is AuthState.SignedIn)
    }

    @Test
    fun firebaseErrorsBecomeReadableMessages() = runTest {
        val auth = firebase { firebaseError("INVALID_LOGIN_CREDENTIALS") }
        val error = assertFailsWith<AuthException> { auth.signIn("ana@example.com", "wrong") }
        assertEquals("Email or password is incorrect.", error.message)
        assertEquals(AuthState.SignedOut, auth.state.value)

        val weak = firebase { firebaseError("WEAK_PASSWORD : Password should be at least 6 characters") }
        assertTrue(assertFailsWith<AuthException> { weak.signUp("a@b.co", "x") }.message!!.startsWith("Choose a stronger password"))
    }

    @Test
    fun passwordResetDoesNotRevealWhetherAnAccountExists() = runTest {
        firebase { firebaseError("EMAIL_NOT_FOUND") }.sendPasswordReset("nobody@example.com")
        val limited = firebase { firebaseError("TOO_MANY_ATTEMPTS_TRY_LATER") }
        assertFailsWith<AuthException> { limited.sendPasswordReset("ana@example.com") }
    }

    @Test
    fun expiringTokensAreRefreshedAndEndedSessionsSignOut() = runTest {
        var now = 0L
        var refreshFails = false
        val requests = mutableListOf<HttpRequestData>()
        val auth = firebase(requests = requests, clock = { now }) { request ->
            when {
                request.url.host == "securetoken.googleapis.com" ->
                    if (refreshFails) firebaseError("TOKEN_EXPIRED")
                    else json("""{"id_token":"id2","refresh_token":"r2","expires_in":"3600"}""")
                request.method() == "accounts:signInWithPassword" -> json(session)
                request.method() == "accounts:lookup" -> json("""{"users":[{"localId":"uid1","emailVerified":true}]}""")
                else -> error("unexpected ${request.url}")
            }
        }
        auth.signIn("ana@example.com", "strong123")
        assertEquals(true, (auth.state.value as AuthState.SignedIn).user.emailVerified)

        val refreshesSoFar = requests.count { it.url.host == "securetoken.googleapis.com" }
        assertEquals("id2", auth.idToken())
        assertEquals(refreshesSoFar, requests.count { it.url.host == "securetoken.googleapis.com" }, "a fresh token is reused")

        now += 3_600_000
        refreshFails = true
        assertFailsWith<AuthException> { auth.idToken() }
        assertEquals(AuthState.SignedOut, auth.state.value)
        assertNull(auth.idToken())
    }

    @Test
    fun deletingTheAccountChecksThePasswordFirst() = runTest {
        val methods = mutableListOf<String>()
        val auth = firebase { request ->
            methods += request.method()
            when (request.method()) {
                "accounts:signInWithPassword" ->
                    if (request.bodyText().contains("strong123")) json(session) else firebaseError("INVALID_PASSWORD")
                "accounts:lookup" -> json("""{"users":[{"emailVerified":true}]}""")
                "accounts:delete" -> json("{}")
                else -> json("""{"id_token":"id2","refresh_token":"r2","expires_in":"3600"}""")
            }
        }
        auth.signIn("ana@example.com", "strong123")
        methods.clear()
        assertFailsWith<AuthException> { auth.deleteAccount("wrong") }
        assertTrue("accounts:delete" !in methods)
        auth.deleteAccount("strong123")
        assertEquals("accounts:delete", methods.last())
    }

    // ---- Per-account data ----

    private val profile = UserProfile(
        "Ana", 28, Sex.FEMALE, 168, 62.0, ActivityLevel.MODERATE, Experience.BEGINNER,
        Goal.BUILD_MUSCLE, Equipment.HOME_DUMBBELLS, 3, 45,
    )

    @Test
    fun eachAccountSeesOnlyItsOwnData() {
        val storage = InMemoryAccountStorage()
        storage.open("ana").profile = profile
        assertNull(storage.open("ben").profile)
        assertEquals("Ana", storage.open("ana").profile?.name)
    }

    // ---- Analytics consent ----

    private class RecordingAnalytics : Analytics {
        var enabled: Boolean? = null
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun event(name: String, params: Map<String, String>) = Unit
    }

    @Test
    fun analyticsFollowTheUsersChoice() {
        val repo = InMemoryAccountStorage().open("ana")
        val analytics = RecordingAnalytics()
        val vm = AppViewModel(repo, NoAiForAccounts, analytics = analytics)
        assertEquals(false, analytics.enabled)
        vm.acceptConsent(crashReports = false, analytics = true)
        assertEquals(true, analytics.enabled)
        assertEquals(true, repo.consent?.analytics)
        // Restarting with the saved choice keeps it.
        val restarted = RecordingAnalytics()
        AppViewModel(repo, NoAiForAccounts, analytics = restarted)
        assertEquals(true, restarted.enabled)
        vm.setAnalytics(false)
        assertEquals(false, analytics.enabled)
        assertEquals(false, repo.consent?.analytics)
    }
}

private object NoAiForAccounts : com.fithall.app.ai.AiService {
    override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String) = error("unused")
    override suspend fun analyzeMealDescription(description: String) = error("unused")
    override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String) = error("unused")
    override suspend fun generateNutritionPlan(profile: UserProfile, targets: com.fithall.app.model.MacroTargets) = error("unused")
    override suspend fun coachReply(context: String, history: List<com.fithall.app.model.ChatMessage>) = error("unused")
    override suspend fun weeklyCheckIn(report: String) = error("unused")
}
