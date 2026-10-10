package com.fithall.app.ai

import com.fithall.app.model.ChatMessage
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.NutritionPlan
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WorkoutPlan

/**
 * Everything the app asks of the AI. The app implements it by calling the FitHall server
 * (BackendAiService); the server implements it by calling Claude (ClaudeAiService).
 */
interface AiService {
    suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String): MealAnalysis

    /** Estimates a meal from a spoken or typed description such as "two eggs and toast with butter". */
    suspend fun analyzeMealDescription(description: String): MealAnalysis

    /** [recentTraining] is a text summary of logged sessions; empty for a new user. */
    suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String): WorkoutPlan

    suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets): NutritionPlan

    /** [context] is a plain-text snapshot of the user's profile, targets, intake and plans. */
    suspend fun coachReply(context: String, history: List<ChatMessage>): String

    /** [report] is the factual weekly summary built by CheckInCalculator.buildReport. */
    suspend fun weeklyCheckIn(report: String): CheckInResult
}

enum class AiErrorKind {
    /** The request itself was invalid; retrying won't help. */
    INVALID_REQUEST,
    /** Too many requests, or a usage limit was reached. */
    RATE_LIMITED,
    /** The model declined the request. */
    REFUSED,
    /** The AI service failed or returned something unusable; retrying may help. */
    UNAVAILABLE,
    /** Credentials or configuration are wrong. Server-side only: never shown verbatim to users. */
    CONFIGURATION,
    /** The device couldn't reach the server. */
    NETWORK,
}

/** [message] is safe to show to the user, except for [AiErrorKind.CONFIGURATION]. */
class AiException(
    message: String,
    val kind: AiErrorKind = AiErrorKind.UNAVAILABLE,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Used on platforms that don't have an AI client wired up yet. */
class UnavailableAiService(private val reason: String) : AiService {
    override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String) = throw AiException(reason)
    override suspend fun analyzeMealDescription(description: String) = throw AiException(reason)
    override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String) = throw AiException(reason)
    override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets) =
        throw AiException(reason)
    override suspend fun coachReply(context: String, history: List<ChatMessage>) = throw AiException(reason)
    override suspend fun weeklyCheckIn(report: String) = throw AiException(reason)
}
