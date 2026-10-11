package com.fithall.app.server

import com.fithall.app.ai.AiErrorKind
import com.fithall.app.ai.AiException
import com.fithall.app.ai.AiService
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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.request.receiveChannel
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.io.readString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.security.MessageDigest

fun main() {
    val config = ServerConfig.fromEnv()
    val costs = CostTracker(config.limits)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        fitHallModule(config, ClaudeAiService(config.anthropicApiKey, usage = costs), costs = costs)
    }.start(wait = true)
}

/** An error the client should see, with a message that is safe to show to users. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val retryAfterSeconds: Long? = null,
) : Exception(message)

private val log = LoggerFactory.getLogger("FitHall")

fun Application.fitHallModule(
    config: ServerConfig,
    ai: AiService,
    limiter: UsageLimiter = UsageLimiter(config.limits),
    tokens: DeviceTokens = DeviceTokens(config.tokenSecret),
    challenges: Challenges = Challenges(config.tokenSecret),
    costs: CostTracker = CostTracker(config.limits),
    // Created eagerly so a missing Google credential fails at startup, not on the first sign-up.
    integrity: IntegrityVerifier =
        if (config.integrity.mode == IntegrityMode.OFF) NoIntegrityVerifier else GooglePlayIntegrityVerifier(config.integrity),
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    if (config.trustProxy) install(XForwardedHeaders)
    // Logs method, path and status only. Request bodies contain meal photos and health data,
    // so never log them.
    install(CallLogging)
    install(StatusPages) {
        exception<ApiException> { call, e ->
            e.retryAfterSeconds?.let { call.response.header(HttpHeaders.RetryAfter, it.toString()) }
            call.respond(e.status, ApiError(e.code, e.message))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorCodes.INVALID_REQUEST, "The request was malformed."))
        }
        exception<Throwable> { call, e ->
            log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError(ErrorCodes.AI_ERROR, "Something went wrong. Please try again."))
        }
    }

    routing {
        get(Api.HEALTH) { call.respondText("ok") }

        // Spend report for you, not the app. Disabled unless ADMIN_TOKEN is set.
        get(ADMIN_USAGE_PATH) {
            val expected = config.adminToken
            val given = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
            if (expected == null) {
                call.respond(HttpStatusCode.NotFound, ApiError(ErrorCodes.INVALID_REQUEST, "Not found."))
            } else if (given == null || !MessageDigest.isEqual(expected.toByteArray(), given.toByteArray())) {
                throw ApiException(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "Admin token required.")
            } else {
                call.respond(costs.report())
            }
        }

        post(Api.CHALLENGE) {
            call.respond(ChallengeResponse(challenges.issue()))
        }

        post(Api.REGISTER) {
            if (!limiter.tryRegister(call.request.origin.remoteHost)) {
                throw ApiException(HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED, "Too many sign-ups from this network. Try again later.", 3600)
            }
            val request = receiveRegisterRequest(call)
            val tier = accessTier(request, config, challenges, integrity)
            call.respond(RegisterResponse(tokens.issue(tier), fullAccess = tier == AccessTier.FULL))
        }

        aiPost<MealPhotoRequest, _>(Api.MEAL_PHOTO, AiOperation.MEAL_PHOTO, limiter, tokens, maxBodyBytes = PHOTO_BODY_LIMIT) { req ->
            val bytes = Validation.jpeg(req.imageBase64)
            Validation.maxLength(req.note, Api.MAX_NOTE_CHARS, "note")
            charge { ai.analyzeMeal(bytes, req.note) }
        }
        aiPost<MealTextRequest, _>(Api.MEAL_TEXT, AiOperation.MEAL_TEXT, limiter, tokens) { req ->
            Validation.text(req.description, Api.MAX_DESCRIPTION_CHARS, "description")
            charge { ai.analyzeMealDescription(req.description.trim()) }
        }
        aiPost<WorkoutPlanRequest, _>(Api.WORKOUT_PLAN, AiOperation.WORKOUT_PLAN, limiter, tokens) { req ->
            Validation.profile(req.profile)
            Validation.maxLength(req.recentTraining, Api.MAX_TRAINING_CHARS, "recentTraining")
            charge { ai.generateWorkoutPlan(req.profile, req.recentTraining) }
        }
        aiPost<NutritionPlanRequest, _>(Api.NUTRITION_PLAN, AiOperation.NUTRITION_PLAN, limiter, tokens) { req ->
            Validation.profile(req.profile)
            Validation.targets(req.targets)
            charge { ai.generateNutritionPlan(req.profile, req.targets) }
        }
        aiPost<CoachRequest, _>(Api.COACH_REPLY, AiOperation.COACH_REPLY, limiter, tokens) { req ->
            Validation.maxLength(req.context, Api.MAX_CONTEXT_CHARS, "context")
            Validation.chat(req.history)
            charge { CoachResponse(ai.coachReply(req.context, req.history)) }
        }
        aiPost<CheckInRequest, _>(Api.CHECK_IN, AiOperation.CHECK_IN, limiter, tokens) { req ->
            Validation.text(req.report, Api.MAX_REPORT_CHARS, "report")
            charge { ai.weeklyCheckIn(req.report) }
        }
    }
}

const val ADMIN_USAGE_PATH = "/admin/usage"
private const val DEFAULT_BODY_LIMIT = 512L * 1024
// Base64 inflates the image by a third, plus a little JSON around it.
private const val PHOTO_BODY_LIMIT = Api.MAX_IMAGE_BYTES * 4L / 3 + 16 * 1024

/** Passed to each AI handler: validate first, then call [charge] to spend units and run the AI. */
class AiCall(
    private val device: DeviceIdentity,
    private val operation: AiOperation,
    private val limiter: UsageLimiter,
) {
    private val deviceId get() = device.deviceId

    suspend fun <T> charge(block: suspend () -> T): T {
        when (val decision = limiter.tryAcquire(deviceId, operation, device.tier)) {
            is Decision.Denied -> throw decision.toApiException()
            Decision.Allowed -> Unit
        }
        return try {
            withContext(AiRequestContext(deviceId, operation)) { block() }
        } catch (e: AiException) {
            // Don't bill the user's allowance for failures on our side.
            if (e.kind in setOf(AiErrorKind.UNAVAILABLE, AiErrorKind.RATE_LIMITED, AiErrorKind.CONFIGURATION)) {
                limiter.refund(deviceId, operation)
            }
            throw e.toApiException()
        }
    }
}

