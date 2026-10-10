package com.fithall.app.server

import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil

/**
 * Which device and operation an AI call belongs to. Set by the route (AiCall.charge) and read by
 * ClaudeAiService, so usage can be attributed without threading it through every method.
 */
class AiRequestContext(val deviceId: String, val operation: AiOperation) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AiRequestContext>
}

/** Token counts for one Claude response. Output includes thinking tokens, which are billed as output. */
data class UsageEvent(
    val operation: AiOperation?,
    val deviceId: String?,
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val durationMillis: Long,
    val stopReason: String,
)

fun interface UsageRecorder {
    fun record(event: UsageEvent)
}

/** USD per million tokens. */
data class ModelPrice(val input: Double, val output: Double, val cacheRead: Double, val cacheWrite: Double)

/**
 * Anthropic list prices. Check them against https://www.anthropic.com/pricing when you deploy
 * and whenever you change models: these are estimates for budgeting, not your invoice.
 * Cache writes are priced at 1.25x input (5-minute cache).
 */
object ModelPricing {
    private fun price(input: Double, output: Double, cacheRead: Double) = ModelPrice(input, output, cacheRead, input * 1.25)

    private val prices = mapOf(
        "claude-opus-5-5" to price(4.0, 20.0, 0.20),
        "claude-opus-5" to price(5.0, 25.0, 0.50),
        "claude-opus-4-8" to price(5.0, 25.0, 0.50),
        "claude-sonnet-5-5" to price(2.0, 10.0, 0.20),
        "claude-fable-5-1" to price(10.0, 50.0, 0.25),
        "claude-haiku-5-5" to price(0.10, 0.50, 0.01),
    )

    /** Longest matching prefix, so dated or suffixed model ids still match. Null if unknown. */
    fun forModel(model: String): ModelPrice? =
        prices.entries.filter { model.startsWith(it.key) }.maxByOrNull { it.key.length }?.value

    fun costUsd(event: UsageEvent, price: ModelPrice): Double =
        (event.inputTokens * price.input + event.outputTokens * price.output +
            event.cacheReadTokens * price.cacheRead + event.cacheWriteTokens * price.cacheWrite) / 1_000_000
}

// ---- Report returned by GET /admin/usage ----

@Serializable
data class UsageReport(
    val generatedAtMillis: Long,
    val deviceUnitsPerDay: Int,
    /** Average cost of one allowance unit over the reported days. */
    val avgCostPerUnitUsd: Double?,
    /** What one device costs per day if it uses its whole allowance at the average unit cost. */
    val maxCostPerDevicePerDayUsd: Double?,
    val days: List<DayUsage>,
)

@Serializable
data class DayUsage(
    /** UTC date, yyyy-MM-dd. */
    val day: String,
    val requests: Int,
    val costUsd: Double,
    val activeDevices: Int,
    /** Daily cost per active device. Only kept for the current day (devices aren't remembered longer). */
    val perDevice: Distribution?,
    /** Responses from a model missing from the price table; their cost is not included. */
    val unpricedRequests: Int,
    val operations: List<OperationUsage>,
)

@Serializable
data class OperationUsage(
    val operation: String,
    val requests: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
    val costUsd: Double,
    val avgCostUsd: Double,
    /** Cost divided by the units this operation charges, to compare against the allowance. */
    val costPerUnitUsd: Double,
)

@Serializable
data class Distribution(val p50: Double, val p90: Double, val max: Double)

/**
 * Logs each AI call and keeps daily totals in memory (the last [keepDays] UTC days). Logs and
 * totals contain no request content and no device ids; per-device costs exist only for the
 * current day, as numbers, to show how spend is spread across users.
 */
