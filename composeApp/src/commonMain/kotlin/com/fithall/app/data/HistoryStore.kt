package com.fithall.app.data

import app.cash.sqldelight.db.SqlDriver
import com.fithall.app.db.FitHallDatabase
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.ChatRole
import com.fithall.app.model.MealEntry
import com.fithall.app.model.WeeklyCheckIn
import com.fithall.app.model.WeightEntry
import com.fithall.app.model.WorkoutLog
import kotlinx.serialization.json.Json

/**
 * The user's growing history. Every change touches one record, so cost stays flat as months of
 * meals and workouts accumulate (unlike rewriting whole lists).
 */
interface HistoryStore {
    /** Meals on or after [sinceDayKey] (yyyy-MM-dd), oldest first; all meals if null. */
    fun meals(sinceDayKey: String? = null): List<MealEntry>
    fun saveMeal(meal: MealEntry)
    fun deleteMeal(id: String)

    fun workouts(): List<WorkoutLog>
    fun saveWorkout(log: WorkoutLog)
    fun deleteWorkout(id: String)

    /** Oldest first, one per day. */
    fun weights(): List<WeightEntry>
    fun saveWeight(entry: WeightEntry)
    fun deleteWeight(dayKey: String)

    fun checkIns(): List<WeeklyCheckIn>
    /** Inserts or replaces by id. */
    fun saveCheckIn(checkIn: WeeklyCheckIn)

    fun chat(): List<ChatMessage>
    fun appendChat(message: ChatMessage)
    fun clearChat()

    fun clearAll()

    /** Runs [block] atomically: all of its writes happen, or none do. */
    fun transaction(block: () -> Unit)
}

/** SQLite via SQLDelight. The platform supplies the driver (Android, iOS or JDBC). */
class SqlHistoryStore(driver: SqlDriver) : HistoryStore {

    private val db = FitHallDatabase(driver)
    private val q = db.historyQueries
    private val json = Json { ignoreUnknownKeys = true }

    // Rows that no longer parse (e.g. after a model change without a migration) are skipped
    // rather than crashing the app.
    private inline fun <reified T> decode(rows: List<String>): List<T> =
        rows.mapNotNull { row -> runCatching { json.decodeFromString<T>(row) }.getOrNull() }

    override fun meals(sinceDayKey: String?): List<MealEntry> =
        decode(if (sinceDayKey == null) q.allMeals().executeAsList() else q.mealsSince(sinceDayKey).executeAsList())

    override fun saveMeal(meal: MealEntry) {
        q.upsertMeal(meal.id, meal.dayKey, meal.timestampMillis, json.encodeToString(MealEntry.serializer(), meal))
    }

    override fun deleteMeal(id: String) {
        q.deleteMeal(id)
    }

    override fun workouts(): List<WorkoutLog> = decode(q.allWorkouts().executeAsList())

    override fun saveWorkout(log: WorkoutLog) {
        q.upsertWorkout(log.id, log.dayKey, log.startedAtMillis, json.encodeToString(WorkoutLog.serializer(), log))
    }

    override fun deleteWorkout(id: String) {
        q.deleteWorkout(id)
    }

    override fun weights(): List<WeightEntry> =
        q.allWeights().executeAsList().map { WeightEntry(it.day_key, it.weight_kg) }

    override fun saveWeight(entry: WeightEntry) {
        q.upsertWeight(entry.dayKey, entry.weightKg)
    }

    override fun deleteWeight(dayKey: String) {
        q.deleteWeight(dayKey)
    }

    override fun checkIns(): List<WeeklyCheckIn> = decode(q.allCheckIns().executeAsList())

    override fun saveCheckIn(checkIn: WeeklyCheckIn) {
        q.upsertCheckIn(checkIn.id, checkIn.createdAtMillis, checkIn.status.name, json.encodeToString(WeeklyCheckIn.serializer(), checkIn))
    }

    override fun chat(): List<ChatMessage> =
        q.allChat().executeAsList().mapNotNull { row ->
            ChatRole.entries.firstOrNull { it.name == row.role }?.let { ChatMessage(it, row.text) }
        }

    override fun appendChat(message: ChatMessage) {
        q.insertChat(message.role.name, message.text)
    }

    override fun clearChat() {
        q.clearChat()
    }

    override fun clearAll() = db.transaction {
        q.deleteAllMeals()
        q.deleteAllWorkouts()
        q.deleteAllWeights()
        q.deleteAllCheckIns()
        q.clearChat()
    }

    override fun transaction(block: () -> Unit) = db.transaction { block() }
}

/** For tests and platforms without a database. Same behaviour, nothing persisted. */
class InMemoryHistoryStore : HistoryStore {
    private val meals = LinkedHashMap<String, MealEntry>()
    private val workouts = LinkedHashMap<String, WorkoutLog>()
    private val weights = mutableMapOf<String, Double>() // sortedMapOf is JVM-only
    private val checkIns = LinkedHashMap<String, WeeklyCheckIn>()
    private val chat = mutableListOf<ChatMessage>()

    override fun meals(sinceDayKey: String?) =
        meals.values.filter { sinceDayKey == null || it.dayKey >= sinceDayKey }.sortedBy { it.timestampMillis }
    override fun saveMeal(meal: MealEntry) { meals[meal.id] = meal }
    override fun deleteMeal(id: String) { meals.remove(id) }

    override fun workouts() = workouts.values.sortedBy { it.startedAtMillis }
    override fun saveWorkout(log: WorkoutLog) { workouts[log.id] = log }
    override fun deleteWorkout(id: String) { workouts.remove(id) }

    override fun weights() = weights.entries.sortedBy { it.key }.map { WeightEntry(it.key, it.value) }
    override fun saveWeight(entry: WeightEntry) { weights[entry.dayKey] = entry.weightKg }
    override fun deleteWeight(dayKey: String) { weights.remove(dayKey) }

    override fun checkIns() = checkIns.values.sortedBy { it.createdAtMillis }
    override fun saveCheckIn(checkIn: WeeklyCheckIn) { checkIns[checkIn.id] = checkIn }

    override fun chat() = chat.toList()
    override fun appendChat(message: ChatMessage) { chat += message }
    override fun clearChat() = chat.clear()

    override fun clearAll() {
        meals.clear(); workouts.clear(); weights.clear(); checkIns.clear(); chat.clear()
    }

    override fun transaction(block: () -> Unit) = block()
}
