package com.fithall.app.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.time.Duration
import java.util.Base64

/** A signed-in FitHall account, from a verified Firebase ID token. */
data class FirebaseUser(val uid: String, val emailVerified: Boolean)

/** Signing keys by key id ("kid"). */
fun interface PublicKeySource {
    fun keys(): Map<String, PublicKey>
}

/**
 * Verifies Firebase Authentication ID tokens without the Admin SDK, following
 * https://firebase.google.com/docs/auth/admin/verify-id-tokens#verify_id_tokens_using_a_third-party_jwt_library:
 * RS256 signature by one of Google's current keys, audience and issuer of our project, not
 * expired, issued in the past, and a non-empty subject (the uid).
 */
class FirebaseTokenVerifier(
    private val projectId: String,
    private val keys: PublicKeySource = GoogleSecureTokenKeys(),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val decoder = Base64.getUrlDecoder()

    /** The account the token belongs to, or null if it isn't a valid, current token for this project. */
    fun verify(token: String): FirebaseUser? {
        val parts = token.split('.')
        if (parts.size != 3 || token.length > MAX_TOKEN_CHARS) return null
        return try {
            val header = parse(parts[0])
            val claims = parse(parts[1])
            if (header.string("alg") != "RS256") return null
            val key = header.string("kid")?.let { keys.keys()[it] } ?: return null
            val signature = Signature.getInstance("SHA256withRSA").apply {
                initVerify(key)
                update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
            }
            if (!signature.verify(decoder.decode(parts[2]))) return null

            val now = nowSeconds()
            val uid = claims.string("sub")?.takeIf { it.isNotEmpty() && it.length <= 128 } ?: return null
            val valid = claims.string("aud") == projectId &&
                claims.string("iss") == "https://securetoken.google.com/$projectId" &&
                (claims.long("exp") ?: 0) > now - CLOCK_SKEW_SECONDS &&
                (claims.long("iat") ?: Long.MAX_VALUE) <= now + CLOCK_SKEW_SECONDS &&
                (claims.long("auth_time") ?: Long.MAX_VALUE) <= now + CLOCK_SKEW_SECONDS
            if (!valid) return null
            FirebaseUser(uid, emailVerified = claims["email_verified"]?.jsonPrimitive?.booleanOrNull == true)
        } catch (e: Exception) {
            // Malformed tokens (bad base64, JSON, key) are simply invalid.
            null
        }
    }

    private fun parse(part: String): JsonObject = json.parseToJsonElement(String(decoder.decode(part))).jsonObject
    private fun JsonObject.string(name: String) = this[name]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(name: String) = this[name]?.jsonPrimitive?.longOrNull

    private companion object {
        const val MAX_TOKEN_CHARS = 8_192
        const val CLOCK_SKEW_SECONDS = 60L
    }
}

/**
 * Google's public certificates for Firebase ID tokens, cached for as long as the response's
 * Cache-Control max-age allows. If a refresh fails, the previous keys keep working.
 */
class GoogleSecureTokenKeys(
    private val url: String = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com",
) : PublicKeySource {
    private val log = LoggerFactory.getLogger(GoogleSecureTokenKeys::class.java)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: Map<String, PublicKey> = emptyMap()
    @Volatile private var expiresAtMillis = 0L

    override fun keys(): Map<String, PublicKey> {
        if (System.currentTimeMillis() < expiresAtMillis) return cached
        synchronized(this) {
            if (System.currentTimeMillis() < expiresAtMillis) return cached
            try {
                val response = client.send(
                    HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
                val factory = CertificateFactory.getInstance("X.509")
                cached = json.parseToJsonElement(response.body()).jsonObject.mapValues { (_, pem) ->
                    factory.generateCertificate(ByteArrayInputStream(pem.jsonPrimitive.content.toByteArray())).publicKey
                }
                val maxAge = response.headers().firstValue("Cache-Control").orElse("")
                    .let { Regex("max-age=(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 3600
                expiresAtMillis = System.currentTimeMillis() + maxAge * 1000
            } catch (e: Exception) {
                log.warn("Couldn't refresh Firebase signing keys: {}", e.message)
                // Try again in a minute rather than on every request.
                expiresAtMillis = System.currentTimeMillis() + 60_000
            }
            return cached
        }
    }
}
