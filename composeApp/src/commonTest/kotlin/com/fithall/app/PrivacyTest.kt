package com.fithall.app

import com.fithall.app.ai.AiPrompts
import com.fithall.app.ai.BackendAiService
import com.fithall.app.ai.UnavailableAiService
import com.fithall.app.api.Api
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.data.PrivacyPolicy
import com.fithall.app.model.ActivityLevel
import com.fithall.app.model.Equipment
import com.fithall.app.model.Experience
import com.fithall.app.model.Goal
import com.fithall.app.model.Sex
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WeightEntry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivacyTest {

    private val profile = UserProfile(
        "Jordan Example", 34, Sex.MALE, 182, 84.0, ActivityLevel.MODERATE, Experience.INTERMEDIATE,
        Goal.BUILD_MUSCLE, Equipment.FULL_GYM, 4, 60,
    )

    @Test
    fun consentIsRequiredUntilAcceptedAndRemembered() {
        val store = InMemoryKeyValueStore()
        val vm = AppViewModel(AppRepository(store), UnavailableAiService("offline"))
        assertTrue(vm.state.value.needsConsent)

        vm.acceptConsent()
        assertFalse(vm.state.value.needsConsent)
        assertEquals(PrivacyPolicy.VERSION, AppRepository(store).consent?.version)
        assertFalse(AppViewModel(AppRepository(store), UnavailableAiService("offline")).state.value.needsConsent)
    }

    @Test
    fun deletingRemovesEverythingIncludingTheDeviceToken() {
        val store = InMemoryKeyValueStore().apply { put("backend_device_token", "v2.abc.full.sig") }
        val repo = AppRepository(store).apply {
            this.profile = this@PrivacyTest.profile
            history.saveWeight(WeightEntry("2026-10-10", 84.0))
        }
        var cachesCleared = false
        val vm = AppViewModel(repo, UnavailableAiService("offline"), clearCaches = { cachesCleared = true })
        vm.acceptConsent()

        vm.deleteAllData()

        assertNull(store.get("backend_device_token"))
        assertNull(repo.profile)
        assertTrue(repo.history.weights().isEmpty())
        assertNull(repo.consent)
        assertTrue(cachesCleared)
        assertNull(vm.state.value.profile)
        assertTrue(vm.state.value.needsConsent, "deleting withdraws consent, so the app starts over")
    }

    @Test
    fun exportContainsTheUsersDataButNotTheDeviceToken() {
        val store = InMemoryKeyValueStore().apply { put("backend_device_token", "secret-token") }
        val repo = AppRepository(store).apply {
            this.profile = this@PrivacyTest.profile
            history.saveWeight(WeightEntry("2026-10-10", 84.0))
        }
        val exported = repo.exportJson("2026-10-10")
        val doc = Json.parseToJsonElement(exported).jsonObject
        assertEquals(setOf("app", "exportedAt", "profile", "bodyWeights"), doc.keys)
        assertTrue("Jordan Example" in exported)
        assertFalse("secret-token" in exported)
    }

    @Test
    fun theNameNeverLeavesTheDevice() = runTest {
        assertFalse("Jordan" in AiPrompts.profileSummary(profile))

        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            (request.body as? OutgoingContent.ByteArrayContent)?.let { bodies += it.bytes().decodeToString() }
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when (request.url.encodedPath) {
                Api.CHALLENGE -> respond("""{"nonce":"n"}""", headers = json)
                Api.REGISTER -> respond("""{"token":"t"}""", headers = json)
                else -> respond("""{"title":"P","summary":"","durationWeeks":4,"days":[],"progression":"","safetyNotes":""}""", headers = json)
            }
        }
        BackendAiService("https://api.example.com", InMemoryKeyValueStore(), engine = engine)
            .generateWorkoutPlan(profile, recentTraining = "")
        val planRequest = bodies.last()
        assertTrue(""""age":34""" in planRequest, planRequest)
        assertFalse("Jordan" in planRequest, planRequest)
    }
}
