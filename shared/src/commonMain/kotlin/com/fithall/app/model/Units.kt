package com.fithall.app.model

import kotlin.math.roundToInt

/** Conversions between the stored metric values and what the user sees and types. */
object Units {
    const val LB_PER_KG = 2.20462262185
    const val CM_PER_INCH = 2.54

    fun kgToDisplay(kg: Double, units: UnitSystem): Double = if (units == UnitSystem.IMPERIAL) kg * LB_PER_KG else kg

    fun displayToKg(value: Double, units: UnitSystem): Double = if (units == UnitSystem.IMPERIAL) value / LB_PER_KG else value

    fun weightUnit(units: UnitSystem): String = if (units == UnitSystem.IMPERIAL) "lb" else "kg"

    /** 180 cm -> (5, 11). */
    fun cmToFeetInches(cm: Int): Pair<Int, Int> {
        val totalInches = (cm / CM_PER_INCH).roundToInt()
        return totalInches / 12 to totalInches % 12
    }

    fun feetInchesToCm(feet: Int, inches: Int): Int = ((feet * 12 + inches) * CM_PER_INCH).roundToInt()
}
