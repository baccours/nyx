package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlaySettings
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
        OverlaySettings.MIN_KELVIN + progressToFraction(progress) * (OverlaySettings.MAX_KELVIN - OverlaySettings.MIN_KELVIN)

    fun kelvinToProgress(kelvin: Float): Int =
        fractionToProgress((kelvin - OverlaySettings.MIN_KELVIN) / (OverlaySettings.MAX_KELVIN - OverlaySettings.MIN_KELVIN))
}