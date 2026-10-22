package com.baccours.nyx.ui

import android.content.Context
import androidx.annotation.StringRes
import com.baccours.nyx.R
import com.baccours.nyx.data.OverlaySettings
import kotlin.math.roundToInt

private const val PERCENT = 100f

/**
 * The settings sliders. Each maps SeekBar progress (0..[STEPS]) linearly onto its setting's
 * range and knows how to display it, so a new slider is a single entry here.
 *
 * Conversions round to prevent float precision drift.
 */
enum class SliderType(
    private val min: Float,
    private val max: Float,
    /** Factor from the setting to the displayed number (e.g. a 0..1 fraction shown as %). */
    private val displayScale: Float,
    /** Format string taking the displayed number. */
    @get:StringRes val valueFormat: Int,
    private val read: (OverlaySettings) -> Float,
    private val write: (OverlaySettings, Float) -> OverlaySettings
) {
    DIM_INTENSITY(
        0f, 1f, PERCENT, R.string.value_percent,
        { it.dimIntensity }, { s, v -> s.copy(dimIntensity = v) }
    ),
    BLUE_LIGHT(
        0f, 1f, PERCENT, R.string.value_percent,
        { it.blueLightIntensity }, { s, v -> s.copy(blueLightIntensity = v) }
    ),
    COLOR_TEMPERATURE(
        OverlaySettings.MIN_KELVIN, OverlaySettings.MAX_KELVIN, 1f, R.string.value_kelvin,
        { it.colorTemperature }, { s, v -> s.copy(colorTemperature = v) }
    ),
    BLACK_LEVEL(
        0f, 1f, PERCENT, R.string.value_percent,
        { it.blackLevel }, { s, v -> s.copy(blackLevel = v) }
    );

    /** This slider's progress for [settings]. */
    fun progressOf(settings: OverlaySettings): Int =
        (((read(settings) - min) / (max - min)) * STEPS).roundToInt().coerceIn(0, STEPS)

    /** [settings] with this slider's value set from [progress] (clamped to 0..[STEPS]). */
    fun withProgress(settings: OverlaySettings, progress: Int): OverlaySettings =
        write(settings, valueAt(progress))

    /** The number shown to the user at [progress], to format with [valueFormat]. */
    fun displayValue(progress: Int): Int = (valueAt(progress) * displayScale).roundToInt()

    /** Formats the value shown to the user at [progress] into a localized string using [context]. */
    fun formatValue(context: Context, progress: Int): String =
        context.getString(valueFormat, displayValue(progress))

    private fun valueAt(progress: Int): Float = min + progress.coerceIn(0, STEPS) / STEPS.toFloat() * (max - min)

    companion object {
        const val STEPS = 100
    }
}
