package com.fithall.app

import androidx.compose.ui.graphics.Color
import com.fithall.app.model.Exercise
import com.fithall.app.model.ExerciseLog
import com.fithall.app.model.SetLog
import com.fithall.app.model.UnitSystem
import com.fithall.app.model.WorkoutDay
import com.fithall.app.model.WorkoutLog
import com.fithall.app.model.WorkoutPlan
import com.fithall.app.ui.MacroColors
import com.fithall.app.ui.NextWorkout
import com.fithall.app.ui.Palette
import com.fithall.app.ui.SuggestedSet
import com.fithall.app.ui.contentOn
import com.fithall.app.ui.contrastRatio
import com.fithall.app.ui.firstUnfinishedExercise
import com.fithall.app.ui.formatSet
import com.fithall.app.ui.formatVolume
import com.fithall.app.ui.formatWeight
import com.fithall.app.ui.longDate
import com.fithall.app.ui.nextWorkout
import com.fithall.app.ui.setLabel
import com.fithall.app.ui.shortDate
import com.fithall.app.ui.suggestedSet
import com.fithall.app.ui.targetReps
import com.fithall.app.ui.weightStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The logic behind the Today screen, the live workout and the colour scheme. */
class RedesignTest {

    // ---- Today: the training card ----

    private fun day(n: Int) = WorkoutDay("Day $n", "Focus $n", 45, "", listOf(Exercise("Lift $n", 3, "8-10", 90, "")))
    private val plan = WorkoutPlan("Plan", "", 8, (1..4).map(::day), "", "")

    @Test
    fun theNextPlanDayMovesOnWithEachWorkoutThisWeek() {
        val state = AppState(workoutPlan = plan)
        assertEquals(NextWorkout.PlanDay(day(1), 1, 4), nextWorkout(state, doneThisWeek = 0))
        assertEquals(NextWorkout.PlanDay(day(3), 3, 4), nextWorkout(state, doneThisWeek = 2))
        // After the last day it starts over.
        assertEquals(NextWorkout.PlanDay(day(2), 2, 4), nextWorkout(state, doneThisWeek = 5))
    }

    @Test
    fun aWorkoutInProgressComesFirst() {
        val session = WorkoutLog("w", "2026-11-13", 0L, title = "Focus 2", exercises = emptyList())
        val state = AppState(workoutPlan = plan, activeWorkout = session)
        assertEquals(NextWorkout.Resume(session), nextWorkout(state, doneThisWeek = 0))
    }

    @Test
    fun withoutAPlanTheCardOffersOneOrShowsItsBeingBuilt() {
        assertEquals(NextWorkout.NoPlan, nextWorkout(AppState(), 0))
        assertEquals(NextWorkout.Building, nextWorkout(AppState(generatingWorkout = true), 0))
        // A plan with no days is treated as no plan.
        assertEquals(NextWorkout.NoPlan, nextWorkout(AppState(workoutPlan = plan.copy(days = emptyList())), 3))
    }

    // ---- Live workout ----

    private fun exercise(target: String = "3 × 8-10", vararg sets: SetLog) = ExerciseLog("Press", target, 90, sets.toList())

    @Test
    fun theFirstSetStartsFromLastSessionThenThePlan() {
        val lastTime = listOf(SetLog(20.0, 10, true), SetLog(20.0, 9, true))
        assertEquals(SuggestedSet(20.0, 10), suggestedSet(exercise(sets = arrayOf(SetLog(), SetLog())), 0, lastTime))
        // Never done before: no weight yet, reps from the low end of the target.
        assertEquals(SuggestedSet(null, 8), suggestedSet(exercise(sets = arrayOf(SetLog())), 0, null))
    }

    @Test
    fun laterSetsCarryOnFromThePreviousSet() {
        val ex = exercise(sets = arrayOf(SetLog(22.5, 9, true), SetLog()))
        assertEquals(SuggestedSet(22.5, 9), suggestedSet(ex, 1, lastTime = listOf(SetLog(20.0, 10), SetLog(20.0, 10))))
    }

    @Test
    fun whatTheUserAlreadyTypedWins() {
        val ex = exercise(sets = arrayOf(SetLog(22.5, 9, true), SetLog(25.0, 6)))
        assertEquals(SuggestedSet(25.0, 6), suggestedSet(ex, 1, null))
    }

