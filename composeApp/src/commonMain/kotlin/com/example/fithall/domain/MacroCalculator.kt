package com.example.fithall.domain

import com.example.fithall.model.Goal
import com.example.fithall.model.MacroTargets
import com.example.fithall.model.Sex
import com.example.fithall.model.UserProfile
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

    fun targets(profile: UserProfile): MacroTargets {
        val tdee = tdee(profile)
        val calories = when (profile.goal) {
            Goal.LOSE_FAT -> tdee * 0.80
            Goal.BUILD_MUSCLE -> tdee * 1.10
            Goal.GAIN_STRENGTH -> tdee * 1.05
            Goal.IMPROVE_ENDURANCE, Goal.MAINTAIN -> tdee
        }.roundToInt()

        // Protein per kg body weight; higher when cutting to preserve muscle.
        val proteinPerKg = when (profile.goal) {
            Goal.LOSE_FAT -> 2.2
            Goal.BUILD_MUSCLE, Goal.GAIN_STRENGTH -> 2.0
            Goal.IMPROVE_ENDURANCE -> 1.6
            Goal.MAINTAIN -> 1.6
        }
        val proteinG = (profile.weightKg * proteinPerKg).roundToInt()
        val fatG = (calories * 0.25 / 9).roundToInt()
        val carbsG = ((calories - proteinG * 4 - fatG * 9) / 4.0).roundToInt().coerceAtLeast(0)
        return MacroTargets(calories, proteinG, carbsG, fatG)
    }
}
