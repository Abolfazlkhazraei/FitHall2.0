package com.example.fithall.ai

import com.example.fithall.model.ChatMessage
import com.example.fithall.model.MacroTargets
import com.example.fithall.model.MealAnalysis
import com.example.fithall.model.NutritionPlan
import com.example.fithall.model.UserProfile
import com.example.fithall.model.WorkoutPlan

/**
 * Everything the app asks of the AI. The UI depends only on this interface, so the direct
 * Claude implementation can later be swapped for one that calls your own backend.
 */
interface AiService {
    suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String): MealAnalysis

    suspend fun generateWorkoutPlan(profile: UserProfile): WorkoutPlan

    suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets): NutritionPlan

    /** [context] is a plain-text snapshot of the user's profile, targets, intake and plans. */
    suspend fun coachReply(context: String, history: List<ChatMessage>): String
}

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Used on platforms that don't have an AI client wired up yet. */
class UnavailableAiService(private val reason: String) : AiService {
    override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String) = throw AiException(reason)
    override suspend fun generateWorkoutPlan(profile: UserProfile) = throw AiException(reason)
    override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets) =
        throw AiException(reason)
    override suspend fun coachReply(context: String, history: List<ChatMessage>) = throw AiException(reason)
}