    @Test
    fun bodyweightSetsHaveNoWeightAndRepsAreAtLeastOne() {
        assertEquals(SuggestedSet(null, 12), suggestedSet(exercise("3 × 12", SetLog(0.0, 0)), 0, null))
        assertEquals(1, suggestedSet(exercise("3 × 0", SetLog()), 0, null).reps)
    }

    @Test
    fun repTargetsAreReadFromThePlan() {
        assertEquals(8, targetReps("3 × 8-10"))
        assertEquals(12, targetReps("3x12"))
        assertEquals(10, targetReps("AMRAP"))
        assertEquals(10, targetReps(""))
    }

    @Test
    fun aSessionOpensOnTheFirstExerciseWithSetsLeft() {
        val done = exercise(sets = arrayOf(SetLog(20.0, 8, true)))
        val todo = exercise(sets = arrayOf(SetLog(20.0, 8, true), SetLog()))
        assertEquals(1, firstUnfinishedExercise(listOf(done, todo, todo)))
        assertEquals(0, firstUnfinishedExercise(listOf(done, done)))
        assertEquals(0, firstUnfinishedExercise(emptyList()))
    }

    @Test
    fun weightStepsAndSetLabelsFollowTheUnits() {
        assertEquals(2.5, weightStep(UnitSystem.METRIC))
        assertEquals(5.0, weightStep(UnitSystem.IMPERIAL))
        assertEquals("22.5 kg × 10", setLabel(SetLog(22.5, 10, true), UnitSystem.METRIC))
        assertEquals("44.1 lb × 8", setLabel(SetLog(20.0, 8, true), UnitSystem.IMPERIAL))
        assertEquals("12 reps", setLabel(SetLog(null, 12, true), UnitSystem.METRIC))
    }

    // ---- Formatting ----

    @Test
    fun weightsAndVolumesAreShownInTheUsersUnits() {
        assertEquals("80.5 kg", formatWeight(80.5, UnitSystem.METRIC))
        assertEquals("176.4 lb", formatWeight(80.0, UnitSystem.IMPERIAL))
        assertEquals("1.2 t", formatVolume(1234.0, UnitSystem.METRIC))
        assertEquals("2.7k lb", formatVolume(1234.0, UnitSystem.IMPERIAL))
        assertEquals("60×8", formatSet(60.0, 8, UnitSystem.METRIC))
        assertEquals("12 reps", formatSet(null, 12, UnitSystem.METRIC, repsSuffix = " reps"))
        assertEquals("5", formatSet(0.0, 5, UnitSystem.METRIC))
    }

    @Test
    fun datesAreShortAndReadable() {
        assertEquals("Oct 11", shortDate("2026-10-11"))
        assertEquals("Sunday, Oct 11", longDate("2026-10-11"))
        // A malformed key is shown as it is rather than crashing the screen.
        assertEquals("not-a-date", shortDate("not-a-date"))
        assertEquals("not-a-date", longDate("not-a-date"))
    }

    // ---- Colours ----

    @Test
    fun contrastRatioMatchesTheWcagFormula() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Palette.lime, Palette.lime), 0.01f)
    }

    @Test
    fun textOnEveryAccentIsReadable() {
        // The blue check-in card once got white text at about 2:1.
        assertEquals(Palette.onLime, contentOn(Palette.blue))
        assertEquals(Palette.onLime, contentOn(Palette.lime))
        assertEquals(Palette.text, contentOn(Palette.raised))
        listOf(Palette.lime, Palette.blue, Palette.raised, Palette.surface, Palette.text, MacroColors.carbs, MacroColors.fat).forEach { accent ->
            val ratio = contrastRatio(contentOn(accent), accent)
            assertTrue(ratio >= 4.5f, "text on $accent has contrast $ratio")
        }
    }

    @Test
    fun thePalettesTextColoursPassAa() {
        val pairs = mapOf(
            "text on ground" to (Palette.text to Palette.ground),
            "muted on ground" to (Palette.muted to Palette.ground),
            "muted on surface" to (Palette.muted to Palette.surface),
            "muted on raised" to (Palette.muted to Palette.raised),
            "lime links on ground" to (Palette.lime to Palette.ground),
            "lime on surface" to (Palette.lime to Palette.surface),
            "blue notes on ground" to (Palette.blue to Palette.ground),
            "dark text on lime" to (Palette.onLime to Palette.lime),
            "errors on ground" to (Palette.danger to Palette.ground),
        )
        pairs.forEach { (name, colours) ->
            val ratio = contrastRatio(colours.first, colours.second)
            assertTrue(ratio >= 4.5f, "$name has contrast $ratio")
        }
    }
}
