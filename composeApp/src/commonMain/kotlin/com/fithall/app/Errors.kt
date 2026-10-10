package com.fithall.app

import com.fithall.app.ai.AiErrorKind
import com.fithall.app.ai.AiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the device has a usable network connection right now. */
interface Connectivity {
    val isOnline: StateFlow<Boolean>
}

/** For platforms without a connectivity monitor, and tests: assume online. */
object AlwaysOnline : Connectivity {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}

/** A failed AI request as the user should see it. */
data class AiFailure(
    val message: String,
    /** True when trying again could work (network or server trouble), false for limits and refusals. */
    val canRetry: Boolean,
)

object ErrorMessages {
    const val OFFLINE = "You're offline. Everything you log is saved on your phone; AI features will work again when you reconnect."

    /** Turns any failure into a message that is safe and useful to show. */
    fun forFailure(error: Throwable, online: Boolean): AiFailure {
        if (!online) return AiFailure(OFFLINE, canRetry = true)
        if (error !is AiException) return AiFailure("Something went wrong. Please try again.", canRetry = true)
        val message = error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
        return when (error.kind) {
            AiErrorKind.NETWORK -> AiFailure("Couldn't reach FitHall. Check your connection and try again.", canRetry = true)
            AiErrorKind.UNAVAILABLE, AiErrorKind.RATE_LIMITED -> AiFailure(message, canRetry = true)
            AiErrorKind.DAILY_LIMIT, AiErrorKind.REFUSED, AiErrorKind.INVALID_REQUEST -> AiFailure(message, canRetry = false)
            // Configuration problems are server-side; their text is never shown.
            AiErrorKind.CONFIGURATION -> AiFailure("The AI service isn't available right now. Please try again later.", canRetry = true)
        }
    }
}

/**
 * Crash and error reporting. Only active after the user opts in; reports carry no user id and
 * no health data. Android uses Firebase Crashlytics.
 */
interface CrashReporter {
    /** Turning it off also deletes reports that haven't been sent yet. */
    fun setEnabled(enabled: Boolean)
    /** For unexpected errors the app recovered from (shown to users as "Something went wrong"). */
    fun recordNonFatal(error: Throwable)
}

object NoCrashReporter : CrashReporter {
    override fun setEnabled(enabled: Boolean) = Unit
    override fun recordNonFatal(error: Throwable) = Unit
}
