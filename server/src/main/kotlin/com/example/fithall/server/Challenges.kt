package com.example.fithall.server

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * One-time nonces for registration. A nonce is random bytes plus its issue time, signed, so the
 * server can check it was issued here and is recent without storing anything when issuing.
 * Used nonces are remembered until they expire so an integrity verdict can't be replayed.
 */
class Challenges(
    secret: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = 10 * 60_000,
) {
    // Separate key from the device tokens, derived from the same secret.
    private val key = SecretKeySpec(("challenge:$secret").toByteArray(), "HmacSHA256")
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val used = HashMap<String, Long>()

    /** Base64url without padding, as Play Integrity requires. */
    fun issue(): String {
        val body = ByteBuffer.allocate(BODY_BYTES)
            .put(ByteArray(RANDOM_BYTES).also(random::nextBytes))
            .putLong(clock())
            .array()
        return encoder.encodeToString(body + mac(body))
    }

    /** True exactly once for a genuine, unexpired nonce. */
    @Synchronized
    fun consume(nonce: String): Boolean {
        val bytes = runCatching { decoder.decode(nonce) }.getOrNull() ?: return false
        if (bytes.size != BODY_BYTES + MAC_BYTES) return false
        val body = bytes.copyOfRange(0, BODY_BYTES)
        if (!MessageDigest.isEqual(mac(body), bytes.copyOfRange(BODY_BYTES, bytes.size))) return false

        val now = clock()
        val issuedAt = ByteBuffer.wrap(body, RANDOM_BYTES, 8).long
        if (now - issuedAt !in 0..ttlMillis) return false

        used.entries.removeAll { now - it.value > ttlMillis }
        if (nonce in used) return false
        used[nonce] = issuedAt
        return true
    }

    private fun mac(body: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(body).copyOf(MAC_BYTES)

    private companion object {
        const val RANDOM_BYTES = 16
        const val BODY_BYTES = RANDOM_BYTES + 8
        const val MAC_BYTES = 16
    }
}
