package com.baccours.nyx.overlay

import com.baccours.nyx.data.OverlaySettings
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure (Android-free, unit-testable) translation of [OverlaySettings] into what the
 * [com.baccours.nyx.data.FilterEngine.SYSTEM] engine can express.
 *
 * The system's Night Light only has an on/off switch and ONE temperature, limited to a device
 * range (AOSP default 2596..4082 K), so the two warmth sliders are folded into the single number
 * that matters for sleep: **how much blue light is removed**. The temperature is chosen so Night
 * Light removes about as much blue as the Blue Light Filter asks for, i.e. the same quantity the
 * overlay engine matches exactly. (Night Light's real matrix differs slightly from the Kelvin
 * approximation used here, so this is a close match, not an identical one.)
 */
object SystemFilterPlanner {

    /** AOSP defaults of config_nightDisplayColorTemperatureMin / ...Max. */
    const val DEFAULT_MIN_KELVIN = 2596
    const val DEFAULT_MAX_KELVIN = 4082

    data class Capabilities(
        val minKelvin: Int = DEFAULT_MIN_KELVIN,
        val maxKelvin: Int = DEFAULT_MAX_KELVIN
    )

    /**
     * @property nightLightKelvin Night Light temperature, or `null` when Night Light should not be used.
     * @property dimIntensity Dimming (0..1), still drawn as a pure black overlay: an exact multiply, so it never lifts black.
     */
    data class Plan(val nightLightKelvin: Int?, val dimIntensity: Float)

    fun plan(settings: OverlaySettings, capabilities: Capabilities = Capabilities()): Plan {
        val s = settings.sanitized()
        return Plan(nightLightKelvin = nightLightKelvin(s, capabilities), dimIntensity = s.dimIntensity)
    }

    /** Share of blue light (0..1) the Blue Light Filter asks to remove, before dimming. */
    internal fun requestedBlueCut(settings: OverlaySettings): Float =
        settings.blueLightIntensity * (1f - blueGain(settings.colorTemperature.roundToInt()))

    /** Remaining blue (0..1) relative to the strongest channel at [kelvin]. Rises with temperature. */
    internal fun blueGain(kelvin: Int): Float {
        val rgb = OverlayColorCalculator.kelvinToRgb(kelvin)
        val peak = max((rgb shr 16) and 0xFF, max((rgb shr 8) and 0xFF, rgb and 0xFF)).coerceAtLeast(1)
        return (rgb and 0xFF) / peak.toFloat()
    }

    private fun nightLightKelvin(settings: OverlaySettings, caps: Capabilities): Int? {
        val min = caps.minKelvin
        val max = max(caps.maxKelvin, min)

        val cut = requestedBlueCut(settings)
        if (cut <= 0f) return null

        // Night Light cannot be milder than its own maximum temperature. Anything closer to "off"
        // than to that mildest setting is treated as off instead of jumping to it.
        val mildestCut = 1f - blueGain(max)
        if (cut < mildestCut / 2f) return null

        // blueGain() rises with temperature: find the lowest temperature whose blue is >= the target.
        val targetGain = 1f - cut
        var lo = min
        var hi = max
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (blueGain(mid) >= targetGain) hi = mid else lo = mid + 1
        }
        return lo
    }
}
