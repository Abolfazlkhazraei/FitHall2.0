package com.example.fithall.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.example.fithall.model.ChatMessage
import com.example.fithall.model.ChatRole
import com.example.fithall.model.MacroTargets
import com.example.fithall.model.MealAnalysis
import com.example.fithall.model.NutritionPlan
import com.example.fithall.model.UserProfile
import com.example.fithall.model.WorkoutPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/**
 * Calls the Claude API directly from the device. Development only: the API key ships inside the
 * APK, so before release move these calls behind your own backend and implement [AiService]
 * against that instead.
 */
class ClaudeAiService(apiKey: String) : AiService {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String): MealAnalysis {
        val image = Base64ImageSource.builder()
            .data(Base64.getEncoder().encodeToString(jpegBytes))
            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
            .build()
        val params = baseParams(AiPrompts.MEAL_SYSTEM)
            .outputConfig(structured(AiPrompts.MEAL_SCHEMA, OutputConfig.Effort.MEDIUM))
            .addUserMessageOfBlockParams(
                listOf(
                    ContentBlockParam.ofImage(image),
                    ContentBlockParam.ofText(AiPrompts.mealUserText(userNote)),
                )
            )
            .build()
        return decode(send(params), MealAnalysis.serializer())
    }

    override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String): WorkoutPlan {
        val params = baseParams(AiPrompts.WORKOUT_SYSTEM)
            .outputConfig(structured(AiPrompts.WORKOUT_SCHEMA, OutputConfig.Effort.HIGH))
            .addUserMessage(AiPrompts.workoutUserText(profile, recentTraining))
            .build()
        return decode(send(params), WorkoutPlan.serializer())
    }

    override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets): NutritionPlan {
        val params = baseParams(AiPrompts.NUTRITION_SYSTEM)
            .outputConfig(structured(AiPrompts.NUTRITION_SCHEMA, OutputConfig.Effort.HIGH))
            .addUserMessage(AiPrompts.nutritionUserText(profile, targets))
            .build()
        return decode(send(params), NutritionPlan.serializer())
    }

    override suspend fun coachReply(context: String, history: List<ChatMessage>): String {
        val builder = baseParams("${AiPrompts.COACH_SYSTEM}\n\n<user_context>\n$context\n</user_context>")
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
        // The conversation must open with a user turn.
        history.dropWhile { it.role != ChatRole.USER }.forEach { message ->
            when (message.role) {
                ChatRole.USER -> builder.addUserMessage(message.text)
                ChatRole.ASSISTANT -> builder.addAssistantMessage(message.text)
            }
        }
        return send(builder.build())
    }

    private fun baseParams(system: String): MessageCreateParams.Builder =
        MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .system(system)
            // If a safety classifier declines a request, let the API retry it on its
            // recommended fallback model instead of failing.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

    private fun structured(schemaJson: String, effort: OutputConfig.Effort): OutputConfig {
        val schema = JsonOutputFormat.Schema.builder()
        json.parseToJsonElement(schemaJson).jsonObject.forEach { (key, value) ->
            schema.putAdditionalProperty(key, JsonValue.from(value.toPlain()))
        }
        return OutputConfig.builder()
            .effort(effort)
            .format(JsonOutputFormat.builder().schema(schema.build()).build())
            .build()
    }

    /** Sends the request off the main thread and returns the response text. */
    private suspend fun send(params: MessageCreateParams): String = withContext(Dispatchers.IO) {
        val message: Message = try {
            client.messages().create(params)
        } catch (e: UnauthorizedException) {
            throw AiException("The Anthropic API key is invalid. Check ANTHROPIC_API_KEY in local.properties.", e)
        } catch (e: PermissionDeniedException) {
            throw AiException("This API key doesn't have access to $MODEL.", e)
        } catch (e: RateLimitException) {
            throw AiException("Too many requests right now. Please wait a moment and try again.", e)
        } catch (e: InternalServerException) {
            throw AiException("The AI service is having trouble. Please try again shortly.", e)
        } catch (e: BadRequestException) {
            throw AiException("The AI request was rejected: ${e.message}", e)
        } catch (e: AnthropicServiceException) {
            throw AiException("AI error (${e.statusCode()}): ${e.message}", e)
        } catch (e: AnthropicIoException) {
            throw AiException("Couldn't reach the AI service. Check your internet connection.", e)
        }

        when (message.stopReason().orElse(null)) {
            StopReason.REFUSAL ->
                throw AiException("The AI declined this request. Try rephrasing it or using a different photo.")
            StopReason.MAX_TOKENS ->
                throw AiException("The AI response was cut off. Please try again.")
            else -> Unit
        }
        message.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
            .ifBlank { throw AiException("The AI returned an empty response. Please try again.") }
    }

    private fun <T> decode(text: String, serializer: KSerializer<T>): T = try {
        json.decodeFromString(serializer, text)
    } catch (e: Exception) {
        throw AiException("The AI response couldn't be read. Please try again.", e)
    }

    /** kotlinx JSON tree -> plain Kotlin values that the SDK's JsonValue.from understands. */
    private fun JsonElement.toPlain(): Any? = when (this) {
        is JsonNull -> null
        is JsonObject -> mapValues { it.value.toPlain() }
        is JsonArray -> map { it.toPlain() }
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            else -> doubleOrNull
        }
    }

    private companion object {
        const val MODEL = "claude-opus-5-5"
        const val MAX_TOKENS = 16_000L
    }
}
