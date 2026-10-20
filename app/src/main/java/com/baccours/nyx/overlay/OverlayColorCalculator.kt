package com.baccours.nyx.overlay

import com.baccours.nyx.data.OverlaySettings
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Calculates the ARGB overlay color for given [OverlaySettings].
 *
 * Converts desired per-channel gains into an alpha blend `out = (1 - a) * src + a * C`.
 * The channel with the lowest target gain sets the overlay opacity `a`. Higher target gains
 * for other channels are compensated with a color offset, capped by [OverlaySettings.blackLevel]
 * to preserve black levels on OLED displays.
 */
object OverlayColorCalculator {

    /** Maximum overlay opacity to ensure the screen remains usable. */
    const val MAX_DIM_OPACITY = 0.9f

    /** Maximum black-level lift allowed (in 8-bit color levels). */
    const val MAX_BLACK_LIFT = 64f

    /** Packed ARGB overlay color for the given settings. */
    fun overlayColor(settings: OverlaySettings): Int {
        // Self-sufficient: NaN would make roundToInt() throw, so don't rely on callers sanitizing.
        val s = settings.sanitized()
        val dim = s.dimIntensity
        val strength = s.blueLightIntensity
        val liftBudget = s.blackLevel * MAX_BLACK_LIFT

        // Target per-channel gains based on Kelvin color, filter strength, and dimming.
        val rgb = kelvinToRgb(s.colorTemperature.roundToInt())
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        val peak = max(red, max(green, blue)).coerceAtLeast(1).toFloat()
        val dimGain = 1f - dim * MAX_DIM_OPACITY
        val gainR = dimGain * (1f - strength * (1f - red / peak))
        val gainG = dimGain * (1f - strength * (1f - green / peak))
        val gainB = dimGain * (1f - strength * (1f - blue / peak))

        // Lowest channel gain determines base layer opacity (capped by MAX_DIM_OPACITY).
        val baseGain = min(gainR, min(gainG, gainB)).coerceAtLeast(1f - MAX_DIM_OPACITY)
        val alpha8 = ((1f - baseGain) * 255f).roundToInt().coerceIn(0, 255)
        if (alpha8 == 0) return 0 // fully transparent
        val alpha = alpha8 / 255f

        // Compensate remaining channels using offset, capped by black level budget.
        val spread = max(gainR, max(gainG, gainB)) - baseGain
        val reference = if (spread > 0f) min(255f, liftBudget / spread) else 0f

        // channelColor = offset / alpha
        fun channel(gain: Float) = (((gain - baseGain) * reference) / alpha).roundToInt().coerceIn(0, 255)

        return (alpha8 shl 24) or (channel(gainR) shl 16) or (channel(gainG) shl 8) or channel(gainB)
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
