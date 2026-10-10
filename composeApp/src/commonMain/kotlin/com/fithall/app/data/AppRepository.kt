package com.fithall.app.data

import com.fithall.app.model.ChatMessage
import com.fithall.app.model.MealEntry
import com.fithall.app.model.NutritionPlan
import com.fithall.app.model.TargetAdjustment
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WeeklyCheckIn
import com.fithall.app.model.WeightEntry
import com.fithall.app.model.WorkoutLog
import com.fithall.app.model.WorkoutPlan
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Minimal persistent string storage; each platform provides its own. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
    /** Removes everything, including the device token used by BackendAiService. */
    fun clear()
}

class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
    override fun clear() = map.clear()
}

/** What the user agreed to on the consent screen. A new policy [version] asks again. */
@Serializable
data class PrivacyConsent(val version: Int, val acceptedAtMillis: Long)

object PrivacyPolicy {
    /** Bump when the privacy policy or the data the app sends changes in a way users must re-accept. */
    const val VERSION = 1
}

/**
 * On-device persistence for everything the user creates. Small single values (profile, plans,
 * settings) live in [store] as JSON; the growing history lives in [history], one record per row.
 */
class AppRepository(
    private val store: KeyValueStore,
    val history: HistoryStore = InMemoryHistoryStore(),
) {

    private val json = Json { ignoreUnknownKeys = true }

    init {
        migrateListsToHistory()
    }

    var profile: UserProfile?
        get() = read(KEY_PROFILE, UserProfile.serializer())
        set(value) = write(KEY_PROFILE, UserProfile.serializer(), value)

    var workoutPlan: WorkoutPlan?
        get() = read(KEY_WORKOUT, WorkoutPlan.serializer())
        set(value) = write(KEY_WORKOUT, WorkoutPlan.serializer(), value)

    var nutritionPlan: NutritionPlan?
        get() = read(KEY_NUTRITION, NutritionPlan.serializer())
        set(value) = write(KEY_NUTRITION, NutritionPlan.serializer(), value)

    /** The session in progress, saved so it survives the app being closed mid-workout. */
    var activeWorkout: WorkoutLog?
        get() = read(KEY_ACTIVE_WORKOUT, WorkoutLog.serializer())
        set(value) = write(KEY_ACTIVE_WORKOUT, WorkoutLog.serializer(), value)

    var targetAdjustment: TargetAdjustment
        get() = read(KEY_TARGET_ADJUSTMENT, TargetAdjustment.serializer()) ?: TargetAdjustment()
        set(value) = write(KEY_TARGET_ADJUSTMENT, TargetAdjustment.serializer(), value)

    var consent: PrivacyConsent?
        get() = read(KEY_CONSENT, PrivacyConsent.serializer())
        set(value) = write(KEY_CONSENT, PrivacyConsent.serializer(), value)

    /** Deletes everything FitHall stores on this device. */
    fun clearAll() {
        history.clearAll()
        store.clear()
    }

    /** Everything the user has entered, as one JSON document they can keep (data portability). */
    fun exportJson(exportedAt: String): String {
        fun stored(key: String) = store.get(key)?.let { raw -> runCatching { json.parseToJsonElement(raw) }.getOrNull() }
        fun <T> list(items: List<T>, serializer: KSerializer<T>) =
            items.takeIf { it.isNotEmpty() }?.let { json.encodeToJsonElement(ListSerializer(serializer), it) }

        val sections = listOf(
            "profile" to stored(KEY_PROFILE),
            "targetAdjustment" to stored(KEY_TARGET_ADJUSTMENT),
            "meals" to list(history.meals(), MealEntry.serializer()),
            "bodyWeights" to list(history.weights(), WeightEntry.serializer()),
            "workouts" to list(history.workouts(), WorkoutLog.serializer()),
            "workoutInProgress" to stored(KEY_ACTIVE_WORKOUT),
            "workoutPlan" to stored(KEY_WORKOUT),
            "mealPlan" to stored(KEY_NUTRITION),
            "weeklyCheckIns" to list(history.checkIns(), WeeklyCheckIn.serializer()),
            "coachChat" to list(history.chat(), ChatMessage.serializer()),
            "privacyConsent" to stored(KEY_CONSENT),
        ).mapNotNull { (label, value) -> value?.let { label to it } }

        val document = JsonObject(
            mapOf("app" to JsonPrimitive("FitHall"), "exportedAt" to JsonPrimitive(exportedAt)) + sections
        )
        return prettyJson.encodeToString(JsonObject.serializer(), document)
    }

    private val prettyJson = Json { prettyPrint = true }

    /**
     * Earlier versions kept each history as one JSON list in [store]. Copy any such lists into
     * [history] in a single transaction, then remove them, so this runs once per install.
     */
    private fun migrateListsToHistory() {
        val legacy = listOf(KEY_MEALS, KEY_WORKOUT_LOGS, KEY_WEIGHTS, KEY_CHECK_INS, KEY_CHAT)
        if (legacy.none { store.get(it) != null }) return
        history.transaction {
            read(KEY_MEALS, ListSerializer(MealEntry.serializer()))?.forEach(history::saveMeal)
            read(KEY_WORKOUT_LOGS, ListSerializer(WorkoutLog.serializer()))?.forEach(history::saveWorkout)
            read(KEY_WEIGHTS, ListSerializer(WeightEntry.serializer()))?.forEach(history::saveWeight)
            read(KEY_CHECK_INS, ListSerializer(WeeklyCheckIn.serializer()))?.forEach(history::saveCheckIn)
            read(KEY_CHAT, ListSerializer(ChatMessage.serializer()))?.forEach(history::appendChat)
        }
        // Only after the copy has committed.
        legacy.forEach { store.put(it, null) }
    }

    private fun <T> read(key: String, serializer: KSerializer<T>): T? =
        store.get(key)?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }

    private fun <T> write(key: String, serializer: KSerializer<T>, value: T?) =
        store.put(key, value?.let { json.encodeToString(serializer, it) })

    private companion object {
        const val KEY_PROFILE = "profile"
        const val KEY_WORKOUT = "workout_plan"
        const val KEY_NUTRITION = "nutrition_plan"
        const val KEY_ACTIVE_WORKOUT = "active_workout"
        const val KEY_TARGET_ADJUSTMENT = "target_adjustment"
        const val KEY_CONSENT = "privacy_consent"

        // Legacy list keys, read only by the one-time migration.
        const val KEY_MEALS = "meals"
        const val KEY_CHAT = "coach_chat"
        const val KEY_WORKOUT_LOGS = "workout_logs"
        const val KEY_WEIGHTS = "body_weights"
        const val KEY_CHECK_INS = "check_ins"
    }
}
