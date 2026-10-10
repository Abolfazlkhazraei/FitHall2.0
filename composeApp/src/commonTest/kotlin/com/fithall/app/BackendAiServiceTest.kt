package com.fithall.app

import com.fithall.app.ai.AiErrorKind
import com.fithall.app.ai.AiException
import com.fithall.app.ai.BackendAiService
import com.fithall.app.ai.DeviceAttestation
import com.fithall.app.ai.NoAttestation
import com.fithall.app.api.Api
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.ChatRole
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackendAiServiceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        store: InMemoryKeyValueStore = InMemoryKeyValueStore(),
        attestation: DeviceAttestation = NoAttestation,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = BackendAiService(
        "https://api.example.com/", store, attestation,
        MockEngine { request ->
            requests += request
            // Every registration starts with a challenge.
            if (request.url.encodedPath == Api.CHALLENGE) json("""{"nonce":"nonce-123"}""") else handler(request)
        },
    )

    private fun HttpRequestData.bodyText() = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, jsonHeaders)

    private val mealJson = """{"mealName":"Toast","items":[{"name":"Toast","portion":"1 slice","calories":80,"proteinG":3,"carbsG":15,"fatG":1}],"confidence":"high","notes":""}"""

    @Test
    fun registersOnceThenSendsTheToken() = runTest {
        var registrations = 0
        val ai = service { request ->
            when (request.url.encodedPath) {
                Api.REGISTER -> { registrations++; json("""{"token":"t$registrations"}""") }
                Api.MEAL_TEXT -> json(mealJson)
                else -> error("unexpected ${request.url}")
            }
        }
        assertEquals("Toast", ai.analyzeMealDescription("toast").mealName)
        assertEquals("Toast", ai.analyzeMealDescription("toast").mealName)
        assertEquals(1, registrations)
        val calls = requests.filter { it.url.encodedPath == Api.MEAL_TEXT }
        assertTrue(calls.all { it.headers[HttpHeaders.Authorization] == "Bearer t1" })
        assertEquals("https://api.example.com/v1/meals/text", calls.first().url.toString())
    }

    @Test
    fun reRegistersOnceWhenTheTokenIsRejected() = runTest {
        val store = InMemoryKeyValueStore().apply { put("backend_device_token", "old") }
        val ai = service(store) { request ->
            when {
                request.url.encodedPath == Api.REGISTER -> json("""{"token":"new"}""")
                request.headers[HttpHeaders.Authorization] == "Bearer old" ->
                    json("""{"code":"unauthorized","message":"Please sign in again."}""", HttpStatusCode.Unauthorized)
                else -> json("""{"reply":"Hi!"}""")
            }
        }
        assertEquals("Hi!", ai.coachReply("", listOf(ChatMessage(ChatRole.USER, "hello"))))
        assertEquals("new", store.get("backend_device_token"))
    }

    @Test
    fun serverErrorsBecomeReadableAiExceptions() = runTest {
        val ai = service { request ->
            if (request.url.encodedPath == Api.REGISTER) json("""{"token":"t"}""")
            else json(
                """{"code":"daily_limit","message":"You've used today's AI allowance. It resets at midnight UTC."}""",
                HttpStatusCode.TooManyRequests,
            )
        }
        val e = assertFailsWith<AiException> { ai.analyzeMealDescription("toast") }
        assertEquals(AiErrorKind.RATE_LIMITED, e.kind)
        assertEquals("You've used today's AI allowance. It resets at midnight UTC.", e.message)
    }

    @Test
    fun unreachableServerIsANetworkError() = runTest {
        val ai = service { throw IllegalStateException("connection refused") }
        val e = assertFailsWith<AiException> { ai.analyzeMealDescription("toast") }
        assertEquals(AiErrorKind.NETWORK, e.kind)
    }

    @Test
    fun registrationSendsTheAttestationForTheServerNonce() = runTest {
        var attestedNonce: String? = null
        val ai = service(attestation = { nonce -> attestedNonce = nonce; "play-token" }) { request ->
            if (request.url.encodedPath == Api.REGISTER) json("""{"token":"t","fullAccess":true}""") else json(mealJson)
        }
        ai.analyzeMealDescription("toast")
        assertEquals("nonce-123", attestedNonce)
        val register = requests.first { it.url.encodedPath == Api.REGISTER }.bodyText()
        assertTrue(""""nonce":"nonce-123"""" in register, register)
        assertTrue(""""integrityToken":"play-token"""" in register, register)
    }

    @Test
    fun attestationFailureStillRegistersWithoutAToken() = runTest {
        val ai = service(attestation = { error("Play services missing") }) { request ->
            if (request.url.encodedPath == Api.REGISTER) json("""{"token":"t","fullAccess":false}""") else json(mealJson)
        }
        assertEquals("Toast", ai.analyzeMealDescription("toast").mealName)
        val register = requests.first { it.url.encodedPath == Api.REGISTER }.bodyText()
        assertTrue(""""integrityToken"""" !in register || """"integrityToken":null""" in register, register)
    }

    @Test
    fun refusedDeviceGetsTheServerMessage() = runTest {
        val ai = service { json("""{"code":"integrity_failed","message":"FitHall couldn't verify this device."}""", HttpStatusCode.Forbidden) }
        val e = assertFailsWith<AiException> { ai.analyzeMealDescription("toast") }
        assertEquals("FitHall couldn't verify this device.", e.message)
        assertEquals(AiErrorKind.INVALID_REQUEST, e.kind)
    }

    @Test
    fun photosAreSentAsBase64() = runTest {
        val ai = service { request ->
            if (request.url.encodedPath == Api.REGISTER) json("""{"token":"t"}""") else json(mealJson)
        }
        ai.analyzeMeal(byteArrayOf(1, 2, 3), "no oil")
        val body = requests.last().bodyText()
        assertTrue(""""imageBase64":"AQID"""" in body, body)
        assertTrue(""""note":"no oil"""" in body, body)
    }
}
