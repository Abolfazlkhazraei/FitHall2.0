package com.fithall.app

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.data.SqlHistoryStore
import com.fithall.app.db.FitHallDatabase
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.ChatRole
import com.fithall.app.model.CheckInResult
import com.fithall.app.model.CheckInStatus
import com.fithall.app.model.ExerciseLog
import com.fithall.app.model.FoodItem
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.MealEntry
import com.fithall.app.model.SetLog
import com.fithall.app.model.WeeklyCheckIn
import com.fithall.app.model.WeightEntry
import com.fithall.app.model.WorkoutLog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the real SQLDelight schema and queries on SQLite (in memory, or a temp file). */
class SqlHistoryStoreTest {

    private fun driver(url: String = JdbcSqliteDriver.IN_MEMORY) =
        JdbcSqliteDriver(url).also { FitHallDatabase.Schema.create(it) }

    private fun meal(id: String, day: String, millis: Long) = MealEntry(
        id, millis, day, MealAnalysis("Meal $id", listOf(FoodItem("Rice", "1 cup", 200, 4, 45, 0)), "high", ""),
    )

    private fun workout(id: String, day: String, millis: Long) = WorkoutLog(
        id, day, millis, millis + 3_600_000, "Day 1",
        listOf(ExerciseLog("Squat", "3 × 5", 180, listOf(SetLog(100.0, 5, true)))),
    )

    private val result = CheckInResult("h", "s", emptyList(), emptyList(), 0, 0, "", emptyList(), emptyList())

    @Test
    fun mealsRoundTripAndFilterByDay() {
        val store = SqlHistoryStore(driver())
        store.saveMeal(meal("b", "2026-10-09", 2))
        store.saveMeal(meal("a", "2026-09-01", 1))
        store.saveMeal(meal("c", "2026-10-10", 3))

        assertEquals(listOf("a", "b", "c"), store.meals().map { it.id })
        assertEquals(listOf("b", "c"), store.meals(sinceDayKey = "2026-10-01").map { it.id })
        assertEquals(meal("c", "2026-10-10", 3), store.meals().last(), "the full record survives the round trip")

        store.deleteMeal("b")
        assertEquals(listOf("a", "c"), store.meals().map { it.id })
    }

    @Test
    fun workoutsWeightsCheckInsAndChat() {
        val store = SqlHistoryStore(driver())
        store.saveWorkout(workout("w2", "2026-10-08", 20))
        store.saveWorkout(workout("w1", "2026-10-01", 10))
        assertEquals(listOf("w1", "w2"), store.workouts().map { it.id })
        assertEquals(500.0, store.workouts().first().volumeKg)

        store.saveWeight(WeightEntry("2026-10-10", 80.0))
        store.saveWeight(WeightEntry("2026-10-10", 79.6)) // same day replaces
        store.saveWeight(WeightEntry("2026-10-03", 80.4))
        assertEquals(listOf(WeightEntry("2026-10-03", 80.4), WeightEntry("2026-10-10", 79.6)), store.weights())

        val pending = WeeklyCheckIn("c1", "2026-10-10", 5, result)
        store.saveCheckIn(pending)
        store.saveCheckIn(pending.copy(status = CheckInStatus.APPLIED, appliedCalorieChange = 150))
        assertEquals(CheckInStatus.APPLIED, store.checkIns().single().status)
        assertEquals(150, store.checkIns().single().appliedCalorieChange)

        store.appendChat(ChatMessage(ChatRole.USER, "hi"))
        store.appendChat(ChatMessage(ChatRole.ASSISTANT, "hello!"))
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), store.chat().map { it.role })
        store.clearChat()
        assertTrue(store.chat().isEmpty())
    }

    @Test
    fun clearAllEmptiesEveryTable() {
        val store = SqlHistoryStore(driver())
        store.saveMeal(meal("a", "2026-10-10", 1))
        store.saveWorkout(workout("w", "2026-10-10", 1))
        store.saveWeight(WeightEntry("2026-10-10", 80.0))
        store.saveCheckIn(WeeklyCheckIn("c", "2026-10-10", 1, result))
        store.appendChat(ChatMessage(ChatRole.USER, "hi"))

        store.clearAll()
        assertTrue(store.meals().isEmpty() && store.workouts().isEmpty() && store.weights().isEmpty())
        assertTrue(store.checkIns().isEmpty() && store.chat().isEmpty())
    }

    @Test
    fun transactionRollsBackOnFailure() {
        val store = SqlHistoryStore(driver())
        runCatching {
            store.transaction {
                store.saveMeal(meal("a", "2026-10-10", 1))
                error("boom")
            }
        }
        assertTrue(store.meals().isEmpty())
    }

    @Test
    fun dataSurvivesReopeningTheDatabaseFile() {
        val file = File.createTempFile("fithall", ".db").apply { delete(); deleteOnExit() }
        val url = "jdbc:sqlite:${file.absolutePath}"
        SqlHistoryStore(driver(url)).saveMeal(meal("a", "2026-10-10", 1))
        // Reopen without creating the schema again.
        assertEquals(listOf("a"), SqlHistoryStore(JdbcSqliteDriver(url)).meals().map { it.id })
    }

    @Test
    fun oldListDataIsMovedIntoTheDatabaseOnce() {
        // What earlier versions stored: each history as one JSON list in key-value storage.
        val kv = InMemoryKeyValueStore().apply {
            put("meals", """[{"id":"m1","timestampMillis":1,"dayKey":"2026-10-01","analysis":{"mealName":"Oats","items":[],"confidence":"high","notes":""}}]""")
            put("body_weights", """[{"dayKey":"2026-10-01","weightKg":81.2},{"dayKey":"2026-10-08","weightKg":80.5}]""")
            put("coach_chat", """[{"role":"USER","text":"hi"},{"role":"ASSISTANT","text":"hello"}]""")
            put("profile", """{"name":"Sam","age":30,"sex":"MALE","heightCm":180,"weightKg":80.5,"activityLevel":"MODERATE","experience":"BEGINNER","goal":"MAINTAIN","equipment":"FULL_GYM","trainingDaysPerWeek":3,"sessionMinutes":60}""")
        }
        val history = SqlHistoryStore(driver())

        val repo = AppRepository(kv, history)
        assertEquals(listOf("m1"), history.meals().map { it.id })
        assertEquals(2, history.weights().size)
        assertEquals(2, history.chat().size)
        assertNull(kv.get("meals"), "migrated lists are removed")
        assertNull(kv.get("body_weights"))
        assertEquals("Sam", repo.profile?.name, "single values stay where they were")

        // Running again (next launch) doesn't duplicate anything.
        AppRepository(kv, history)
        assertEquals(2, history.chat().size)
    }

    @Test
    fun exportIncludesDatabaseHistory() {
        val history = SqlHistoryStore(driver())
        val repo = AppRepository(InMemoryKeyValueStore(), history)
        history.saveMeal(meal("a", "2026-10-10", 1))
        history.saveWeight(WeightEntry("2026-10-10", 80.0))
        val exported = repo.exportJson("2026-10-10")
        assertTrue("\"meals\"" in exported && "Meal a" in exported, exported)
        assertTrue("\"bodyWeights\"" in exported)
        assertTrue("\"workouts\"" !in exported, "empty sections are left out")
    }
}
