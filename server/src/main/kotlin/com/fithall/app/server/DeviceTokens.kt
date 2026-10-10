package com.fithall.app.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** How much a device may use. Decided once, at registration, by the integrity check. */
enum class AccessTier { FULL, LIMITED }

data class DeviceIdentity(val deviceId: String, val tier: AccessTier)

/**
 * Anonymous device tokens: "v2.<deviceId>.<tier>.<signature>", signed with HMAC-SHA256 so the
 * server can verify them, tier included, without a database. They identify an install for rate
 * limiting; they are not accounts. Older "v1" tokens are rejected, which makes those installs
 * re-register (and pass the integrity check) automatically.
 */
class DeviceTokens(secret: String) {

    private val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun issue(tier: AccessTier): String {
        val id = ByteArray(16).also(random::nextBytes)
        val payload = "$PREFIX.${encoder.encodeToString(id)}.${tier.name.lowercase()}"
        return "$payload.${sign(payload)}"
    }

    /** Returns who the token belongs to if it's genuine, otherwise null. */
    fun verify(token: String): DeviceIdentity? {
        val parts = token.split('.')
        if (parts.size != 4 || parts[0] != PREFIX || parts[1].isEmpty()) return null
        val payload = parts.take(3).joinToString(".")
        if (!MessageDigest.isEqual(sign(payload).toByteArray(), parts[3].toByteArray())) return null
        val tier = AccessTier.entries.firstOrNull { it.name.lowercase() == parts[2] } ?: return null
        return DeviceIdentity(parts[1], tier)
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return encoder.encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private companion object {
        const val PREFIX = "v2"
    }
}
