package com.example.fithall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fithall.ai.AiException
import com.example.fithall.ai.AiPrompts
import com.example.fithall.ai.AiService
import com.example.fithall.data.AppRepository
import com.example.fithall.data.FoodDatabase
import com.example.fithall.data.PackagedFood
import com.example.fithall.data.PackagedFoods
import com.example.fithall.data.PortionUnit
import com.example.fithall.data.UnavailableFoodDatabase
import com.example.fithall.domain.CheckInCalculator
import com.example.fithall.domain.MacroCalculator
import com.example.fithall.domain.ProgressCalculator
import com.example.fithall.model.ChatMessage
import com.example.fithall.model.ChatRole
import com.example.fithall.model.CheckInStatus
import com.example.fithall.model.ExerciseLog
import com.example.fithall.model.MacroTargets
import com.example.fithall.model.MealAnalysis
import com.example.fithall.model.MealEntry
import com.example.fithall.model.NutritionPlan
import com.example.fithall.model.SetLog
import com.example.fithall.model.TargetAdjustment
import com.example.fithall.model.UserProfile
import com.example.fithall.model.WeeklyCheckIn
import com.example.fithall.model.WeightEntry
import com.example.fithall.model.WorkoutDay
import com.example.fithall.model.WorkoutLog
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

/** Where a meal being logged came from. */
sealed interface MealSource {
    data class Photo(val jpeg: ByteArray) : MealSource
    data class Description(val text: String) : MealSource
    data class Barcode(val code: String) : MealSource
}

sealed interface ScanState {
    data object Idle : ScanState
    data class Analyzing(val source: MealSource) : ScanState
    data class Result(val source: MealSource, val analysis: MealAnalysis) : ScanState
    data class Failed(val source: MealSource, val message: String) : ScanState
    /** A barcode matched a product; the user picks a portion next. */
    data class ProductFound(val product: PackagedFood) : ScanState
}

data class AppState(
    val profile: UserProfile? = null,
    val meals: List<MealEntry> = emptyList(),
    val workoutPlan: WorkoutPlan? = null,
    val nutritionPlan: NutritionPlan? = null,
    val chat: List<ChatMessage> = emptyList(),
    val workoutLogs: List<WorkoutLog> = emptyList(),
    val activeWorkout: WorkoutLog? = null,
    val weights: List<WeightEntry> = emptyList(),
    val checkIns: List<WeeklyCheckIn> = emptyList(),
    val adjustment: TargetAdjustment = TargetAdjustment(),
    val today: String = todayKey(),
    val scan: ScanState = ScanState.Idle,
    val generatingWorkout: Boolean = false,
    val generatingNutrition: Boolean = false,
    val coachTyping: Boolean = false,
    val runningCheckIn: Boolean = false,
    val error: String? = null,
) {
    val targets: MacroTargets? get() = profile?.let { MacroCalculator.targets(it, adjustment) }
    val pendingCheckIn: WeeklyCheckIn? get() = checkIns.lastOrNull { it.status == CheckInStatus.PENDING }
    val checkInDue: Boolean
        get() = pendingCheckIn == null && CheckInCalculator.isDue(checkIns, today, workoutLogs, weights, meals)
    val todaysMeals: List<MealEntry> get() = meals.filter { it.dayKey == today }
    val todaysTotals: MacroTargets
        get() = todaysMeals.fold(MacroTargets.ZERO) { acc, meal -> acc + meal.analysis.totals }
}

