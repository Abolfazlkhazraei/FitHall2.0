package com.fithall.app.auth

import com.fithall.app.data.KeyValueStore
import com.fithall.app.platform.nowMillis
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Firebase Authentication through its REST API, so the same code runs on Android, iOS and
 * desktop. Firebase stores the account and sends the verification and reset emails. The session
 * (ID and refresh tokens) is kept in [store], which must not be backed up or shared.
 *
 * [apiKey] is the Firebase Web API key: an identifier rather than a secret, but restrict it to the
 * app in Google Cloud. [extraHeaders] carries the Android package and signing-certificate headers
 * such a restriction checks.
 */
class FirebaseAuthService(
    private val apiKey: String,
    private val store: KeyValueStore,
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val clock: () -> Long = ::nowMillis,
    /** Only for tests; production picks the platform engine automatically. */
    engine: HttpClientEngine? = null,
) : AuthService {

    @Serializable
    private data class Session(
        val uid: String,
        val email: String,
        val emailVerified: Boolean,
        val provider: String = PASSWORD_PROVIDER,
        val idToken: String,
        val refreshToken: String,
        val expiresAtMillis: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val client: HttpClient = run {
        val config: HttpClientConfig<*>.() -> Unit = {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
        }
        if (engine != null) HttpClient(engine, config) else HttpClient(config)
    }

    private var session: Session? = store.get(SESSION_KEY)?.let { runCatching { json.decodeFromString(Session.serializer(), it) }.getOrNull() }
    private val _state = MutableStateFlow(stateOf(session))

    override val isConfigured = true
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    override suspend fun signUp(email: String, password: String) {
        val response = call("accounts:signUp", credentials(email, password))
        save(sessionFrom(response, emailVerified = false))
        // Best effort: the verify screen has a resend button if this fails.
        runCatching { sendEmailVerification() }
    }

    override suspend fun signIn(email: String, password: String) {
        val response = call("accounts:signInWithPassword", credentials(email, password))
        save(sessionFrom(response, emailVerified = false))
        reloadUser()
    }

    override suspend fun signInWithProvider(provider: SocialProvider, token: String) {
        val response = signInWithIdp(provider, token)
        val verified = response["emailVerified"]?.jsonPrimitive?.booleanOrNull == true
        save(sessionFrom(response, emailVerified = verified, provider = provider.id))
    }

    /** Exchanges a Google ID token or Facebook access token for a Firebase session. */
    private suspend fun signInWithIdp(provider: SocialProvider, token: String): JsonObject {
        val response = call("accounts:signInWithIdp", buildJsonObject {
            put("postBody", "${provider.tokenParam}=${token.encodeURLParameter()}&providerId=${provider.id}")
            put("requestUri", "http://localhost")
            put("returnIdpCredential", true)
            put("returnSecureToken", true)
        })
        // The email already has an account that signs in another way; Firebase won't merge them silently.
        if (response["needConfirmation"]?.jsonPrimitive?.booleanOrNull == true) {
            throw AuthException("There's already a FitHall account for this email. Log in the way you did before.")
        }
        return response
    }

    override suspend fun sendEmailVerification() {
        val token = idToken() ?: throw AuthException(SIGNED_OUT)
        call("accounts:sendOobCode", buildJsonObject {
            put("requestType", "VERIFY_EMAIL")
            put("idToken", token)
        })
    }

    override suspend fun reloadUser() {
        // A fresh token carries the current email_verified claim, which the server checks.
        val token = idToken(forceRefresh = true) ?: return
        val user = call("accounts:lookup", buildJsonObject { put("idToken", token) })["users"]
            ?.jsonArray?.firstOrNull()?.jsonObject ?: return
        val verified = user["emailVerified"]?.jsonPrimitive?.booleanOrNull == true
        lock.withLock { session?.let { save(it.copy(emailVerified = verified)) } }
    }

    override suspend fun sendPasswordReset(email: String) {
        try {
            post("$IDENTITY_URL/accounts:sendOobCode?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("requestType", "PASSWORD_RESET")
                    put("email", email.trim())
                }.toString())
            }
        } catch (e: FirebaseError) {
            // Don't reveal whether an account exists.
            if (e.code != "EMAIL_NOT_FOUND") throw e.toAuthException()
        }
    }

    override suspend fun deleteAccount(password: String) {
        val email = session?.email ?: throw AuthException(SIGNED_OUT)
        // Firebase only deletes after a recent sign-in, so sign in again with the password.
        val fresh = call("accounts:signInWithPassword", credentials(email, password))
        val token = fresh["idToken"]?.jsonPrimitive?.contentOrNull ?: throw AuthException(GENERIC)
        call("accounts:delete", buildJsonObject { put("idToken", token) })
    }

    override suspend fun deleteAccount(provider: SocialProvider, token: String) {
        val current = session ?: throw AuthException(SIGNED_OUT)
        // Firebase only deletes after a recent sign-in, so sign in again with the provider.
        val fresh = signInWithIdp(provider, token)
        if (fresh.string("localId") != current.uid) {
            throw AuthException("That's a different ${provider.label} account. Choose the one you use for FitHall.")
        }
        val freshToken = fresh.string("idToken") ?: throw AuthException(GENERIC)
        call("accounts:delete", buildJsonObject { put("idToken", freshToken) })
    }

    override fun signOut() {
        session = null
        store.put(SESSION_KEY, null)
        _state.value = AuthState.SignedOut
    }

    override suspend fun idToken(forceRefresh: Boolean): String? = lock.withLock {
        val current = session ?: return@withLock null
        if (!forceRefresh && current.expiresAtMillis - clock() > REFRESH_MARGIN_MILLIS) return@withLock current.idToken
        val response = try {
            post("$TOKEN_URL?key=$apiKey") {
                setBody(FormDataContent(parameters {
                    append("grant_type", "refresh_token")
                    append("refresh_token", current.refreshToken)
                }))
            }
        } catch (e: FirebaseError) {
            if (e.code in SESSION_ENDED) signOut()
            throw e.toAuthException()
        }
        val refreshed = current.copy(
            idToken = response.string("id_token") ?: throw AuthException(GENERIC),
            refreshToken = response.string("refresh_token") ?: current.refreshToken,
            expiresAtMillis = clock() + (response.string("expires_in")?.toLongOrNull() ?: 3600) * 1000,
        )
        save(refreshed)
        refreshed.idToken
    }

    // ---- Plumbing ----

    private class FirebaseError(val code: String) : Exception(code)

    private fun credentials(email: String, password: String) = buildJsonObject {
        put("email", email.trim())
        put("password", password)
        put("returnSecureToken", true)
    }

    private fun sessionFrom(response: JsonObject, emailVerified: Boolean, provider: String = PASSWORD_PROVIDER) = Session(
        uid = response.string("localId") ?: throw AuthException(GENERIC),
        email = response.string("email") ?: "",
        emailVerified = emailVerified,
        provider = provider,
        idToken = response.string("idToken") ?: throw AuthException(GENERIC),
        refreshToken = response.string("refreshToken") ?: throw AuthException(GENERIC),
        expiresAtMillis = clock() + (response.string("expiresIn")?.toLongOrNull() ?: 3600) * 1000,
    )

    private fun save(value: Session) {
        session = value
        store.put(SESSION_KEY, json.encodeToString(Session.serializer(), value))
        _state.value = stateOf(value)
    }

    private fun stateOf(value: Session?): AuthState =
        value?.let { AuthState.SignedIn(AuthUser(it.uid, it.email, it.emailVerified, it.provider)) } ?: AuthState.SignedOut

    private suspend fun call(method: String, body: JsonObject): JsonObject = try {
        post("$IDENTITY_URL/$method?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    } catch (e: FirebaseError) {
        throw e.toAuthException()
    }

    /** Throws [FirebaseError] for Firebase's error codes and [AuthException] for network trouble. */
    private suspend fun post(url: String, configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit): JsonObject {
        val response: HttpResponse = try {
            client.post(url) {
                extraHeaders.forEach { (name, value) -> header(name, value) }
                configure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthException("Couldn't connect. Check your internet connection and try again.", e)
        }
        val body = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
        if (response.status.isSuccess() && body != null) return body
        // Firebase sends e.g. {"error":{"message":"WEAK_PASSWORD : Password should be ..."}}.
        val message = body?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        throw FirebaseError(message?.substringBefore(' ')?.substringBefore(':')?.trim() ?: "HTTP_${response.status.value}")
    }

    private fun JsonObject.string(name: String) = this[name]?.jsonPrimitive?.contentOrNull

    private fun FirebaseError.toAuthException() = AuthException(messageFor(code), this)

    companion object {
        private const val SESSION_KEY = "auth_session"
        private const val IDENTITY_URL = "https://identitytoolkit.googleapis.com/v1"
        private const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token"
        private const val REFRESH_MARGIN_MILLIS = 5 * 60_000L
        private const val GENERIC = "Something went wrong. Please try again."
        private const val SIGNED_OUT = "You're signed out. Please log in again."
        private val SESSION_ENDED = setOf("TOKEN_EXPIRED", "USER_DISABLED", "USER_NOT_FOUND", "INVALID_REFRESH_TOKEN", "INVALID_GRANT_TYPE")

        /** Firebase error codes as messages people can act on. */
        fun messageFor(code: String): String = when (code) {
            "EMAIL_EXISTS" -> "There's already an account with this email. Log in instead."
            "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" -> "Email or password is incorrect."
            "USER_DISABLED" -> "This account has been disabled. Contact support if you think this is a mistake."
            "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Wait a few minutes and try again."
            "WEAK_PASSWORD" -> "Choose a stronger password: at least ${AuthValidation.MIN_PASSWORD} characters with letters and numbers."
            "INVALID_EMAIL", "MISSING_EMAIL" -> "Enter a valid email address."
            "INVALID_IDP_RESPONSE", "INVALID_CREDENTIAL_OR_PROVIDER_ID" -> "That sign-in didn't work. Please try again."
            "FEDERATED_USER_ID_ALREADY_LINKED" -> "That account is already linked to another FitHall account."
            "MISSING_PASSWORD" -> "Enter your password."
            "OPERATION_NOT_ALLOWED", "ADMIN_ONLY_OPERATION" -> "Email sign-in isn't enabled for FitHall yet. Please try again later."
            "TOKEN_EXPIRED", "USER_NOT_FOUND", "INVALID_REFRESH_TOKEN", "INVALID_ID_TOKEN", "CREDENTIAL_TOO_OLD_LOGIN_AGAIN" ->
                "Your sign-in has expired. Please log in again."
            else -> GENERIC
        }
    }
}
