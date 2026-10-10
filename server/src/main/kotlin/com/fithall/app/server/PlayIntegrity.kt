package com.fithall.app.server

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

enum class IntegrityMode {
    /** No checks. For local development and before the app is on Google Play. */
    OFF,
    /** Check and log verdicts, but give every device full access. Use to see real-world pass rates first. */
    MONITOR,
    /** Devices that fail get the limited allowance (or are refused if that's 0). */
    ENFORCE,
}

data class IntegrityConfig(
    val mode: IntegrityMode = IntegrityMode.OFF,
    /** The app's application id; verdicts for any other package are rejected. */
    val packageName: String = "com.fithall.app",
    /** Require MEETS_DEVICE_INTEGRITY (a genuine, certified Android device). Otherwise MEETS_BASIC_INTEGRITY is enough. */
    val requireDeviceIntegrity: Boolean = true,
    /** Require the user to have installed the app from Google Play. Off by default for a free app. */
    val requireLicensed: Boolean = false,
    /** Optional SHA-256 digests (as Play reports them) of the app signing certificate. */
    val certificateDigests: Set<String> = emptySet(),
    val maxTokenAgeMillis: Long = 10 * 60_000,
)

data class IntegrityVerdict(val passed: Boolean, val failures: List<String>) {
    companion object {
        fun failed(reason: String) = IntegrityVerdict(false, listOf(reason))
    }
}

interface IntegrityVerifier {
    /** Decodes [integrityToken] and checks it was requested by this app for [expectedNonce]. */
    suspend fun verify(integrityToken: String, expectedNonce: String): IntegrityVerdict
}

// ---- Verdict payload (the parts we check) ----

@Serializable
data class IntegrityPayload(
    val requestDetails: RequestDetails? = null,
    val appIntegrity: AppIntegrity? = null,
    val deviceIntegrity: DeviceIntegrity? = null,
    val accountDetails: AccountDetails? = null,
) {
    @Serializable
    data class RequestDetails(val requestPackageName: String? = null, val nonce: String? = null, val timestampMillis: String? = null)

    @Serializable
    data class AppIntegrity(
        val appRecognitionVerdict: String? = null,
        val packageName: String? = null,
        val certificateSha256Digest: List<String> = emptyList(),
    )

    @Serializable
    data class DeviceIntegrity(val deviceRecognitionVerdict: List<String> = emptyList())

    @Serializable
    data class AccountDetails(val appLicensingVerdict: String? = null)
}

/** The rules, separate from the network call so they can be tested directly. */
object IntegrityRules {

    fun evaluate(payload: IntegrityPayload, expectedNonce: String, config: IntegrityConfig, now: Long): IntegrityVerdict {
        val failures = mutableListOf<String>()
        val request = payload.requestDetails

        if (request?.requestPackageName != config.packageName) failures += "wrong_package"
        // Play may return the nonce with or without base64 padding.
        if (request?.nonce?.trimEnd('=') != expectedNonce.trimEnd('=')) failures += "nonce_mismatch"
        val timestamp = request?.timestampMillis?.toLongOrNull()
        if (timestamp == null || now - timestamp > config.maxTokenAgeMillis || timestamp - now > 60_000) failures += "stale_token"

        val app = payload.appIntegrity
        if (app?.appRecognitionVerdict != "PLAY_RECOGNIZED") failures += "app_not_recognized:${app?.appRecognitionVerdict}"
        if (app?.packageName != null && app.packageName != config.packageName) failures += "wrong_app_package"
        if (config.certificateDigests.isNotEmpty() && app?.certificateSha256Digest.orEmpty().none { it in config.certificateDigests }) {
            failures += "wrong_certificate"
        }

        val device = payload.deviceIntegrity?.deviceRecognitionVerdict.orEmpty()
        val deviceOk = if (config.requireDeviceIntegrity) {
            "MEETS_DEVICE_INTEGRITY" in device || "MEETS_STRONG_INTEGRITY" in device
        } else {
            device.any { it in setOf("MEETS_BASIC_INTEGRITY", "MEETS_DEVICE_INTEGRITY", "MEETS_STRONG_INTEGRITY") }
        }
        if (!deviceOk) failures += "device_integrity:${device.joinToString("+").ifEmpty { "none" }}"

        if (config.requireLicensed && payload.accountDetails?.appLicensingVerdict != "LICENSED") {
            failures += "not_licensed:${payload.accountDetails?.appLicensingVerdict}"
        }
        return IntegrityVerdict(failures.isEmpty(), failures)
    }
}

/**
 * Asks Google to decode the token (playintegrity.googleapis.com), then applies [IntegrityRules].
 * Credentials come from Application Default Credentials, e.g. GOOGLE_APPLICATION_CREDENTIALS
 * pointing at a service account key from the Google Cloud project linked in the Play Console.
 */
class GooglePlayIntegrityVerifier(
    private val config: IntegrityConfig,
    private val credentials: GoogleCredentials = GoogleCredentials.getApplicationDefault().createScoped(SCOPE),
    private val clock: () -> Long = System::currentTimeMillis,
) : IntegrityVerifier {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class DecodeRequest(val integrityToken: String)

    @Serializable
    private data class DecodeResponse(val tokenPayloadExternal: JsonObject? = null)

    override suspend fun verify(integrityToken: String, expectedNonce: String): IntegrityVerdict = withContext(Dispatchers.IO) {
        val accessToken = synchronized(credentials) {
            credentials.refreshIfExpired()
            credentials.accessToken?.tokenValue ?: error("No Google access token for Play Integrity")
        }
        val request = HttpRequest.newBuilder(URI("https://playintegrity.googleapis.com/v1/${config.packageName}:decodeIntegrityToken"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(DecodeRequest.serializer(), DecodeRequest(integrityToken))))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 400) return@withContext IntegrityVerdict.failed("invalid_token")
        check(response.statusCode() == 200) { "Play Integrity decode returned HTTP ${response.statusCode()}" }

        val payload = json.decodeFromString(DecodeResponse.serializer(), response.body()).tokenPayloadExternal
            ?: return@withContext IntegrityVerdict.failed("empty_payload")
        IntegrityRules.evaluate(json.decodeFromJsonElement(IntegrityPayload.serializer(), payload), expectedNonce, config, clock())
    }

    private companion object {
        const val SCOPE = "https://www.googleapis.com/auth/playintegrity"
    }
}

/** Placeholder when integrity checks are off: never called. */
object NoIntegrityVerifier : IntegrityVerifier {
    override suspend fun verify(integrityToken: String, expectedNonce: String) = IntegrityVerdict.failed("checks_off")
}
