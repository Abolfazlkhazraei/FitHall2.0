package com.fithall.app.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AuthUser(val uid: String, val email: String, val emailVerified: Boolean)

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/** A failed account action, with a [message] that is safe to show. */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** FitHall accounts: email and password, with email verification and password reset. */
interface AuthService {
    /** False in builds without Firebase (local development): everyone counts as signed in. */
    val isConfigured: Boolean
    val state: StateFlow<AuthState>

    suspend fun signUp(email: String, password: String)
    suspend fun signIn(email: String, password: String)
    suspend fun sendEmailVerification()
    /** Re-reads the account, e.g. to see whether the email has been verified since. */
    suspend fun reloadUser()
    /** Succeeds whether or not an account exists, so it can't be used to find out who has one. */
    suspend fun sendPasswordReset(email: String)
    /** Permanently deletes the account after checking [password]. Doesn't sign out; the caller does. */
    suspend fun deleteAccount(password: String)
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
    override suspend fun sendEmailVerification() = unavailable()
    override suspend fun reloadUser() = Unit
    override suspend fun sendPasswordReset(email: String) = unavailable()
    override suspend fun deleteAccount(password: String) = unavailable()
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
