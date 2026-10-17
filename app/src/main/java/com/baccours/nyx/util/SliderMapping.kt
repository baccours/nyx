package com.baccours.nyx.util

import com.baccours.nyx.data.OverlaySettings.Companion.MAX_KELVIN
import com.baccours.nyx.data.OverlaySettings.Companion.MIN_KELVIN
import kotlin.math.roundToInt

/**
 * Maps between SeekBar progress (0..[STEPS]) and the values the overlay uses.
 *
 * Conversions back to progress *round* instead of truncating: `(0.57f * 100).toInt()` and
 * similar float round-trips can land a hair below the integer and move the slider one step.
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
