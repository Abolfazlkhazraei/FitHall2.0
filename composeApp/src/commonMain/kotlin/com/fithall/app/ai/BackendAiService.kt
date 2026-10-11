package com.fithall.app.ai

import com.fithall.app.api.Api
import com.fithall.app.api.ApiError
import com.fithall.app.api.ChallengeResponse
import com.fithall.app.api.CheckInRequest
import com.fithall.app.api.CoachRequest
import com.fithall.app.api.CoachResponse
import com.fithall.app.api.ErrorCodes
import com.fithall.app.api.MealPhotoRequest
import com.fithall.app.api.MealTextRequest
import com.fithall.app.api.NutritionPlanRequest
import com.fithall.app.api.RegisterRequest
import com.fithall.app.api.RegisterResponse
import com.fithall.app.api.WorkoutPlanRequest
import com.fithall.app.data.KeyValueStore
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.NutritionPlan
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WorkoutPlan
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/**
 * Calls the FitHall server, which holds the Anthropic key. The install registers itself
 * anonymously on first use, proving it's the genuine app with [attestation], and keeps its
 * device token in [store].
 */
class BackendAiService(
    baseUrl: String,
    private val store: KeyValueStore,
    private val attestation: DeviceAttestation = NoAttestation,
    /** Only for tests; production picks the platform engine automatically. */
    engine: HttpClientEngine? = null,
) : AiService {

    private val baseUrl = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenLock = Mutex()

    private val client: HttpClient = run {
        val config: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                // Plans and check-ins are long, high-effort generations, and the server sends nothing
                // until the answer is ready. The socket timeout must be as long as the request timeout,
                // or the engine's default (10 s on OkHttp) cuts the request off while Claude is working.
                requestTimeoutMillis = AI_TIMEOUT_MILLIS
                socketTimeoutMillis = AI_TIMEOUT_MILLIS
                connectTimeoutMillis = 15_000
            }
        }
        if (engine != null) HttpClient(engine, config) else HttpClient(config)
    }

    override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String): MealAnalysis =
        post(Api.MEAL_PHOTO, MealPhotoRequest(Base64.encode(jpegBytes), userNote.take(Api.MAX_NOTE_CHARS)))

    override suspend fun analyzeMealDescription(description: String): MealAnalysis =
        post(Api.MEAL_TEXT, MealTextRequest(description.take(Api.MAX_DESCRIPTION_CHARS)))

    override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String): WorkoutPlan =
        post(Api.WORKOUT_PLAN, WorkoutPlanRequest(profile.withoutName(), recentTraining.take(Api.MAX_TRAINING_CHARS)))

    override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets): NutritionPlan =
        post(Api.NUTRITION_PLAN, NutritionPlanRequest(profile.withoutName(), targets))

    override suspend fun coachReply(context: String, history: List<ChatMessage>): String {
        val trimmed = history.takeLast(Api.MAX_CHAT_MESSAGES).map { it.copy(text = it.text.take(Api.MAX_CHAT_MESSAGE_CHARS)) }
        return post<CoachRequest, CoachResponse>(Api.COACH_REPLY, CoachRequest(context.take(Api.MAX_CONTEXT_CHARS), trimmed)).reply
    }

    override suspend fun weeklyCheckIn(report: String): CheckInResult =
        post(Api.CHECK_IN, CheckInRequest(report.take(Api.MAX_REPORT_CHARS)))

    private suspend inline fun <reified Req : Any, reified Res : Any> post(path: String, body: Req): Res {
        try {
            var token = currentToken()
            var response = send(path, token, body)
            if (response.status == HttpStatusCode.Unauthorized) {
                // The server's signing secret changed, or the token was lost: register again once.
                token = newToken(stale = token)
                response = send(path, token, body)
            }
            if (!response.status.isSuccess()) throw response.toAiException()
            return response.body()
        } catch (e: AiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            throw AiException("Got an unexpected response from FitHall's servers. Please try again.", AiErrorKind.UNAVAILABLE, e)
        } catch (e: ContentConvertException) {
            throw AiException("Got an unexpected response from FitHall's servers. Please try again.", AiErrorKind.UNAVAILABLE, e)
        } catch (e: Exception) {
            throw AiException("Couldn't reach FitHall's servers. Check your internet connection and try again.", AiErrorKind.NETWORK, e)
        }
    }

    private suspend inline fun <reified Req : Any> send(path: String, token: String, body: Req): HttpResponse =
        client.post(baseUrl + path) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun currentToken(): String = store.get(TOKEN_KEY) ?: newToken(stale = null)

    /** Registers a new token unless another request already replaced [stale]. */
    private suspend fun newToken(stale: String?): String = tokenLock.withLock {
        store.get(TOKEN_KEY)?.takeIf { it != stale }?.let { return it }
        val challenge = client.post(baseUrl + Api.CHALLENGE)
        if (!challenge.status.isSuccess()) throw challenge.toAiException()
        val nonce = challenge.body<ChallengeResponse>().nonce
        val integrityToken = try {
            attestation.attest(nonce)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // The server decides what an unattested device may do.
        }
        val response = client.post(baseUrl + Api.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(nonce, integrityToken))
        }
        if (!response.status.isSuccess()) throw response.toAiException()
        val token = response.body<RegisterResponse>().token
        store.put(TOKEN_KEY, token)
        token
    }

    private suspend fun HttpResponse.toAiException(): AiException {
        val error = runCatching { body<ApiError>() }.getOrNull()
        val kind = when (error?.code) {
            ErrorCodes.DAILY_LIMIT -> AiErrorKind.DAILY_LIMIT
            ErrorCodes.RATE_LIMITED, ErrorCodes.SERVICE_BUSY -> AiErrorKind.RATE_LIMITED
            ErrorCodes.REFUSED -> AiErrorKind.REFUSED
            ErrorCodes.INVALID_REQUEST, ErrorCodes.PAYLOAD_TOO_LARGE, ErrorCodes.INTEGRITY_FAILED -> AiErrorKind.INVALID_REQUEST
            else -> AiErrorKind.UNAVAILABLE
        }
        return AiException(error?.message ?: "FitHall's servers had a problem (${status.value}). Please try again.", kind)
    }

    /** The AI doesn't need the user's name, so it never leaves the device. */
    private fun UserProfile.withoutName() = copy(name = "")

    private companion object {
        const val TOKEN_KEY = "backend_device_token"
        const val AI_TIMEOUT_MILLIS = 180_000L
    }
}
