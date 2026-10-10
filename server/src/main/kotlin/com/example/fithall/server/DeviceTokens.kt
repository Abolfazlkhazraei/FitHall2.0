package com.example.fithall.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Anonymous device tokens: "v1.<deviceId>.<signature>", signed with HMAC-SHA256. The server can
 * verify them without a database. They identify an install for rate limiting; they are not
 * accounts, and a determined abuser can register more (registration is rate limited per IP).
 */
class DeviceTokens(secret: String) {

    private val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun issue(): String {
        val id = ByteArray(16).also(random::nextBytes)
        val deviceId = encoder.encodeToString(id)
        return "$PREFIX.$deviceId.${sign(deviceId)}"
    }

    /** Returns the device id if the token is genuine, otherwise null. */
    fun verify(token: String): String? {
        val parts = token.split('.')
        if (parts.size != 3 || parts[0] != PREFIX || parts[1].isEmpty()) return null
        val expected = sign(parts[1]).toByteArray()
        return parts[1].takeIf { MessageDigest.isEqual(expected, parts[2].toByteArray()) }
    }

    private fun sign(deviceId: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return encoder.encodeToString(mac.doFinal("$PREFIX.$deviceId".toByteArray()))
    }

    private companion object {
        const val PREFIX = "v1"
    }
}
