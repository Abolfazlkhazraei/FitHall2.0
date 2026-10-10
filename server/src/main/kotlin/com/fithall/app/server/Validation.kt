package com.fithall.app.server

import com.fithall.app.api.Api
import com.fithall.app.api.ErrorCodes
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.ChatRole
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.UserProfile
import io.ktor.http.HttpStatusCode
import java.util.Base64

/**
 * Checks requests before any AI units are spent. The app never sends anything outside these
 * bounds, so a failure here means a bug or a client that isn't the app.
 */
object Validation {

    fun jpeg(base64: String): ByteArray {
        if (base64.length > Api.MAX_IMAGE_BYTES * 4L / 3 + 4) invalid("The photo is too large.")
        val bytes = try {
            Base64.getDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            invalid("The photo couldn't be read.")
        }
        val isJpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        if (!isJpeg) invalid("The photo must be a JPEG.")
        return bytes
    }

    fun text(value: String, maxChars: Int, field: String) {
        if (value.isBlank()) invalid("$field is empty.")
        maxLength(value, maxChars, field)
    }

    fun maxLength(value: String, maxChars: Int, field: String) {
        if (value.length > maxChars) invalid("$field is too long.")
    }

    fun profile(p: UserProfile) {
        maxLength(p.name, 100, "name")
        maxLength(p.dietaryNotes, 500, "dietaryNotes")
        maxLength(p.injuries, 500, "injuries")
        if (p.age !in 13..100) invalid("age is out of range.")
        if (p.heightCm !in 100..250) invalid("heightCm is out of range.")
        if (p.weightKg !in 30.0..300.0) invalid("weightKg is out of range.")
        if (p.trainingDaysPerWeek !in 1..7) invalid("trainingDaysPerWeek is out of range.")
        if (p.sessionMinutes !in 10..180) invalid("sessionMinutes is out of range.")
    }

    fun targets(t: MacroTargets) {
        if (t.calories !in 800..6000) invalid("calories is out of range.")
        if (t.proteinG !in 0..500 || t.carbsG !in 0..1000 || t.fatG !in 0..400) invalid("macros are out of range.")
    }

    fun chat(history: List<ChatMessage>) {
        if (history.isEmpty()) invalid("history is empty.")
        if (history.size > Api.MAX_CHAT_MESSAGES) invalid("history is too long.")
        if (history.last().role != ChatRole.USER) invalid("The last message must be from the user.")
        history.forEach { text(it.text, Api.MAX_CHAT_MESSAGE_CHARS, "message") }
    }

    private fun invalid(message: String): Nothing =
        throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST, message)
}
