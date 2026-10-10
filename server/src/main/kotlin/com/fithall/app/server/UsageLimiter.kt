package com.fithall.app.server

/**
 * Units are a rough cost measure: a meal estimate is cheap, a full plan or check-in is a long,
 * high-effort generation. Tune the defaults to your budget with the environment variables.
 */
enum class AiOperation(val units: Int) {
    MEAL_PHOTO(2),
    MEAL_TEXT(1),
    COACH_REPLY(1),
    NUTRITION_PLAN(3),
    WORKOUT_PLAN(4),
    CHECK_IN(4),
}

data class UsageLimits(
    /** Per device per UTC day. 40 units ≈ 10 photo scans, 10 coach messages and a couple of plans. */
    val deviceUnitsPerDay: Int = 40,
    val deviceRequestsPerMinute: Int = 10,
    /** Across all devices per UTC day: the hard cap on what the server will spend. */
    val globalUnitsPerDay: Int = 20_000,
    val registrationsPerIpPerHour: Int = 20,
    /** Daily units for devices that failed the integrity check in ENFORCE mode. 0 refuses them. */
    val limitedUnitsPerDay: Int = 8,
)

sealed interface Decision {
    data object Allowed : Decision
    data class Denied(val reason: Reason, val retryAfterSeconds: Long) : Decision
    enum class Reason { TOO_FAST, DEVICE_DAILY_LIMIT, GLOBAL_DAILY_LIMIT }
}

/**
 * In-memory limits. Good for a single server instance; with several instances, move these
 * counters to a shared store such as Redis so limits apply across all of them.
 */
class UsageLimiter(
    private val limits: UsageLimits,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class DeviceUsage(var day: Long, var units: Int, val recentRequests: ArrayDeque<Long> = ArrayDeque())

    private val devices = HashMap<String, DeviceUsage>()
    private val registrations = HashMap<String, ArrayDeque<Long>>()
    private var globalDay = -1L
    private var globalUnits = 0

    @Synchronized
    fun tryAcquire(deviceId: String, operation: AiOperation, tier: AccessTier = AccessTier.FULL): Decision {
        val now = clock()
        val day = now / DAY_MS
        if (day != globalDay) {
            globalDay = day
            globalUnits = 0
            devices.values.removeAll { it.day != day } // drop yesterday's counters
        }
        val usage = devices.getOrPut(deviceId) { DeviceUsage(day, 0) }
        if (usage.day != day) {
            usage.day = day
            usage.units = 0
        }
        usage.recentRequests.removeAll { now - it >= MINUTE_MS }

        val secondsToMidnight = ((day + 1) * DAY_MS - now + 999) / 1000
        return when {
            usage.recentRequests.size >= limits.deviceRequestsPerMinute -> {
                val retry = (MINUTE_MS - (now - usage.recentRequests.first()) + 999) / 1000
                Decision.Denied(Decision.Reason.TOO_FAST, retry.coerceAtLeast(1))
            }
            usage.units + operation.units > dailyUnits(tier) ->
                Decision.Denied(Decision.Reason.DEVICE_DAILY_LIMIT, secondsToMidnight)
            globalUnits + operation.units > limits.globalUnitsPerDay ->
                Decision.Denied(Decision.Reason.GLOBAL_DAILY_LIMIT, secondsToMidnight)
            else -> {
                usage.units += operation.units
                usage.recentRequests.addLast(now)
                globalUnits += operation.units
                Decision.Allowed
            }
        }
    }

    private fun dailyUnits(tier: AccessTier) = when (tier) {
        AccessTier.FULL -> limits.deviceUnitsPerDay
        AccessTier.LIMITED -> limits.limitedUnitsPerDay
    }

    /** Gives the units back, e.g. when the AI call failed through no fault of the user. */
    /** For tests and monitoring: how many IP addresses are currently remembered. */
    @Synchronized
    fun rememberedIpCount(): Int = registrations.size

    @Synchronized
    fun refund(deviceId: String, operation: AiOperation) {
        val day = clock() / DAY_MS
        devices[deviceId]?.takeIf { it.day == day }?.let { it.units = (it.units - operation.units).coerceAtLeast(0) }
        if (globalDay == day) globalUnits = (globalUnits - operation.units).coerceAtLeast(0)
    }

    /**
     * Sign-up IPs are held for at most an hour (the privacy policy says so): every call first
     * forgets anything older, for all IPs.
     */
    @Synchronized
    fun tryRegister(ip: String): Boolean {
        val now = clock()
        registrations.values.forEach { q -> q.removeAll { now - it >= HOUR_MS } }
        registrations.values.removeAll { it.isEmpty() }
        val recent = registrations.getOrPut(ip) { ArrayDeque() }
        if (recent.size >= limits.registrationsPerIpPerHour) return false
        recent.addLast(now)
        return true
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
    }
}
