package com.fithall.app.model

/**
 * Health guard rails shared by the app and the server. They override the user's goal, the
 * calculated targets and anything the AI suggests.
 */
object Safety {
    /** FitHall is for adults: diet targets and AI coaching aren't designed for minors. */
    const val MIN_AGE = 18
    const val MAX_AGE = 100

    /** WHO threshold for underweight. Below it, the app never sets a calorie deficit. */
    const val UNDERWEIGHT_BMI = 18.5

    /** Losing faster than this share of body weight per week blocks further calorie cuts. */
    const val MAX_SAFE_WEEKLY_LOSS_PERCENT = 1.0

    fun bmi(weightKg: Double, heightCm: Int): Double {
        val meters = heightCm / 100.0
        return weightKg / (meters * meters)
    }

    fun bmi(profile: UserProfile): Double = bmi(profile.weightKg, profile.heightCm)

    fun isUnderweight(profile: UserProfile): Boolean = bmi(profile) < UNDERWEIGHT_BMI
}
