package com.baccours.nyx.ui

import android.content.Context
import androidx.annotation.StringRes
import com.baccours.nyx.R
import com.baccours.nyx.data.OverlaySettings
import kotlin.math.roundToInt

private const val PERCENT = 100f
private const val KELVIN_STEP = 100f
private const val KELVIN_STEPS =
    ((OverlaySettings.MAX_KELVIN - OverlaySettings.MIN_KELVIN) / KELVIN_STEP).toInt()

/**
 * The settings sliders. Each maps SeekBar progress (0...steps) linearly onto its setting's
 * range and knows how to display it, so a new slider is a single entry here.
 *
 * Conversions round to prevent float precision drift.
 */
enum class SliderType(
    private val min: Float,
    private val max: Float,
    val steps: Int,
    /** Factor from the setting to the displayed number (e.g. a 0..1 fraction shown as %). */
    private val displayScale: Float,
    /** Format string taking the displayed number. */
    @get:StringRes val valueFormat: Int,
    private val read: (OverlaySettings) -> Float,
    private val write: (OverlaySettings, Float) -> OverlaySettings
) {
    DIM_INTENSITY(
        0f, 1f, 100, PERCENT, R.string.value_percent,
        { it.dimIntensity }, { s, v -> s.copy(dimIntensity = v) }
    ),
    BLUE_LIGHT(
        0f, 1f, 100, PERCENT, R.string.value_percent,
        { it.blueLightIntensity }, { s, v -> s.copy(blueLightIntensity = v) }
    ),
    COLOR_TEMPERATURE(
        OverlaySettings.MIN_KELVIN, OverlaySettings.MAX_KELVIN, KELVIN_STEPS, 1f, R.string.value_kelvin,
        { it.colorTemperature }, { s, v -> s.copy(colorTemperature = v) }
    ),
    WARMTH_BOOST(
        0f, 1f, 100, PERCENT, R.string.value_percent,
        { it.warmthBoost }, { s, v -> s.copy(warmthBoost = v) }
    );

    /** This slider's progress for [settings]. */
    fun progressOf(settings: OverlaySettings): Int =
        (((read(settings) - min) / (max - min)) * steps).roundToInt().coerceIn(0, steps)

    /** [settings] with this slider's value set from [progress] (clamped to 0...steps). */
    fun withProgress(settings: OverlaySettings, progress: Int): OverlaySettings =
        write(settings, valueAt(progress))

    /** The number shown to the user at [progress], to format with [valueFormat]. */
    fun displayValue(progress: Int): Int = (valueAt(progress) * displayScale).roundToInt()

    /** Formats the value shown to the user at [progress] into a localized string using [context]. */
    fun formatValue(context: Context, progress: Int): String =
        context.getString(valueFormat, displayValue(progress))

    private fun valueAt(progress: Int): Float = min + progress.coerceIn(0, steps) / steps.toFloat() * (max - min)
}