class CostTracker(
    private val limits: UsageLimits,
    private val clock: () -> Long = System::currentTimeMillis,
    private val keepDays: Int = 31,
) : UsageRecorder {

    private class OpStats {
        var requests = 0
        var input = 0L
        var output = 0L
        var cacheRead = 0L
        var cacheWrite = 0L
        var cost = 0.0
    }

    private class DayStats {
        val operations = sortedMapOf<AiOperation, OpStats>()
        var unpriced = 0
        var otherRequests = 0 // calls without an operation (shouldn't happen in production)
        var activeDevices = 0
    }

    private val days = sortedMapOf<Long, DayStats>()
    private var currentDay = -1L
    private val deviceCostsToday = HashMap<String, Double>()

    @Synchronized
    override fun record(event: UsageEvent) {
        val day = clock() / DAY_MS
        if (day != currentDay) {
            currentDay = day
            deviceCostsToday.clear()
            while (days.isNotEmpty() && days.firstKey() <= day - keepDays) days.remove(days.firstKey())
        }
        val stats = days.getOrPut(day) { DayStats() }
        val price = ModelPricing.forModel(event.model)
        val cost = price?.let { ModelPricing.costUsd(event, it) } ?: 0.0
        if (price == null) stats.unpriced++

        val operation = event.operation
        if (operation == null) {
            stats.otherRequests++
        } else {
            stats.operations.getOrPut(operation) { OpStats() }.apply {
                requests++
                input += event.inputTokens
                output += event.outputTokens
                cacheRead += event.cacheReadTokens
                cacheWrite += event.cacheWriteTokens
                this.cost += cost
            }
        }
        event.deviceId?.let { deviceCostsToday.merge(it, cost, Double::plus) }
        stats.activeDevices = deviceCostsToday.size

        log.info(
            "ai_usage op={} model={} input={} output={} cache_read={} cache_write={} cost_usd={} ms={} stop={}{}",
            operation ?: "none", event.model, event.inputTokens, event.outputTokens, event.cacheReadTokens,
            event.cacheWriteTokens, String.format(java.util.Locale.ROOT, "%.6f", cost), event.durationMillis, event.stopReason,
            if (price == null) " unpriced=true" else "",
        )
    }

    @Synchronized
    fun report(): UsageReport {
        var totalCost = 0.0
        var totalUnits = 0
        val dayReports = days.entries.reversed().map { (day, stats) ->
            val operations = stats.operations.map { (op, s) ->
                totalCost += s.cost
                totalUnits += s.requests * op.units
                OperationUsage(
                    operation = op.name,
                    requests = s.requests,
                    inputTokens = s.input,
                    outputTokens = s.output,
                    cacheReadTokens = s.cacheRead,
                    cacheWriteTokens = s.cacheWrite,
                    costUsd = s.cost.rounded(),
                    avgCostUsd = (s.cost / s.requests).rounded(),
                    costPerUnitUsd = (s.cost / (s.requests * op.units)).rounded(),
                )
            }
            DayUsage(
                day = utcDate(day),
                requests = stats.operations.values.sumOf { it.requests } + stats.otherRequests,
                costUsd = stats.operations.values.sumOf { it.cost }.rounded(),
                activeDevices = stats.activeDevices,
                perDevice = if (day == currentDay) distribution(deviceCostsToday.values) else null,
                unpricedRequests = stats.unpriced,
                operations = operations,
            )
        }
        val perUnit = if (totalUnits > 0) totalCost / totalUnits else null
        return UsageReport(
            generatedAtMillis = clock(),
            deviceUnitsPerDay = limits.deviceUnitsPerDay,
            avgCostPerUnitUsd = perUnit?.rounded(),
            maxCostPerDevicePerDayUsd = perUnit?.let { (it * limits.deviceUnitsPerDay).rounded() },
            days = dayReports,
        )
    }

    private fun distribution(values: Collection<Double>): Distribution? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        fun percentile(p: Double) = sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)]
        return Distribution(percentile(0.5).rounded(), percentile(0.9).rounded(), sorted.last().rounded())
    }

    private fun Double.rounded() = Math.round(this * 1_000_000) / 1_000_000.0

    private fun utcDate(day: Long): String = java.time.LocalDate.ofEpochDay(day).toString()

    private companion object {
        const val DAY_MS = 86_400_000L
        val log = LoggerFactory.getLogger("FitHall.usage")
    }
}
