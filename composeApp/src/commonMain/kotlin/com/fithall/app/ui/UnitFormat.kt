package com.fithall.app.ui

import com.fithall.app.model.UnitSystem
import com.fithall.app.model.Units

/** "80.5 kg" or "177.5 lb", from a stored kg value. */
fun formatWeight(kg: Double, units: UnitSystem): String =
    "${formatNumber(Units.kgToDisplay(kg, units))} ${Units.weightUnit(units)}"

/** Total lifted: tonnes in metric ("1.2 t"), thousands of pounds in imperial ("2.6k lb"). */
fun formatVolume(kg: Double, units: UnitSystem): String = when (units) {
    UnitSystem.METRIC -> "${formatNumber(kg / 1000)} t"
    UnitSystem.IMPERIAL -> "${formatNumber(Units.kgToDisplay(kg, units) / 1000)}k lb"
}

/** "60×8" or "132×8" for a set; bodyweight sets show reps only. */
fun formatSet(weightKg: Double?, reps: Int, units: UnitSystem, repsSuffix: String = ""): String =
    weightKg?.takeIf { it > 0 }?.let { "${formatNumber(Units.kgToDisplay(it, units))}×$reps" } ?: "$reps$repsSuffix"
