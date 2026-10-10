package com.fithall.app.domain

import com.fithall.app.model.Goal
import com.fithall.app.model.MacroTargets
import com.fithall.app.model.Safety
import com.fithall.app.model.Sex
import com.fithall.app.model.TargetAdjustment
import com.fithall.app.model.UserProfile
import kotlin.math.roundToInt

/**
 * Deterministic daily targets. The math is done here rather than by the AI so the numbers are
 * stable, explainable and testable; the AI builds meal plans on top of these targets.
 */
object MacroCalculator {

    /** Mifflin-St Jeor basal metabolic rate, kcal/day. */
    fun bmr(profile: UserProfile): Double {
        val base = 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * profile.age
        return if (profile.sex == Sex.MALE) base + 5 else base - 161
    }

    fun tdee(profile: UserProfile): Double = bmr(profile) * profile.activityLevel.multiplier

    /** Lowest daily intake the app will ever set, regardless of goal or check-in adjustments. */
    fun minimumCalories(profile: UserProfile): Int = if (profile.sex == Sex.MALE) 1500 else 1200

    /** Largest total change check-ins may make to the calculated targets. */
    const val MAX_CALORIE_ADJUSTMENT = 1000
    const val MAX_PROTEIN_ADJUSTMENT = 60

    /**
     * The goal the targets are actually built for. Underweight users never get a deficit, even
     * if they chose "Lose fat"; they're treated as maintaining instead.
     */
    fun effectiveGoal(profile: UserProfile): Goal =
        if (profile.goal == Goal.LOSE_FAT && Safety.isUnderweight(profile)) Goal.MAINTAIN else profile.goal

    /** Shown wherever targets appear when the safety rules changed what the user asked for. */
    fun safetyNotice(profile: UserProfile): String? =
        if (profile.goal == Goal.LOSE_FAT && Safety.isUnderweight(profile)) {
            "Your BMI is below ${Safety.UNDERWEIGHT_BMI}, so FitHall won't set a calorie deficit. " +
                "Your targets are set to maintain your weight. If you want to lose weight anyway, please talk to a doctor first."
        } else null

    fun targets(profile: UserProfile, adjustment: TargetAdjustment = TargetAdjustment()): MacroTargets {
        val tdee = tdee(profile)
        val goal = effectiveGoal(profile)
        val underweight = Safety.isUnderweight(profile)
        val baseCalories = when (goal) {
            Goal.LOSE_FAT -> tdee * 0.80
            Goal.BUILD_MUSCLE -> tdee * 1.10
            Goal.GAIN_STRENGTH -> tdee * 1.05
            Goal.IMPROVE_ENDURANCE, Goal.MAINTAIN -> tdee
        }.roundToInt()
        // Check-in cuts don't apply to underweight users, and they never go below maintenance.
        val calorieAdjustment = if (underweight) adjustment.calories.coerceAtLeast(0) else adjustment.calories
        val floor = if (underweight) maxOf(minimumCalories(profile), tdee.roundToInt()) else minimumCalories(profile)
        val calories = (baseCalories + calorieAdjustment).coerceAtLeast(floor)

        // Protein per kg body weight; higher when cutting to preserve muscle.
        val proteinPerKg = when (goal) {
            Goal.LOSE_FAT -> 2.2
            Goal.BUILD_MUSCLE, Goal.GAIN_STRENGTH -> 2.0
            Goal.IMPROVE_ENDURANCE -> 1.6
            Goal.MAINTAIN -> 1.6
        }
        val proteinG = ((profile.weightKg * proteinPerKg).roundToInt() + adjustment.proteinG).coerceAtLeast(50)
        val fatG = (calories * 0.25 / 9).roundToInt()
        val carbsG = ((calories - proteinG * 4 - fatG * 9) / 4.0).roundToInt().coerceAtLeast(0)
        return MacroTargets(calories, proteinG, carbsG, fatG)
    }
}
