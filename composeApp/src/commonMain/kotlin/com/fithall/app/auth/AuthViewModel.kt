package com.fithall.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fithall.app.platform.nowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val busy: Boolean = false,
    /** What went wrong with the last action. */
    val error: String? = null,
    /** A confirmation, e.g. "We've sent you an email". */
    val info: String? = null,
    /** Resending emails is allowed again at this time, so people can't flood an inbox. */
    val resendAllowedAtMillis: Long = 0,
)

/** Runs sign-in, sign-up, verification, reset and deletion, and tracks their progress for the UI. */
class AuthViewModel(val auth: AuthService, private val clock: () -> Long = ::nowMillis) : ViewModel() {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun clearMessages() = _ui.update { it.copy(error = null, info = null) }

    fun signIn(email: String, password: String) {
        val problem = AuthValidation.email(email) ?: if (password.isEmpty()) "Enter your password." else null
        run(problem) { auth.signIn(email.trim(), password) }
    }

    fun signUp(email: String, password: String, confirmation: String, acceptedTerms: Boolean) {
        val problem = AuthValidation.email(email)
            ?: AuthValidation.newPassword(password)
            ?: AuthValidation.confirmation(password, confirmation)
            ?: if (!acceptedTerms) "Please accept the Terms of Use and Privacy Policy." else null
        run(problem) {
            auth.signUp(email.trim(), password)
            startCooldown()
        }
    }

    fun sendPasswordReset(email: String, onSent: () -> Unit = {}) {
        if (clock() < _ui.value.resendAllowedAtMillis) {
            _ui.update { it.copy(error = "Please wait a minute before asking for another email.") }
            return
        }
        run(AuthValidation.email(email)) {
            auth.sendPasswordReset(email.trim())
            startCooldown()
            _ui.update { it.copy(info = "If there's an account for ${email.trim()}, we've sent a link to reset its password. Check your inbox and spam folder.") }
            onSent()
        }
    }

    fun resendVerification() {
        if (clock() < _ui.value.resendAllowedAtMillis) {
            _ui.update { it.copy(error = "Please wait a minute before asking for another email.") }
            return
        }
        run(null) {
            auth.sendEmailVerification()
            startCooldown()
            _ui.update { it.copy(info = "Verification email sent. Check your inbox and spam folder.") }
        }
    }

    fun checkVerified() = run(null) {
        auth.reloadUser()
        val verified = (auth.state.value as? AuthState.SignedIn)?.user?.emailVerified == true
        if (!verified) _ui.update { it.copy(error = "Your email isn't verified yet. Tap the link in the email, then try again.") }
    }

    /** [wipeLocalData] runs after the account is deleted and before signing out. */
    fun deleteAccount(password: String, wipeLocalData: () -> Unit) {
        run(if (password.isEmpty()) "Enter your password to confirm." else null) {
            auth.deleteAccount(password)
            wipeLocalData()
            auth.signOut()
        }
    }

    private fun startCooldown() = _ui.update { it.copy(resendAllowedAtMillis = clock() + RESEND_COOLDOWN_MILLIS) }

    private fun run(validationError: String?, action: suspend () -> Unit) {
        if (validationError != null) {
            _ui.update { it.copy(error = validationError, info = null) }
            return
        }
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                _ui.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = "Something went wrong. Please try again.") }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val RESEND_COOLDOWN_MILLIS = 60_000L
    }
}