private inline fun <reified Req : Any, reified Res : Any> Route.aiPost(
    path: String,
    operation: AiOperation,
    limiter: UsageLimiter,
    tokens: DeviceTokens,
    maxBodyBytes: Long = DEFAULT_BODY_LIMIT,
    crossinline handle: suspend AiCall.(Req) -> Res,
) {
    post(path) {
        val device = authenticate(call, tokens)
        checkBodySize(call, maxBodyBytes)
        val request = call.receive<Req>()
        val result = AiCall(device, operation, limiter).handle(request)
        call.respond(result)
    }
}

private fun authenticate(call: ApplicationCall, tokens: DeviceTokens): DeviceIdentity {
    val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
    return token?.let(tokens::verify)
        ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "Please sign in again.")
}

private fun checkBodySize(call: ApplicationCall, maxBytes: Long) {
    val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        ?: throw ApiException(HttpStatusCode.LengthRequired, ErrorCodes.INVALID_REQUEST, "Content-Length is required.")
    if (length > maxBytes) {
        throw ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.PAYLOAD_TOO_LARGE, "That request is too large.")
    }
}

private fun Decision.Denied.toApiException() = when (reason) {
    Decision.Reason.TOO_FAST -> ApiException(
        HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED,
        "You're going a bit fast. Try again in $retryAfterSeconds seconds.", retryAfterSeconds,
    )
    Decision.Reason.DEVICE_DAILY_LIMIT -> ApiException(
        HttpStatusCode.TooManyRequests, ErrorCodes.DAILY_LIMIT,
        "You've used today's AI allowance. It resets at midnight UTC.", retryAfterSeconds,
    )
    Decision.Reason.GLOBAL_DAILY_LIMIT -> ApiException(
        HttpStatusCode.ServiceUnavailable, ErrorCodes.SERVICE_BUSY,
        "FitHall's AI is at capacity today. Please try again tomorrow.", retryAfterSeconds,
    )
}

