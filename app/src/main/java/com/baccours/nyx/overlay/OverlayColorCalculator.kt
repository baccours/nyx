package com.baccours.nyx.overlay

import com.baccours.nyx.data.OverlaySettings
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure (Android-free, unit-testable) math that turns [OverlaySettings] into the ARGB color of
 * the full-screen overlay.
 *
 * ## Why this is not a simple "tint"
 * What we want is a per-channel *multiplicative* filter (`out = src * gain`), like a real display
 * color matrix: black stays exactly 0, which matters on OLED panels where black pixels are off.
 * An overlay window can only do an alpha blend:
 *
 *     out = (1 - a) * src + a * C   =   G * src + offset      (G = 1 - a, offset = a * C >= 0)
 *
 * i.e. ONE gain shared by all channels plus a non-negative per-channel offset. Two consequences:
 *  - The channel with the lowest wanted gain (blue, for a warm filter) can be matched exactly:
 *    set `G` to that gain and give the channel no offset. Its output, and therefore the blue light
 *    emitted, is exactly what the filter asks for, and black stays 0 in that channel.
 *  - Every other channel wants a *higher* gain than `G`. Overlays can only get there by adding an
 *    offset, which lifts black. Matching them for the whole 0..255 range would need an offset of
 *    `(gain - G) * 255` (up to ~120 levels), a visible haze. The previous implementation lerped
 *    toward the full Kelvin color and lifted black to about (180, 133, 95) at full strength.
 *
 * So the compensation is capped by an explicit **black-level budget** (in 8-bit levels): the offset
 * of every channel is chosen so the filter is matched exactly up to a reference level
 * `budget / spread` and black never rises above `budget`. A budget of 0 gives true black and plain
 * dimming (the Blue Light Filter then just darkens, evenly); higher budgets restore more warmth
 * (more red/green fill) at the price of lifted blacks. LCDs, whose blacks are already backlit,
 * can afford a higher budget than OLEDs.
 */
object OverlayColorCalculator {

    /**
     * The overlay never reaches full opacity: at 100% the whole screen, including this app's own
     * controls, would be invisible and the user could not find the slider again. Applies to
     * dimming and to the blue-light filter alike.
     */
    const val MAX_DIM_OPACITY = 0.9f

    /** Black-level budget (8-bit levels) at the top of the "Black Level" slider (~25%). */
    const val MAX_BLACK_LIFT = 64f

    /** Packed ARGB overlay color for the given settings. */
    fun overlayColor(settings: OverlaySettings): Int {
        val dim = settings.dimIntensity.coerceIn(0f, 1f)
        val strength = settings.blueLightIntensity.coerceIn(0f, 1f)
        val liftBudget = settings.blackLevel.coerceIn(0f, 1f) * MAX_BLACK_LIFT

        // 1. The filter we would like: Kelvin color (normalised so its strongest channel is 1),
        //    blended in by `strength`, times the dimming.
        val rgb = kelvinToRgb(settings.colorTemperature.roundToInt())
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        val peak = max(red, max(green, blue)).coerceAtLeast(1).toFloat()
        val dimGain = 1f - dim * MAX_DIM_OPACITY
        val gainR = dimGain * (1f - strength * (1f - red / peak))
        val gainG = dimGain * (1f - strength * (1f - green / peak))
        val gainB = dimGain * (1f - strength * (1f - blue / peak))

        // 2. Realise it as one alpha blend. The lowest gain becomes the layer's own gain
        //    (exact match, zero black lift in that channel)...
        // Never let the layer become (nearly) opaque, e.g. a 0 blue gain at 1000K / 100% would
        // otherwise paint a flat, unusable screen. The cap is the same one used for dimming.
        val baseGain = min(gainR, min(gainG, gainB)).coerceAtLeast(1f - MAX_DIM_OPACITY)
        val alpha8 = ((1f - baseGain) * 255f).roundToInt().coerceIn(0, 255)
        if (alpha8 == 0) return 0 // nothing to draw: fully transparent
        val alpha = alpha8 / 255f

        // ...and the other channels are compensated with an offset, capped by the black budget.
        val spread = max(gainR, max(gainG, gainB)) - baseGain
        val reference = if (spread > 0f) min(255f, liftBudget / spread) else 0f

        // offset = alpha * channelColor  =>  channelColor = offset / alpha
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
