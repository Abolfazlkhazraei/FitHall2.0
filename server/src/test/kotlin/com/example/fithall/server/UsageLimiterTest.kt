package com.example.fithall.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceTokensTest {
    private val tokens = DeviceTokens("x".repeat(32))

    @Test
    fun issuedTokensVerifyWithTheirTier() {
        val a = tokens.issue(AccessTier.FULL)
        val b = tokens.issue(AccessTier.LIMITED)
        assertNotEquals(a, b)
        assertEquals(DeviceIdentity(a.split('.')[1], AccessTier.FULL), tokens.verify(a))
        assertEquals(AccessTier.LIMITED, tokens.verify(b)?.tier)
    }

    @Test
    fun rejectsTamperedOrForeignTokens() {
        val token = tokens.issue(AccessTier.LIMITED)
        val (prefix, id, tier, sig) = token.split('.')
        assertNull(tokens.verify("$prefix.$id.full.$sig"), "upgrading the tier must break the signature")
        assertNull(tokens.verify("$prefix.${id}x.$tier.$sig"))
        assertNull(tokens.verify("$prefix.$id.$tier.${sig.reversed()}"))
        assertNull(tokens.verify("v1.$id.$sig"), "old-format tokens are rejected")
        assertNull(tokens.verify("garbage"))
        assertNull(DeviceTokens("y".repeat(32)).verify(token))
    }
}

class ChallengesTest {
    private var now = 1_000_000_000_000L
    private val challenges = Challenges("x".repeat(32), clock = { now })

    @Test
    fun nonceIsUsableOnceAndIsPlayCompatible() {
        val nonce = challenges.issue()
        assertTrue(Regex("^[A-Za-z0-9_-]{16,500}$").matches(nonce), nonce)
        assertTrue(challenges.consume(nonce))
        assertTrue(!challenges.consume(nonce), "replay must fail")
    }

    @Test
    fun rejectsExpiredForgedOrForeignNonces() {
        val nonce = challenges.issue()
        now += 11 * 60_000
        assertTrue(!challenges.consume(nonce))
        assertTrue(!challenges.consume(Challenges("y".repeat(32), clock = { now }).issue()))
        assertTrue(!challenges.consume("AAAA"))
        assertTrue(!challenges.consume("not base64 !!"))
    }
}

class UsageLimiterTest {
    private var now = 1_000_000_000_000L // some time on a fixed UTC day
    private val limits = UsageLimits(deviceUnitsPerDay = 10, deviceRequestsPerMinute = 3, globalUnitsPerDay = 15, registrationsPerIpPerHour = 2)
    private val limiter = UsageLimiter(limits) { now }

    @Test
    fun limitsRequestsPerMinute() {
        repeat(3) { assertEquals(Decision.Allowed, limiter.tryAcquire("a", AiOperation.MEAL_TEXT)) }
        val denied = assertIs<Decision.Denied>(limiter.tryAcquire("a", AiOperation.MEAL_TEXT))
        assertEquals(Decision.Reason.TOO_FAST, denied.reason)
        assertTrue(denied.retryAfterSeconds in 1..60)
        now += 60_000
        assertEquals(Decision.Allowed, limiter.tryAcquire("a", AiOperation.MEAL_TEXT))
    }

    @Test
    fun limitsDailyUnitsPerDeviceAndResetsNextDay() {
        // 4 + 4 = 8 units; another 4 would exceed 10.
        repeat(2) { assertEquals(Decision.Allowed, limiter.tryAcquire("a", AiOperation.WORKOUT_PLAN)) }
        now += 60_000
        val denied = assertIs<Decision.Denied>(limiter.tryAcquire("a", AiOperation.CHECK_IN))
        assertEquals(Decision.Reason.DEVICE_DAILY_LIMIT, denied.reason)
        // A cheaper call still fits.
        assertEquals(Decision.Allowed, limiter.tryAcquire("a", AiOperation.MEAL_PHOTO))
        now += 86_400_000
        assertEquals(Decision.Allowed, limiter.tryAcquire("a", AiOperation.CHECK_IN))
    }

    @Test
    fun globalCapAppliesAcrossDevices() {
        repeat(3) { i -> assertEquals(Decision.Allowed, limiter.tryAcquire("d$i", AiOperation.WORKOUT_PLAN)) } // 12
        val denied = assertIs<Decision.Denied>(limiter.tryAcquire("d9", AiOperation.WORKOUT_PLAN))
        assertEquals(Decision.Reason.GLOBAL_DAILY_LIMIT, denied.reason)
    }

    @Test
    fun refundReturnsUnits() {
        repeat(2) { limiter.tryAcquire("a", AiOperation.WORKOUT_PLAN) }
        limiter.refund("a", AiOperation.WORKOUT_PLAN)
        now += 60_000
        assertEquals(Decision.Allowed, limiter.tryAcquire("a", AiOperation.CHECK_IN))
    }

    @Test
    fun limitsRegistrationsPerIp() {
        assertTrue(limiter.tryRegister("1.2.3.4"))
        assertTrue(limiter.tryRegister("1.2.3.4"))
        assertTrue(!limiter.tryRegister("1.2.3.4"))
        assertTrue(limiter.tryRegister("5.6.7.8"))
        now += 3_600_000
        assertTrue(limiter.tryRegister("1.2.3.4"))
    }
}