private fun AiException.toApiException(): ApiException = when (kind) {
    AiErrorKind.INVALID_REQUEST -> {
        // Anthropic's reason names the offending parameter; it doesn't echo request content.
        log.warn("AI request rejected by Anthropic: {}", cause?.message ?: message)
        ApiException(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST, message ?: "Invalid request.")
    }
    AiErrorKind.REFUSED -> ApiException(HttpStatusCode.UnprocessableEntity, ErrorCodes.REFUSED, message ?: "The AI declined this request.")
    AiErrorKind.RATE_LIMITED -> ApiException(HttpStatusCode.ServiceUnavailable, ErrorCodes.SERVICE_BUSY, message ?: "The AI is busy.", 30)
    AiErrorKind.DAILY_LIMIT -> ApiException(HttpStatusCode.TooManyRequests, ErrorCodes.DAILY_LIMIT, message ?: "Daily limit reached.")
    AiErrorKind.CONFIGURATION -> {
        log.error("AI configuration error: {}", message, this)
        ApiException(HttpStatusCode.ServiceUnavailable, ErrorCodes.SERVICE_BUSY, "The AI service isn't available right now.")
    }
    AiErrorKind.UNAVAILABLE, AiErrorKind.NETWORK -> {
        log.warn("AI call failed: {}", message, cause)
        ApiException(HttpStatusCode.BadGateway, ErrorCodes.AI_ERROR, message ?: "The AI service had a problem. Please try again.")
    }
}

// ---- Registration ----

private const val REGISTER_BODY_LIMIT = 32L * 1024
private val registerJson = Json { ignoreUnknownKeys = true }

/** The body is optional: older clients and integrity-off setups may send none. */
private suspend fun receiveRegisterRequest(call: ApplicationCall): RegisterRequest {
    val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (length != null && length > REGISTER_BODY_LIMIT) {
        throw ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.PAYLOAD_TOO_LARGE, "That request is too large.")
    }
    val text = call.receiveChannel().readRemaining(REGISTER_BODY_LIMIT + 1).readString()
    if (text.length > REGISTER_BODY_LIMIT) {
        throw ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.PAYLOAD_TOO_LARGE, "That request is too large.")
    }
    if (text.isBlank()) return RegisterRequest()
    return try {
        registerJson.decodeFromString(RegisterRequest.serializer(), text)
    } catch (e: SerializationException) {
        throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST, "The request was malformed.")
    }
}

/** Decides what a registering device gets, according to the integrity mode. */
private suspend fun accessTier(
    request: RegisterRequest,
    config: ServerConfig,
    challenges: Challenges,
    integrity: IntegrityVerifier,
): AccessTier {
    val mode = config.integrity.mode
    if (mode == IntegrityMode.OFF) return AccessTier.FULL

    val verdict = checkIntegrity(request, challenges, integrity)
    // Failure reasons are verdict codes only, nothing that identifies the user.
    log.info("Registration integrity check: mode={} passed={} failures={}", mode, verdict.passed, verdict.failures)
    return when {
        mode == IntegrityMode.MONITOR || verdict.passed -> AccessTier.FULL
        config.limits.limitedUnitsPerDay > 0 -> AccessTier.LIMITED
        else -> throw ApiException(
            HttpStatusCode.Forbidden, ErrorCodes.INTEGRITY_FAILED,
            "FitHall couldn't verify this device. AI features need the app from Google Play on a certified Android device.",
        )
    }
}

private suspend fun checkIntegrity(request: RegisterRequest, challenges: Challenges, integrity: IntegrityVerifier): IntegrityVerdict {
    val nonce = request.nonce
    if (nonce == null || !challenges.consume(nonce)) return IntegrityVerdict.failed("missing_or_invalid_nonce")
    val token = request.integrityToken ?: return IntegrityVerdict.failed("no_token")
    return try {
        integrity.verify(token, nonce)
    } catch (e: Exception) {
        // A Google outage shouldn't lock everyone out: treat it as unverified (limited access).
        log.warn("Play Integrity verification failed", e)
        IntegrityVerdict.failed("verifier_error")
    }
}
