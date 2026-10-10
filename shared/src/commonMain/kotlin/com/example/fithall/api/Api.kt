package com.example.fithall.api

import com.example.fithall.model.ChatMessage
import com.example.fithall.model.MacroTargets
import com.example.fithall.model.UserProfile
import kotlinx.serialization.Serializable

/**
 * The HTTP contract between the app and the FitHall server. All AI endpoints are POST with a
 * JSON body and need `Authorization: Bearer <token>` from [Api.REGISTER]. Responses are the
 * model classes (MealAnalysis, WorkoutPlan, ...) or an [ApiError] with a non-2xx status.
 */
object Api {
    const val REGISTER = "/v1/devices"
    const val MEAL_PHOTO = "/v1/meals/photo"
    const val MEAL_TEXT = "/v1/meals/text"
    const val WORKOUT_PLAN = "/v1/plans/workout"
    const val NUTRITION_PLAN = "/v1/plans/nutrition"
    const val COACH_REPLY = "/v1/coach/reply"
    const val CHECK_IN = "/v1/check-ins"
    const val HEALTH = "/health"

    // Input limits, enforced by the server. The app stays well inside them.
    const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    const val MAX_NOTE_CHARS = 500
    const val MAX_DESCRIPTION_CHARS = 2_000
    const val MAX_CONTEXT_CHARS = 20_000
    const val MAX_REPORT_CHARS = 30_000
    const val MAX_TRAINING_CHARS = 10_000
    const val MAX_CHAT_MESSAGES = 40
    const val MAX_CHAT_MESSAGE_CHARS = 4_000
}

@Serializable
data class RegisterResponse(val token: String)

@Serializable
data class MealPhotoRequest(val imageBase64: String, val note: String = "")

@Serializable
data class MealTextRequest(val description: String)

@Serializable
data class WorkoutPlanRequest(val profile: UserProfile, val recentTraining: String = "")

@Serializable
data class NutritionPlanRequest(val profile: UserProfile, val targets: MacroTargets)

@Serializable
data class CoachRequest(val context: String, val history: List<ChatMessage>)

@Serializable
data class CoachResponse(val reply: String)

@Serializable
data class CheckInRequest(val report: String)

@Serializable
data class ApiError(
    /** One of [ErrorCodes]. */
    val code: String,
    /** Safe to show to the user. */
    val message: String,
)

object ErrorCodes {
    const val INVALID_REQUEST = "invalid_request"
    const val UNAUTHORIZED = "unauthorized"
    const val RATE_LIMITED = "rate_limited"
    const val DAILY_LIMIT = "daily_limit"
    const val SERVICE_BUSY = "service_busy"
    const val REFUSED = "refused"
    const val AI_ERROR = "ai_error"
    const val PAYLOAD_TOO_LARGE = "payload_too_large"
}
