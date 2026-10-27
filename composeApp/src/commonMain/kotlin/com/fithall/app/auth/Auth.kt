package com.fithall.app.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How an account signs in, as Firebase names the provider. */
enum class SocialProvider(val id: String, val label: String, internal val tokenParam: String) {
    GOOGLE("google.com", "Google", "id_token"),
    FACEBOOK("facebook.com", "Facebook", "access_token"),
}

const val PASSWORD_PROVIDER = "password"

data class AuthUser(
    val uid: String,
    val email: String,
    val emailVerified: Boolean,
    /** [PASSWORD_PROVIDER] or a [SocialProvider.id]. */
    val provider: String = PASSWORD_PROVIDER,
) {
    val socialProvider: SocialProvider? get() = SocialProvider.entries.firstOrNull { it.id == provider }

    /** Google and Facebook accounts don't need to verify their email; the server agrees. */
    val verified: Boolean get() = emailVerified || socialProvider != null
}

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/** A failed account action, with a [message] that is safe to show. */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The user closed the Google or Facebook sign-in; not an error. */
class SignInCancelled : Exception("Sign-in cancelled")

/**
 * Gets a token from Google or Facebook on this device, for [AuthService.signInWithProvider].
 * [available] is empty where neither is set up (desktop, iOS, builds without the keys).
 */
interface SocialSignIn {
    val available: List<SocialProvider>
    /** Throws [SignInCancelled] when the user backs out, [AuthException] when it fails. */
    suspend fun token(provider: SocialProvider): String
}

object NoSocialSignIn : SocialSignIn {
    override val available = emptyList<SocialProvider>()
    override suspend fun token(provider: SocialProvider): String =
        throw AuthException("${provider.label} sign-in isn't available here.")
}

/** FitHall accounts: email and password (with verification and reset), or Google or Facebook. */
interface AuthService {
    /** False in builds without Firebase (local development): everyone counts as signed in. */
    val isConfigured: Boolean
    val state: StateFlow<AuthState>

    suspend fun signUp(email: String, password: String)
    suspend fun signIn(email: String, password: String)
    /** Signs in, or creates the account on first use, with a token from [SocialSignIn]. */
    suspend fun signInWithProvider(provider: SocialProvider, token: String)
    suspend fun sendEmailVerification()
    /** Re-reads the account, e.g. to see whether the email has been verified since. */
    suspend fun reloadUser()
    /** Succeeds whether or not an account exists, so it can't be used to find out who has one. */
    suspend fun sendPasswordReset(email: String)
    /** Permanently deletes the account after checking [password]. Doesn't sign out; the caller does. */
    suspend fun deleteAccount(password: String)
    /** The same for Google and Facebook accounts, confirmed with a fresh token from that provider. */
    suspend fun deleteAccount(provider: SocialProvider, token: String)
    fun signOut()

    /** A current ID token for FitHall's server, or null when signed out or accounts are off. */
    suspend fun idToken(forceRefresh: Boolean = false): String?
}

/** Builds without Firebase: no accounts, one local user, and the server's accounts must be off too. */
object NoAccounts : AuthService {
    override val isConfigured = false
    override val state: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedIn(AuthUser("local", "", emailVerified = true)))
    private fun unavailable(): Nothing = throw AuthException("Accounts aren't set up in this build.")
    override suspend fun signUp(email: String, password: String) = unavailable()
    override suspend fun signIn(email: String, password: String) = unavailable()
    override suspend fun signInWithProvider(provider: SocialProvider, token: String) = unavailable()
    override suspend fun sendEmailVerification() = unavailable()
    override suspend fun reloadUser() = Unit
    override suspend fun sendPasswordReset(email: String) = unavailable()
    override suspend fun deleteAccount(password: String) = unavailable()
    override suspend fun deleteAccount(provider: SocialProvider, token: String) = unavailable()
    override fun signOut() = Unit
    override suspend fun idToken(forceRefresh: Boolean): String? = null
}

/** Form checks for the sign-in screens. Each returns an error message, or null when valid. */
object AuthValidation {
    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 128
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    fun email(email: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> "Enter your email address."
            trimmed.length > 254 || !emailPattern.matches(trimmed) -> "Enter a valid email address, like name@example.com."
            else -> null
        }
    }

    /** For new passwords. Signing in only checks the password isn't empty. */
    fun newPassword(password: String): String? = when {
        password.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters."
        password.length > MAX_PASSWORD -> "Use at most $MAX_PASSWORD characters."
        password.none { it.isLetter() } || password.none { it.isDigit() } -> "Include at least one letter and one number."
        else -> null
    }

    fun confirmation(password: String, confirmation: String): String? =
        if (password != confirmation) "The passwords don't match." else null
}
