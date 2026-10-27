package com.fithall.app.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerConfigTest {
    private val base = mapOf("ANTHROPIC_API_KEY" to "k", "TOKEN_SECRET" to "s".repeat(32))

    @Test
    fun planEffortDefaultsToMedium() {
        assertEquals("medium", ServerConfig.fromEnv(base).planEffort)
    }

    @Test
    fun planEffortCanBeSetAndIsValidated() {
        assertEquals("low", ServerConfig.fromEnv(base + ("PLAN_EFFORT" to " LOW ")).planEffort)
        assertFailsWith<IllegalArgumentException> { ServerConfig.fromEnv(base + ("PLAN_EFFORT" to "fast")) }
    }
}