class AppViewModel(
    private val repository: AppRepository,
    private val ai: AiService,
    private val foods: FoodDatabase = UnavailableFoodDatabase("Barcode lookup is not available on this platform."),
) : ViewModel() {

    private val _state = MutableStateFlow(
        AppState(
            profile = repository.profile,
            meals = repository.meals,
            workoutPlan = repository.workoutPlan,
            nutritionPlan = repository.nutritionPlan,
            chat = repository.chat,
            workoutLogs = repository.workoutLogs,
            activeWorkout = repository.activeWorkout,
            weights = repository.weights,
            checkIns = repository.checkIns,
            adjustment = repository.targetAdjustment,
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

    fun analyzeMeal(photo: ByteArray, note: String = "") =
        analyze(MealSource.Photo(photo)) { ai.analyzeMeal(photo, note) }

    fun analyzeDescription(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        analyze(MealSource.Description(trimmed)) { ai.analyzeMealDescription(trimmed) }
    }

    private fun analyze(source: MealSource, call: suspend () -> MealAnalysis) {
        _state.update { it.copy(scan = ScanState.Analyzing(source)) }
        viewModelScope.launch {
            val result = runAi(call)
            _state.update {
                it.copy(
                    scan = result.fold(
                        onSuccess = { analysis -> ScanState.Result(source, analysis) },
                        onFailure = { e -> ScanState.Failed(source, e.userMessage()) },
                    )
                )
            }
        }
    }

    /**
     * Re-runs the current scan. For photos, [input] is a correction note; for descriptions it
     * replaces the description; for barcodes the lookup is simply retried.
     */
    fun retryScan(input: String) {
        val source = when (val scan = _state.value.scan) {
            is ScanState.Failed -> scan.source
            is ScanState.Result -> scan.source
            else -> return
        }
        when (source) {
            is MealSource.Photo -> analyzeMeal(source.jpeg, input)
            is MealSource.Description -> analyzeDescription(input.ifBlank { source.text })
            is MealSource.Barcode -> lookupBarcode(source.code)
        }
    }

    fun lookupBarcode(raw: String) {
        val code = raw.filter { it.isDigit() }
        val source = MealSource.Barcode(code)
        if (code.length !in 6..14) {
            _state.update { it.copy(scan = ScanState.Failed(source, "That doesn't look like a food barcode. Try scanning again.")) }
            return
        }
        _state.update { it.copy(scan = ScanState.Analyzing(source)) }
        viewModelScope.launch {
            val result = runAi { foods.lookupBarcode(code) }
            _state.update {
                it.copy(
                    scan = result.fold(
                        onSuccess = { product ->
                            if (product != null) ScanState.ProductFound(product)
                            else ScanState.Failed(source, "This product isn't in the Open Food Facts database yet. Describe it instead, or snap a photo of it.")
                        },
                        onFailure = { ScanState.Failed(source, "Couldn't look up the barcode. Check your internet connection and try again.") },
                    )
                )
            }
        }
    }

    fun logProduct(product: PackagedFood, amount: Double, unit: PortionUnit) {
        val meal = PackagedFoods.toMeal(product, amount, unit) ?: return
        logScannedMeal(meal)
    }

    fun logScannedMeal(analysis: MealAnalysis) {
        val entry = MealEntry(
            id = newId(),
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
            val history = ProgressCalculator.historySummary(_state.value.workoutLogs)
            runAi { ai.generateWorkoutPlan(profile, history) }
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

    // ---- Workout sessions ----

    fun startWorkout(day: WorkoutDay) {
        if (_state.value.activeWorkout != null) return
        val session = WorkoutLog(
            id = newId(),
            dayKey = todayKey(),
            startedAtMillis = nowMillis(),
            title = "${day.dayName}: ${day.focus}",
            exercises = ProgressCalculator.newSessionExercises(day, _state.value.workoutLogs),
        )
        saveActive(session)
    }

    fun startEmptyWorkout() {
        if (_state.value.activeWorkout != null) return
        saveActive(WorkoutLog(newId(), todayKey(), nowMillis(), title = "Custom workout", exercises = emptyList()))
    }

    fun updateSet(exercise: Int, set: Int, transform: (SetLog) -> SetLog) = editActive { log ->
        log.copy(exercises = log.exercises.mapAt(exercise) { ex -> ex.copy(sets = ex.sets.mapAt(set, transform)) })
    }

    fun addSet(exercise: Int) = editActive { log ->
        log.copy(exercises = log.exercises.mapAt(exercise) { ex ->
            // A new set starts as a copy of the previous one, which is what people usually do next.
            ex.copy(sets = ex.sets + (ex.sets.lastOrNull()?.copy(completed = false) ?: SetLog()))
        })
    }

    fun removeSet(exercise: Int, set: Int) = editActive { log ->
        log.copy(exercises = log.exercises.mapAt(exercise) { ex -> ex.copy(sets = ex.sets.filterIndexed { i, _ -> i != set }) })
    }

    fun addExercise(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        editActive { log ->
            val previous = ProgressCalculator.lastSets(_state.value.workoutLogs, trimmed)
            val sets = previous?.map { it.copy(completed = false) } ?: List(3) { SetLog() }
            log.copy(exercises = log.exercises + ExerciseLog(name = trimmed, sets = sets))
        }
    }

    fun removeExercise(exercise: Int) = editActive { log ->
        log.copy(exercises = log.exercises.filterIndexed { i, _ -> i != exercise })
    }

    /** Saves completed sets only. Returns false if nothing was completed. */
    fun finishWorkout(notes: String): Boolean {
        val active = _state.value.activeWorkout ?: return false
        val exercises = active.exercises
            .map { ex -> ex.copy(sets = ex.sets.filter { it.completed }) }
            .filter { it.sets.isNotEmpty() }
        if (exercises.isEmpty()) {
            _state.update { it.copy(error = "Tick off at least one set before finishing.") }
            return false
        }
        val finished = active.copy(exercises = exercises, finishedAtMillis = nowMillis(), notes = notes.trim())
        val logs = _state.value.workoutLogs + finished
        repository.workoutLogs = logs
        repository.activeWorkout = null
        _state.update { it.copy(workoutLogs = logs, activeWorkout = null, today = todayKey()) }
        return true
    }

    fun discardWorkout() {
        repository.activeWorkout = null
        _state.update { it.copy(activeWorkout = null) }
    }

    fun deleteWorkoutLog(id: String) {
        val logs = _state.value.workoutLogs.filterNot { it.id == id }
        repository.workoutLogs = logs
        _state.update { it.copy(workoutLogs = logs) }
    }

    private fun saveActive(log: WorkoutLog) {
        repository.activeWorkout = log
        _state.update { it.copy(activeWorkout = log) }
    }

    private fun editActive(transform: (WorkoutLog) -> WorkoutLog) {
        _state.value.activeWorkout?.let { saveActive(transform(it)) }
    }

    // ---- Body weight ----

    /** Records today's weight (one entry per day) and keeps the profile, and so the targets, in sync. */
    fun logWeight(kg: Double) {
        val today = todayKey()
        val weights = (_state.value.weights.filterNot { it.dayKey == today } + WeightEntry(today, kg))
            .sortedBy { it.dayKey }
        repository.weights = weights
        val profile = _state.value.profile?.copy(weightKg = kg)
        if (profile != null) repository.profile = profile
        _state.update { it.copy(weights = weights, profile = profile ?: it.profile, today = today) }
    }

    fun deleteWeight(dayKey: String) {
        val weights = _state.value.weights.filterNot { it.dayKey == dayKey }
        repository.weights = weights
        _state.update { it.copy(weights = weights) }
    }

    // ---- Weekly check-in ----

    fun runCheckIn() {
        val s = _state.value
        val profile = s.profile ?: return
        val targets = s.targets ?: return
        if (s.runningCheckIn) return
        _state.update { it.copy(runningCheckIn = true) }
        val today = todayKey()
        val report = CheckInCalculator.buildReport(
            profile = profile,
            targets = targets,
            adjustment = s.adjustment,
            meals = s.meals,
            logs = s.workoutLogs,
            weights = s.weights,
            plan = s.workoutPlan,
            previous = s.checkIns.maxByOrNull { it.createdAtMillis },
            today = today,
        )
        viewModelScope.launch {
            runAi { ai.weeklyCheckIn(report) }
                .onSuccess { result ->
                    // A newer check-in replaces any earlier one that was never acted on.
                    val earlier = _state.value.checkIns.map {
                        if (it.status == CheckInStatus.PENDING) it.copy(status = CheckInStatus.DISMISSED) else it
                    }
                    val checkIns = earlier + WeeklyCheckIn(newId(), today, nowMillis(), result)
                    repository.checkIns = checkIns
                    _state.update { it.copy(checkIns = checkIns) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
            _state.update { it.copy(runningCheckIn = false) }
        }
    }

    /**
     * Applies the parts of a check-in the user accepted. Nutrition changes are bounded by
     * CheckInCalculator; training changes are matched against the current plan by name.
     */
    fun applyCheckIn(id: String, applyNutrition: Boolean, trainingChangeIndexes: Set<Int>) {
        val s = _state.value
        val checkIn = s.checkIns.firstOrNull { it.id == id && it.status == CheckInStatus.PENDING } ?: return
        val result = checkIn.result

        val adjustment = if (applyNutrition) {
            CheckInCalculator.boundedAdjustment(s.adjustment, result.calorieChange, result.proteinChange)
        } else {
            s.adjustment
        }
        val selected = result.trainingChanges.filterIndexed { i, _ -> i in trainingChangeIndexes }
        val (plan, appliedTraining) = s.workoutPlan
            ?.let { CheckInCalculator.applyTrainingChanges(it, selected) }
            ?: (null to 0)

        val updated = checkIn.copy(
            status = CheckInStatus.APPLIED,
            appliedCalorieChange = adjustment.calories - s.adjustment.calories,
            appliedProteinChange = adjustment.proteinG - s.adjustment.proteinG,
            appliedTrainingChanges = appliedTraining,
        )
        val checkIns = s.checkIns.map { if (it.id == id) updated else it }

        repository.checkIns = checkIns
        repository.targetAdjustment = adjustment
        if (plan != null && appliedTraining > 0) repository.workoutPlan = plan
        _state.update {
            it.copy(
                checkIns = checkIns,
                adjustment = adjustment,
                workoutPlan = if (plan != null && appliedTraining > 0) plan else it.workoutPlan,
            )
        }
        if (appliedTraining < selected.size) {
            _state.update { it.copy(error = "${selected.size - appliedTraining} exercise change(s) no longer matched your plan and were skipped.") }
        }
        // The meal plan was built for the old targets.
        if (adjustment != s.adjustment && s.nutritionPlan != null) generateNutritionPlan()
    }

    fun dismissCheckIn(id: String) {
        val checkIns = _state.value.checkIns.map {
            if (it.id == id && it.status == CheckInStatus.PENDING) it.copy(status = CheckInStatus.DISMISSED) else it
        }
        repository.checkIns = checkIns
        _state.update { it.copy(checkIns = checkIns) }
    }

    fun resetAdjustment() {
        repository.targetAdjustment = TargetAdjustment()
        _state.update { it.copy(adjustment = TargetAdjustment()) }
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
        if (s.workoutLogs.isNotEmpty()) {
            appendLine("Recent workouts (completed sets):")
            appendLine(ProgressCalculator.historySummary(s.workoutLogs))
        }
        s.checkIns.lastOrNull { it.status == CheckInStatus.APPLIED }?.let { c ->
            appendLine("Latest weekly check-in (${c.dayKey}): ${c.result.headline} ${c.result.summary}")
            if (c.result.focusForNextWeek.isNotEmpty()) appendLine("Focus this week: " + c.result.focusForNextWeek.joinToString("; "))
        }
        if (s.weights.size >= 2) {
            val first = s.weights.first()
            val last = s.weights.last()
            appendLine("Body weight trend: ${first.weightKg} kg on ${first.dayKey} -> ${last.weightKg} kg on ${last.dayKey}")
        }
    }.trim()

    private fun newId() = "${nowMillis()}-${Random.nextInt(1_000_000)}"

    private inline fun <T> List<T>.mapAt(index: Int, transform: (T) -> T): List<T> =
        mapIndexed { i, item -> if (i == index) transform(item) else item }

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
