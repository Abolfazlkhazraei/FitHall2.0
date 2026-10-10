package com.fithall.app

import com.fithall.app.ai.AiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.FoodDatabase
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.data.Nutrients
import com.fithall.app.data.OpenFoodFacts
import com.fithall.app.data.PackagedFood
import com.fithall.app.data.PackagedFoods
import com.fithall.app.data.PortionUnit
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.FoodItem
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FoodLoggingTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // ---- Open Food Facts parsing ----

    private val nutellaJson = """
        {"code":"3017620422003","status":1,"status_verbose":"product found",
         "product":{"product_name":"Nutella","brands":"Ferrero,Nutella","serving_size":"15 g","serving_quantity":"15",
           "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9,
                         "energy-kcal_serving":80.8,"proteins_serving":0.945,"carbohydrates_serving":8.62,"fat_serving":4.63}}}
    """.trimIndent()

    @Test
    fun parsesProductWithPer100gAndServing() {
        val food = assertNotNull(OpenFoodFacts.parse("3017620422003", nutellaJson))
        assertEquals("Ferrero Nutella", food.displayName)
        assertEquals(539.0, food.per100g!!.calories)
        assertEquals(15.0, food.servingGrams)
        assertEquals("15 g", food.servingLabel)
        assertEquals(80.8, food.servingNutrients!!.calories)
    }

    @Test
    fun convertsKilojoulesWhenKcalIsMissing() {
        val json = """{"status":1,"product":{"product_name":"Oats","nutriments":{"energy-kj_100g":1569,"proteins_100g":13.5}}}"""
        val food = assertNotNull(OpenFoodFacts.parse("123456", json))
        assertTrue(abs(food.per100g!!.calories - 375.0) < 0.1)
        assertEquals(0.0, food.per100g.fatG)
        assertNull(food.servingNutrients)
    }

    @Test
    fun returnsNullForUnknownOrEmptyProducts() {
        assertNull(OpenFoodFacts.parse("1", """{"status":0,"status_verbose":"product not found"}"""))
        assertNull(OpenFoodFacts.parse("1", """{"status":1,"product":{"product_name":"Water","nutriments":{}}}"""))
        assertNull(OpenFoodFacts.parse("1", "not json"))
    }

    // ---- Portions ----

    private val bar = PackagedFood(
        barcode = "5000000000000", name = "Protein Bar", brand = "Acme",
        per100g = Nutrients(400.0, 30.0, 40.0, 15.0), perServing = null,
        servingLabel = "1 bar (60 g)", servingGrams = 60.0,
    )

    @Test
    fun portionsInGramsAndServings() {
        val grams = PackagedFoods.toMeal(bar, 30.0, PortionUnit.GRAMS)!!.items.single()
        assertEquals(120, grams.calories)
        assertEquals(9, grams.proteinG)
        assertEquals("30 g", grams.portion)

        // A serving is derived from per-100 g and the serving weight: 60 g.
        val servings = PackagedFoods.toMeal(bar, 2.0, PortionUnit.SERVINGS)!!.items.single()
        assertEquals(480, servings.calories)
        assertEquals("2 × 1 bar (60 g)", servings.portion)
    }

    @Test
    fun servingsUnavailableWithoutServingData() {
        val noServing = bar.copy(servingGrams = null, servingLabel = null)
        assertNull(PackagedFoods.toMeal(noServing, 1.0, PortionUnit.SERVINGS))
    }

    // ---- ViewModel flows ----

    private class FakeFoods(private val product: PackagedFood?) : FoodDatabase {
        var lookups = mutableListOf<String>()
        override suspend fun lookupBarcode(barcode: String): PackagedFood? { lookups += barcode; return product }
    }

    private class FakeAi : AiService {
        var described: String? = null
        override suspend fun analyzeMealDescription(description: String): MealAnalysis {
            described = description
            return MealAnalysis("Eggs on toast", listOf(FoodItem("Eggs", "2 large", 140, 12, 1, 10)), "medium", "")
        }
        override suspend fun analyzeMeal(jpegBytes: ByteArray, userNote: String) = error("unused")
        override suspend fun generateWorkoutPlan(profile: UserProfile, recentTraining: String) = error("unused")
        override suspend fun generateNutritionPlan(profile: UserProfile, targets: MacroTargets) = error("unused")
        override suspend fun coachReply(context: String, history: List<ChatMessage>) = error("unused")
        override suspend fun weeklyCheckIn(report: String): CheckInResult = error("unused")
    }

    @Test
    fun barcodeLookupThenLogPortion() {
        val repo = AppRepository(InMemoryKeyValueStore())
        val foods = FakeFoods(bar)
        val vm = AppViewModel(repo, FakeAi(), foods)

        vm.lookupBarcode(" 5000000000000\n")
        assertEquals(listOf("5000000000000"), foods.lookups)
        val found = assertIs<ScanState.ProductFound>(vm.state.value.scan)

        vm.logProduct(found.product, 1.0, PortionUnit.SERVINGS)
        assertEquals(ScanState.Idle, vm.state.value.scan)
        val logged = repo.meals.single().analysis
        assertEquals("Acme Protein Bar", logged.mealName)
        assertEquals(240, logged.totals.calories)
    }

    @Test
    fun unknownBarcodeFailsWithBarcodeSource() {
        val vm = AppViewModel(AppRepository(InMemoryKeyValueStore()), FakeAi(), FakeFoods(null))
        vm.lookupBarcode("12345678")
        val failed = assertIs<ScanState.Failed>(vm.state.value.scan)
        assertEquals(MealSource.Barcode("12345678"), failed.source)
    }

    @Test
    fun rejectsNonBarcodes() {
        val foods = FakeFoods(bar)
        val vm = AppViewModel(AppRepository(InMemoryKeyValueStore()), FakeAi(), foods)
        vm.lookupBarcode("https://example.com")
        assertIs<ScanState.Failed>(vm.state.value.scan)
        assertTrue(foods.lookups.isEmpty())
    }

    @Test
    fun describedMealIsAnalyzedAndCanBeReEdited() {
        val ai = FakeAi()
        val vm = AppViewModel(AppRepository(InMemoryKeyValueStore()), ai, FakeFoods(null))

        vm.analyzeDescription("  two eggs and toast ")
        assertEquals("two eggs and toast", ai.described)
        val result = assertIs<ScanState.Result>(vm.state.value.scan)
        assertEquals(MealSource.Description("two eggs and toast"), result.source)

        vm.retryScan("three eggs and toast")
        assertEquals("three eggs and toast", ai.described)
    }
}
