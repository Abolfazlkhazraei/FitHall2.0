package com.fithall.app.data

import com.fithall.app.model.FoodItem
import com.fithall.app.model.MealAnalysis
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/** Calories and macros for some amount of a food. */
data class Nutrients(val calories: Double, val proteinG: Double, val carbsG: Double, val fatG: Double) {
    operator fun times(factor: Double) = Nutrients(calories * factor, proteinG * factor, carbsG * factor, fatG * factor)
}

/** A packaged product looked up by barcode. */
data class PackagedFood(
    val barcode: String,
    val name: String,
    val brand: String?,
    /** Per 100 g (or 100 ml for drinks); null when the database only has per-serving values. */
    val per100g: Nutrients?,
    val perServing: Nutrients?,
    /** e.g. "1 bar (40 g)". */
    val servingLabel: String?,
    val servingGrams: Double?,
) {
    val displayName: String get() = listOfNotNull(brand?.takeIf { it.isNotBlank() }, name).joinToString(" ")

    /** Nutrition for one serving, from per-serving data or derived from per-100 g and the serving weight. */
    val servingNutrients: Nutrients?
        get() = perServing ?: servingGrams?.let { grams -> per100g?.times(grams / 100) }

    val canUseGrams: Boolean get() = per100g != null
    val canUseServings: Boolean get() = servingNutrients != null
}

enum class PortionUnit { GRAMS, SERVINGS }

interface FoodDatabase {
    /** Null when the product isn't in the database. Throws on network errors. */
    suspend fun lookupBarcode(barcode: String): PackagedFood?
}

class UnavailableFoodDatabase(private val reason: String) : FoodDatabase {
    override suspend fun lookupBarcode(barcode: String): PackagedFood? = throw IllegalStateException(reason)
}

object PackagedFoods {

    /** Turns a chosen portion of a product into a loggable meal. */
    fun toMeal(food: PackagedFood, amount: Double, unit: PortionUnit): MealAnalysis? {
        val nutrients = when (unit) {
            PortionUnit.GRAMS -> food.per100g?.times(amount / 100)
            PortionUnit.SERVINGS -> food.servingNutrients?.times(amount)
        } ?: return null
        val portion = when (unit) {
            PortionUnit.GRAMS -> "${formatAmount(amount)} g"
            PortionUnit.SERVINGS -> "${formatAmount(amount)} × ${food.servingLabel ?: "serving"}"
        }
        return MealAnalysis(
            mealName = food.displayName,
            items = listOf(
                FoodItem(
                    name = food.displayName,
                    portion = portion,
                    calories = nutrients.calories.roundToInt(),
                    proteinG = nutrients.proteinG.roundToInt(),
                    carbsG = nutrients.carbsG.roundToInt(),
                    fatG = nutrients.fatG.roundToInt(),
                )
            ),
            confidence = "high",
            notes = "From the product label via Open Food Facts (barcode ${food.barcode}).",
        )
    }

    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

/** Parses Open Food Facts API v2 product responses. */
object OpenFoodFacts {

    const val FIELDS = "product_name,brands,nutriments,serving_size,serving_quantity"

    fun productUrl(barcode: String) =
        "https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=$FIELDS"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(barcode: String, body: String): PackagedFood? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        if (root["status"]?.jsonPrimitive?.intOrNull != 1) return null
        val product = root["product"] as? JsonObject ?: return null
        val nutriments = product["nutriments"] as? JsonObject

        fun number(key: String): Double? = (nutriments?.get(key))?.jsonPrimitive?.let {
            it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
        }

        fun nutrients(suffix: String): Nutrients? {
            val kcal = number("energy-kcal$suffix") ?: number("energy-kj$suffix")?.div(4.184)
            val protein = number("proteins$suffix")
            val carbs = number("carbohydrates$suffix")
            val fat = number("fat$suffix")
            if (kcal == null && protein == null && carbs == null && fat == null) return null
            return Nutrients(kcal ?: 0.0, protein ?: 0.0, carbs ?: 0.0, fat ?: 0.0)
        }

        fun text(key: String) = product[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        val per100g = nutrients("_100g")
        val perServing = nutrients("_serving")
        if (per100g == null && perServing == null) return null

        val servingGrams = product["serving_quantity"]?.jsonPrimitive?.let {
            it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull()
        }?.takeIf { it > 0 }

        return PackagedFood(
            barcode = barcode,
            name = text("product_name") ?: "Unnamed product",
            brand = text("brands")?.substringBefore(',')?.trim(),
            per100g = per100g,
            perServing = perServing,
            servingLabel = text("serving_size"),
            servingGrams = servingGrams,
        )
    }
}
