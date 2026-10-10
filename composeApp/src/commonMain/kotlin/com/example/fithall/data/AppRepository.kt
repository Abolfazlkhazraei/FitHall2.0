package com.example.fithall.data

import com.example.fithall.model.ChatMessage
import com.example.fithall.model.MealEntry
import com.example.fithall.model.NutritionPlan
import com.example.fithall.model.UserProfile
import com.example.fithall.model.WeightEntry
import com.example.fithall.model.WorkoutLog
import com.example.fithall.model.WorkoutPlan
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Minimal persistent string storage; each platform provides its own. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/** On-device persistence for everything the user creates. Values are stored as JSON. */
class AppRepository(private val store: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }

    var profile: UserProfile?
        get() = read(KEY_PROFILE, UserProfile.serializer())
        set(value) = write(KEY_PROFILE, UserProfile.serializer(), value)

    var meals: List<MealEntry>
        get() = read(KEY_MEALS, ListSerializer(MealEntry.serializer())) ?: emptyList()
        set(value) = write(KEY_MEALS, ListSerializer(MealEntry.serializer()), value)

    var workoutPlan: WorkoutPlan?
        get() = read(KEY_WORKOUT, WorkoutPlan.serializer())
        set(value) = write(KEY_WORKOUT, WorkoutPlan.serializer(), value)

    var nutritionPlan: NutritionPlan?
        get() = read(KEY_NUTRITION, NutritionPlan.serializer())
        set(value) = write(KEY_NUTRITION, NutritionPlan.serializer(), value)

    var chat: List<ChatMessage>
        get() = read(KEY_CHAT, ListSerializer(ChatMessage.serializer())) ?: emptyList()
        set(value) = write(KEY_CHAT, ListSerializer(ChatMessage.serializer()), value)

    var workoutLogs: List<WorkoutLog>
        get() = read(KEY_WORKOUT_LOGS, ListSerializer(WorkoutLog.serializer())) ?: emptyList()
        set(value) = write(KEY_WORKOUT_LOGS, ListSerializer(WorkoutLog.serializer()), value)

    /** The session in progress, saved so it survives the app being closed mid-workout. */
    var activeWorkout: WorkoutLog?
        get() = read(KEY_ACTIVE_WORKOUT, WorkoutLog.serializer())
        set(value) = write(KEY_ACTIVE_WORKOUT, WorkoutLog.serializer(), value)

    var weights: List<WeightEntry>
        get() = read(KEY_WEIGHTS, ListSerializer(WeightEntry.serializer())) ?: emptyList()
        set(value) = write(KEY_WEIGHTS, ListSerializer(WeightEntry.serializer()), value)

    private fun <T> read(key: String, serializer: KSerializer<T>): T? =
        store.get(key)?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }

    private fun <T> write(key: String, serializer: KSerializer<T>, value: T?) =
        store.put(key, value?.let { json.encodeToString(serializer, it) })

    private companion object {
        const val KEY_PROFILE = "profile"
        const val KEY_MEALS = "meals"
        const val KEY_WORKOUT = "workout_plan"
        const val KEY_NUTRITION = "nutrition_plan"
        const val KEY_CHAT = "coach_chat"
        const val KEY_WORKOUT_LOGS = "workout_logs"
        const val KEY_ACTIVE_WORKOUT = "active_workout"
        const val KEY_WEIGHTS = "body_weights"
    }
}
