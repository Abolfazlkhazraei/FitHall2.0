package com.fithall.app.domain

import com.fithall.app.domain.ProgressCalculator.formatNumber
import com.fithall.app.model.FoodItem
import kotlin.math.roundToInt

/** The number at the start of a portion and what follows it: "1.5 cups" -> (1.5, "cups"). */
data class Amount(val value: Double, val unit: String)

/**
 * Editing portions. Macros scale linearly with the amount, always from the AI's original
 * estimate, so repeated edits don't accumulate rounding errors.
 */
object Portions {

    // "150 g", "1.5 cups", "1,5 tbsp", "1/2 cup", "1 1/2 slices", "~200 ml", "approx. 2 eggs"
    private val leadingAmount = Regex("""^\s*(?:~|about|approx\.?|approximately)?\s*(\d+(?:[.,]\d+)?)(?:\s+(\d+)/(\d+)|/(\d+))?\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(portion: String): Amount? {
        val match = leadingAmount.matchEntire(portion) ?: return null
        val (whole, fracTop, fracBottom, slashBottom, rest) = match.destructured
        var value = whole.replace(',', '.').toDoubleOrNull() ?: return null
        when {
            slashBottom.isNotEmpty() -> value /= slashBottom.toDouble().takeIf { it > 0 } ?: return null // "1/2"
            fracTop.isNotEmpty() -> value += fracTop.toDouble() / (fracBottom.toDouble().takeIf { it > 0 } ?: return null) // "1 1/2"
        }
        if (value <= 0) return null
        return Amount(value, rest.trim())
    }

    /** [original] scaled by [factor], with its portion text updated to match. */
    fun scale(original: FoodItem, factor: Double): FoodItem {
        if (factor == 1.0) return original
        val amount = parse(original.portion)
        val portion = if (amount != null) {
            listOf(formatNumber(amount.value * factor), amount.unit).filter { it.isNotEmpty() }.joinToString(" ")
        } else {
            "${formatNumber(factor)} × ${original.portion}"
        }
        return original.copy(
            portion = portion,
            calories = (original.calories * factor).roundToInt(),
            proteinG = (original.proteinG * factor).roundToInt(),
            carbsG = (original.carbsG * factor).roundToInt(),
            fatG = (original.fatG * factor).roundToInt(),
        )
    }

    /** The factor that turns [original]'s amount into [newValue], e.g. 150 g -> 200 g is 4/3. */
    fun factorFor(original: FoodItem, newValue: Double): Double? =
        parse(original.portion)?.let { newValue / it.value }
}
