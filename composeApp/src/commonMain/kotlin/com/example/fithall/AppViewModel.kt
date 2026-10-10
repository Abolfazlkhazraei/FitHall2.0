package com.example.fithall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fithall.ai.AiException
import com.example.fithall.ai.AiPrompts
import com.example.fithall.ai.AiService
import com.example.fithall.data.AppRepository
import com.example.fithall.domain.MacroCalculator
import com.example.fithall.model.ChatMessage
import com.example.fithall.model.ChatRole
import com.example.fithall.model.MacroTargets
import com.example.fithall.model.MealAnalysis
import com.example.fithall.model.MealEntry
import com.example.fithall.model.NutritionPlan
import com.example.fithall.model.UserProfile
import com.example.fithall.model.WorkoutPlan
import com.example.fithall.platform.nowMillis
import com.example.fithall.platform.todayKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface ScanState {
    data object Idle : ScanState
    data class Analyzing(val photo: ByteArray) : ScanState
    data class Result(val photo: ByteArray, val analysis: MealAnalysis) : ScanState
    data class Failed(val photo: ByteArray, val message: String) : ScanState
}

data class AppState(
    val profile: UserProfile? = null,
    val meals: List<MealEntry> = emptyList(),
    val workoutPlan: WorkoutPlan? = null,
    val nutritionPlan: NutritionPlan? = null,
    val chat: List<ChatMessage> = emptyList(),
    val today: String = todayKey(),
    val scan: ScanState = ScanState.Idle,
    val generatingWorkout: Boolean = false,
    val generatingNutrition: Boolean = false,
    val coachTyping: Boolean = false,
    val error: String? = null,
) {
    val targets: MacroTargets? get() = profile?.let(MacroCalculator::targets)
    val todaysMeals: List<MealEntry> get() = meals.filter { it.dayKey == today }
    val todaysTotals: MacroTargets
        get() = todaysMeals.fold(MacroTargets.ZERO) { acc, meal -> acc + meal.analysis.totals }
}

class AppViewModel(
    private val repository: AppRepository,
    private val ai: AiService,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AppState(
            profile = repository.profile,
            meals = repository.meals,
            workoutPlan = repository.workoutPlan,
            nutritionPlan = repository.nutritionPlan,
            chat = repository.chat,
        )
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun refreshDay() = _state.update { it.copy(today = todayKey()) }

    fun dismissError() = _state.update { it.copy(error = null) }

    // ---- Profile ----

    fun saveProfile(profile: UserProfile) {
        val previous = _state.value.profile
        repository.profile = profile
        _state.update { it.copy(profile = profile) }
        // Plans were built for the old profile; generate fresh ones for a brand-new user.
        if (previous == null) {
            generateWorkoutPlan()
            generateNutritionPlan()
        }
    }

    // ---- Meal scanning ----

    fun analyzeMeal(photo: ByteArray, note: String = "") {
        _state.update { it.copy(scan = ScanState.Analyzing(photo)) }
        viewModelScope.launch {
            val result = runAi { ai.analyzeMeal(photo, note) }
            _state.update {
                it.copy(
                    scan = result.fold(
                        onSuccess = { analysis -> ScanState.Result(photo, analysis) },
                        onFailure = { e -> ScanState.Failed(photo, e.userMessage()) },
                    )
                )
            }
        }
    }

    fun retryScan(note: String) {
        when (val scan = _state.value.scan) {
            is ScanState.Failed -> analyzeMeal(scan.photo, note)
            is ScanState.Result -> analyzeMeal(scan.photo, note)
            else -> Unit
        }
    }

    fun logScannedMeal(analysis: MealAnalysis) {
        val entry = MealEntry(
            id = "${nowMillis()}-${Random.nextInt(1_000_000)}",
            timestampMillis = nowMillis(),
            dayKey = todayKey(),
            analysis = analysis,
        )
        val meals = _state.value.meals + entry
        repository.meals = meals
        _state.update { it.copy(meals = meals, scan = ScanState.Idle, today = todayKey()) }
    }

    fun cancelScan() = _state.update { it.copy(scan = ScanState.Idle) }

    fun deleteMeal(id: String) {
        val meals = _state.value.meals.filterNot { it.id == id }
        repository.meals = meals
        _state.update { it.copy(meals = meals) }
    }

    // ---- Plans ----

    fun generateWorkoutPlan() {
        val profile = _state.value.profile ?: return
        if (_state.value.generatingWorkout) return
        _state.update { it.copy(generatingWorkout = true) }
        viewModelScope.launch {
            runAi { ai.generateWorkoutPlan(profile) }
                .onSuccess { plan ->
                    repository.workoutPlan = plan
                    _state.update { it.copy(workoutPlan = plan) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
            _state.update { it.copy(generatingWorkout = false) }
        }
    }

    fun generateNutritionPlan() {
        val profile = _state.value.profile ?: return
        val targets = _state.value.targets ?: return
        if (_state.value.generatingNutrition) return
        _state.update { it.copy(generatingNutrition = true) }
        viewModelScope.launch {
            runAi { ai.generateNutritionPlan(profile, targets) }
                .onSuccess { plan ->
                    repository.nutritionPlan = plan
                    _state.update { it.copy(nutritionPlan = plan) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
            _state.update { it.copy(generatingNutrition = false) }
        }
    }

    // ---- Coach ----

    fun sendCoachMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.coachTyping) return
        val history = _state.value.chat + ChatMessage(ChatRole.USER, trimmed)
        repository.chat = history
        _state.update { it.copy(chat = history, coachTyping = true) }
        viewModelScope.launch {
            runAi { ai.coachReply(coachContext(_state.value), history.takeLast(MAX_CHAT_TURNS)) }
                .onSuccess { reply ->
                    val updated = history + ChatMessage(ChatRole.ASSISTANT, reply)
                    repository.chat = updated
                    _state.update { it.copy(chat = updated) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
            _state.update { it.copy(coachTyping = false) }
        }
    }

    fun clearChat() {
        repository.chat = emptyList()
        _state.update { it.copy(chat = emptyList()) }
    }

    private fun coachContext(s: AppState): String = buildString {
        s.profile?.let { appendLine(AiPrompts.profileSummary(it)) }
        s.targets?.let {
            appendLine("Daily targets: ${it.calories} kcal, ${it.proteinG} g protein, ${it.carbsG} g carbs, ${it.fatG} g fat")
        }
        val eaten = s.todaysTotals
        appendLine("Eaten so far today: ${eaten.calories} kcal, ${eaten.proteinG} g protein, ${eaten.carbsG} g carbs, ${eaten.fatG} g fat")
        if (s.todaysMeals.isNotEmpty()) {
            appendLine("Today's meals: " + s.todaysMeals.joinToString("; ") { it.analysis.mealName })
        }
        s.workoutPlan?.let { plan ->
            appendLine("Current workout plan: ${plan.title}")
            plan.days.forEach { day ->
                appendLine("- ${day.dayName} (${day.focus}): " + day.exercises.joinToString { "${it.name} ${it.sets}x${it.reps}" })
            }
        }
        s.nutritionPlan?.let { appendLine("Current meal plan: ${it.summary}") }
    }.trim()

    private suspend fun <T> runAi(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun Throwable.userMessage(): String =
        if (this is AiException) message ?: "Something went wrong."
        else "Something went wrong: ${message ?: this::class.simpleName}"

    private companion object {
        const val MAX_CHAT_TURNS = 30
    }
}
