package com.fithall.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fithall.app.ai.AiErrorKind
import com.fithall.app.ai.AiException
import com.fithall.app.ai.AiPrompts
import com.fithall.app.ai.AiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.FoodDatabase
import com.fithall.app.data.PackagedFood
import com.fithall.app.data.PackagedFoods
import com.fithall.app.data.PortionUnit
import com.fithall.app.data.PrivacyConsent
import com.fithall.app.data.PrivacyPolicy
import com.fithall.app.data.UnavailableFoodDatabase
import com.fithall.app.domain.CheckInCalculator
import com.fithall.app.domain.NoReminders
import com.fithall.app.domain.ReminderScheduler
import com.fithall.app.domain.MacroCalculator
import com.fithall.app.domain.ProgressCalculator
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.ChatRole
import com.fithall.app.model.CheckInStatus
import com.fithall.app.model.ExerciseLog
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.MealAnalysis
import com.fithall.app.model.MealEntry
import com.fithall.app.model.NutritionPlan
import com.fithall.app.model.SetLog
import com.fithall.app.model.TargetAdjustment
import com.fithall.app.model.UserProfile
import com.fithall.app.model.WeeklyCheckIn
import com.fithall.app.model.WeightEntry
import com.fithall.app.model.WorkoutDay
import com.fithall.app.model.WorkoutLog
import com.fithall.app.model.WorkoutPlan
import com.fithall.app.platform.nowMillis
import com.fithall.app.platform.todayKey
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.coroutines.cancelChildren
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
    val consent: PrivacyConsent? = null,
    val checkInReminders: Boolean = false,
    val today: String = todayKey(),
    val scan: ScanState = ScanState.Idle,
    val generatingWorkout: Boolean = false,
    val generatingNutrition: Boolean = false,
    val coachTyping: Boolean = false,
    val runningCheckIn: Boolean = false,
    /** Inline failures, shown where the request was made, with a Retry when it can help. */
    val workoutPlanFailure: AiFailure? = null,
    val nutritionPlanFailure: AiFailure? = null,
    val checkInFailure: AiFailure? = null,
    val coachFailure: AiFailure? = null,
    /** Short notices for the snackbar. */
    val error: String? = null,
) {
    /** True until the user accepts the current privacy policy version. */
    val needsConsent: Boolean get() = consent?.version != PrivacyPolicy.VERSION
    val targets: MacroTargets? get() = profile?.let { MacroCalculator.targets(it, adjustment) }
    /** Null when check-ins may lower calories; otherwise the reason they can't. */
    val calorieCutBlockedReason: String?
        get() = profile?.let { CheckInCalculator.calorieCutBlockedReason(it, weights, today) }
    val canCutCalories: Boolean get() = calorieCutBlockedReason == null
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
    /** Deletes the platform's temporary files (cached photos, exports) when the user deletes their data. */
    private val clearCaches: () -> Unit = {},
    private val reminders: ReminderScheduler = NoReminders,
    private val connectivity: Connectivity = AlwaysOnline,
    private val crashReporter: CrashReporter = NoCrashReporter,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AppState(
            profile = repository.profile,
            meals = repository.history.meals(sinceDayKey = daysAgo(MEAL_HISTORY_DAYS)),
            workoutPlan = repository.workoutPlan,
            nutritionPlan = repository.nutritionPlan,
            chat = repository.history.chat(),
            workoutLogs = repository.history.workouts(),
            activeWorkout = repository.activeWorkout,
            weights = repository.history.weights(),
            checkIns = repository.history.checkIns(),
            adjustment = repository.targetAdjustment,
            consent = repository.consent,
            checkInReminders = repository.checkInReminders,
        )
    )

    init {
        // Idempotent: keeps the existing schedule if there is one.
        if (repository.checkInReminders) reminders.schedule()
        // Crash reports only with the current consent and the user's opt-in.
        crashReporter.setEnabled(!_state.value.needsConsent && _state.value.consent?.crashReports == true)
    }
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun refreshDay() = _state.update { it.copy(today = todayKey()) }

    fun dismissError() = _state.update { it.copy(error = null) }

    // ---- Privacy ----

    fun acceptConsent(crashReports: Boolean = false) {
        val consent = PrivacyConsent(PrivacyPolicy.VERSION, nowMillis(), crashReports)
        repository.consent = consent
        crashReporter.setEnabled(crashReports)
        _state.update { it.copy(consent = consent) }
    }

    fun setCrashReports(enabled: Boolean) {
        val consent = _state.value.consent?.copy(crashReports = enabled) ?: return
        repository.consent = consent
        crashReporter.setEnabled(enabled)
        _state.update { it.copy(consent = consent) }
    }

    /** Everything the user has entered, as a JSON document. */
    fun exportData(): String = repository.exportJson(exportedAt = todayKey())

    /**
     * Deletes everything FitHall keeps on this device and returns to the first screen. Nothing
     * needs deleting on the server: it doesn't store users' data. Running AI requests are
     * cancelled first so their results can't be written back afterwards.
     */
    fun deleteAllData() {
        viewModelScope.coroutineContext.cancelChildren()
        reminders.cancel()
        crashReporter.setEnabled(false)
        repository.clearAll()
        clearCaches()
        _state.value = AppState()
    }

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
                        onFailure = { e -> ScanState.Failed(source, failure(e).message) },
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
                        onFailure = {
                            val message = if (connectivity.isOnline.value) "Couldn't look up the barcode. Please try again." else ErrorMessages.OFFLINE
                            ScanState.Failed(source, message)
                        },
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
        repository.history.saveMeal(entry)
        _state.update { it.copy(meals = it.meals + entry, scan = ScanState.Idle, today = todayKey()) }
    }

    fun cancelScan() = _state.update { it.copy(scan = ScanState.Idle) }

    /** Saves edits to a logged meal (changed portions or removed foods). */
    fun updateMeal(entry: MealEntry) {
        repository.history.saveMeal(entry)
        _state.update { s -> s.copy(meals = s.meals.map { if (it.id == entry.id) entry else it }) }
    }

    fun deleteMeal(id: String) {
        repository.history.deleteMeal(id)
        _state.update { it.copy(meals = it.meals.filterNot { meal -> meal.id == id }) }
    }

    // ---- Plans ----

    fun generateWorkoutPlan() {
        val profile = _state.value.profile ?: return
        if (_state.value.generatingWorkout) return
        _state.update { it.copy(generatingWorkout = true, workoutPlanFailure = null) }
        viewModelScope.launch {
            val history = ProgressCalculator.historySummary(_state.value.workoutLogs)
            runAi { ai.generateWorkoutPlan(profile, history) }
                .onSuccess { plan ->
                    repository.workoutPlan = plan
                    _state.update { it.copy(workoutPlan = plan) }
                }
                .onFailure { e -> _state.update { it.copy(workoutPlanFailure = failure(e)) } }
            _state.update { it.copy(generatingWorkout = false) }
        }
    }

    fun generateNutritionPlan() {
        val profile = _state.value.profile ?: return
        val targets = _state.value.targets ?: return
        if (_state.value.generatingNutrition) return
        _state.update { it.copy(generatingNutrition = true, nutritionPlanFailure = null) }
        viewModelScope.launch {
            runAi { ai.generateNutritionPlan(profile, targets) }
                .onSuccess { plan ->
                    repository.nutritionPlan = plan
                    _state.update { it.copy(nutritionPlan = plan) }
                }
                .onFailure { e -> _state.update { it.copy(nutritionPlanFailure = failure(e)) } }
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
        // Save the finished session before clearing the in-progress one, so a crash can't lose it.
        repository.history.saveWorkout(finished)
        repository.activeWorkout = null
        _state.update { it.copy(workoutLogs = it.workoutLogs + finished, activeWorkout = null, today = todayKey()) }
        return true
    }

    fun discardWorkout() {
        repository.activeWorkout = null
        _state.update { it.copy(activeWorkout = null) }
    }

    fun deleteWorkoutLog(id: String) {
        repository.history.deleteWorkout(id)
        _state.update { it.copy(workoutLogs = it.workoutLogs.filterNot { log -> log.id == id }) }
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
        val entry = WeightEntry(today, kg)
        val weights = (_state.value.weights.filterNot { it.dayKey == today } + entry).sortedBy { it.dayKey }
        repository.history.saveWeight(entry)
        val profile = _state.value.profile?.copy(weightKg = kg)
        if (profile != null) repository.profile = profile
        _state.update { it.copy(weights = weights, profile = profile ?: it.profile, today = today) }
    }

    fun deleteWeight(dayKey: String) {
        repository.history.deleteWeight(dayKey)
        _state.update { it.copy(weights = it.weights.filterNot { w -> w.dayKey == dayKey }) }
    }

    // ---- Weekly check-in ----

    fun runCheckIn() {
        val s = _state.value
        val profile = s.profile ?: return
        val targets = s.targets ?: return
        if (s.runningCheckIn) return
        _state.update { it.copy(runningCheckIn = true, checkInFailure = null) }
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
                    val superseded = _state.value.checkIns
                        .filter { it.status == CheckInStatus.PENDING }
                        .map { it.copy(status = CheckInStatus.DISMISSED) }
                    val created = WeeklyCheckIn(newId(), today, nowMillis(), result)
                    repository.history.transaction {
                        superseded.forEach(repository.history::saveCheckIn)
                        repository.history.saveCheckIn(created)
                    }
                    _state.update { s -> s.copy(checkIns = s.checkIns.replaceAll(superseded) + created) }
                }
                .onFailure { e -> _state.update { it.copy(checkInFailure = failure(e)) } }
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
            CheckInCalculator.boundedAdjustment(s.adjustment, result.calorieChange, result.proteinChange, s.canCutCalories)
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
        val checkIns = s.checkIns.replaceAll(listOf(updated))

        repository.history.saveCheckIn(updated)
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
        val pending = _state.value.checkIns.firstOrNull { it.id == id && it.status == CheckInStatus.PENDING } ?: return
        val dismissed = pending.copy(status = CheckInStatus.DISMISSED)
        repository.history.saveCheckIn(dismissed)
        _state.update { it.copy(checkIns = it.checkIns.replaceAll(listOf(dismissed))) }
    }

    /** Call with true only after notification permission was granted. */
    fun setCheckInReminders(enabled: Boolean) {
        repository.checkInReminders = enabled
        if (enabled) reminders.schedule() else reminders.cancel()
        _state.update { it.copy(checkInReminders = enabled) }
    }

    fun resetAdjustment() {
        repository.targetAdjustment = TargetAdjustment()
        _state.update { it.copy(adjustment = TargetAdjustment()) }
    }

    // ---- Coach ----

    fun sendCoachMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.coachTyping) return
        val message = ChatMessage(ChatRole.USER, trimmed)
        repository.history.appendChat(message)
        _state.update { it.copy(chat = it.chat + message) }
        requestCoachReply()
    }

    /** Asks again for a reply to the last, unanswered message. */
    fun retryCoachReply() {
        if (_state.value.chat.lastOrNull()?.role == ChatRole.USER) requestCoachReply()
    }

    private fun requestCoachReply() {
        if (_state.value.coachTyping) return
        val history = _state.value.chat
        _state.update { it.copy(coachTyping = true, coachFailure = null) }
        viewModelScope.launch {
            runAi { ai.coachReply(coachContext(_state.value), history.takeLast(MAX_CHAT_TURNS)) }
                .onSuccess { reply ->
                    val answer = ChatMessage(ChatRole.ASSISTANT, reply)
                    repository.history.appendChat(answer)
                    _state.update { it.copy(chat = it.chat + answer) }
                }
                .onFailure { e -> _state.update { it.copy(coachFailure = failure(e)) } }
            _state.update { it.copy(coachTyping = false) }
        }
    }

    fun dismissFailures() = _state.update {
        it.copy(workoutPlanFailure = null, nutritionPlanFailure = null, checkInFailure = null, coachFailure = null)
    }

    fun clearChat() {
        repository.history.clearChat()
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

    /** Replaces entries with the same id. */
    private fun List<WeeklyCheckIn>.replaceAll(updated: List<WeeklyCheckIn>): List<WeeklyCheckIn> {
        val byId = updated.associateBy { it.id }
        return map { byId[it.id] ?: it }
    }

    private fun daysAgo(days: Int): String =
        LocalDate.parse(todayKey()).minus(DatePeriod(days = days)).toString()

    private fun newId() = "${nowMillis()}-${Random.nextInt(1_000_000)}"

    private inline fun <T> List<T>.mapAt(index: Int, transform: (T) -> T): List<T> =
        mapIndexed { i, item -> if (i == index) transform(item) else item }

    /** Runs an AI or network call; fails straight away when the device is offline. */
    private suspend fun <T> runAi(block: suspend () -> T): Result<T> {
        if (!connectivity.isOnline.value) {
            return Result.failure(AiException(ErrorMessages.OFFLINE, AiErrorKind.NETWORK))
        }
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun failure(e: Throwable): AiFailure {
        // Expected failures (offline, limits, refusals) are AiExceptions; anything else is a bug worth reporting.
        if (e !is AiException) crashReporter.recordNonFatal(e)
        return ErrorMessages.forFailure(e, connectivity.isOnline.value)
    }

    private companion object {
        const val MAX_CHAT_TURNS = 30

        /** Meals kept in memory: today, the weekly check-in and the coach need far less. Older meals stay in the database (and in exports). */
        const val MEAL_HISTORY_DAYS = 60
    }
}
