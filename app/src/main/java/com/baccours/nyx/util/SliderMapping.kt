package com.baccours.nyx.util

import com.baccours.nyx.data.OverlaySettings.Companion.MAX_KELVIN
import com.baccours.nyx.data.OverlaySettings.Companion.MIN_KELVIN
import kotlin.math.roundToInt

/**
 * Maps SeekBar progress (0..[STEPS]) to overlay values.
 *
 * Conversions round to prevent float precision drift.
 */
object SliderMapping {
    const val STEPS = 100

    fun progressToFraction(progress: Int): Float = progress.coerceIn(0, STEPS) / STEPS.toFloat()

    fun fractionToProgress(fraction: Float): Int = (fraction * STEPS).roundToInt().coerceIn(0, STEPS)

    fun progressToKelvin(progress: Int): Float =
        MIN_KELVIN + progressToFraction(progress) * (MAX_KELVIN - MIN_KELVIN)

    fun kelvinToProgress(kelvin: Float): Int =
        fractionToProgress((kelvin - MIN_KELVIN) / (MAX_KELVIN - MIN_KELVIN))
}
