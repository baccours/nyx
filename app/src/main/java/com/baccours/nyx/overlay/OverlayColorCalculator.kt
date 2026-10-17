package com.baccours.nyx.overlay

import com.baccours.nyx.data.OverlaySettings
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pure (Android-free, unit-testable) math that turns [OverlaySettings] into an ARGB color. */
object OverlayColorCalculator {

    /**
     * Dimming never reaches fully opaque black: at 100% the whole screen, including this
     * app's own controls, would be invisible and the user could not find the slider again.
     */
    const val MAX_DIM_OPACITY = 0.9f

    private const val MAX_TINT_ALPHA = 180 // max 180/255 opacity for the tint

    /** Packed ARGB overlay color for the given settings. */
    fun overlayColor(settings: OverlaySettings): Int {
        val dim = settings.dimIntensity.coerceIn(0f, 1f)
        val tint = settings.blueLightIntensity.coerceIn(0f, 1f)
        val rgb = kelvinToRgb(settings.colorTemperature.roundToInt())

        // Scale the Kelvin color by tint strength, then darken it by the dimming amount.
        // With tint == 0 this is black, which gives pure dimming.
        val scale = tint * (1f - dim)
        val red = (((rgb shr 16) and 0xFF) * scale).toInt()
        val green = (((rgb shr 8) and 0xFF) * scale).toInt()
        val blue = ((rgb and 0xFF) * scale).toInt()

        val alphaDim = (dim * MAX_DIM_OPACITY * 255).toInt()
        val alphaTint = (tint * MAX_TINT_ALPHA).toInt()
        val alpha = max(alphaDim, alphaTint)

        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    /**
     * Approximates the RGB value (packed as 0xRRGGBB) of a Kelvin temperature.
     * Based on Tanner Helland's implementation of Mitchell Charity's formula.
     */
    fun kelvinToRgb(kelvin: Int): Int {
        val temp = (kelvin / 100.0).coerceIn(10.0, 400.0)

        val r = if (temp <= 66) 255.0 else 329.698727446 * (temp - 60).pow(-0.1332047592)

        val g = if (temp <= 66) {
            99.4708025861 * ln(temp) - 161.1195681661
        } else {
            288.1221695283 * (temp - 60).pow(-0.0755148492)
        }

        val b = when {
            temp >= 66 -> 255.0
            temp <= 19 -> 0.0
            else -> 138.5177312231 * ln(temp - 10) - 305.0447927307
        }

        return (r.toChannel() shl 16) or (g.toChannel() shl 8) or b.toChannel()
    }

    private fun Double.toChannel() = coerceIn(0.0, 255.0).toInt()
}
