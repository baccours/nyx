package com.baccours.nyx

import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.overlay.OverlayColorCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OverlayColorCalculatorTest {

    private companion object {
        const val R = 16
        const val G = 8
        const val B = 0
        val DIMS = floatArrayOf(0f, 0.3f, 0.7f, 1f)
        val STRENGTHS = floatArrayOf(0f, 0.25f, 0.5f, 1f)
        val KELVINS = floatArrayOf(1000f, 2200f, 3400f, 5000f, 6500f)
        val BLACK_LEVELS = floatArrayOf(0f, 0.2f, 0.5f, 1f)
    }

    private fun alpha(argb: Int) = (argb ushr 24) and 0xFF
    private fun channel(argb: Int, shift: Int) = (argb shr shift) and 0xFF

    /** What a pixel of value [src] looks like after the overlay is alpha-blended on top. */
    private fun composite(argb: Int, src: Int, shift: Int): Float {
        val a = alpha(argb) / 255f
        return (1f - a) * src + a * channel(argb, shift)
    }

    private inline fun forEachSetting(block: (OverlaySettings) -> Unit) {
        for (d in DIMS) for (s in STRENGTHS) for (k in KELVINS) for (b in BLACK_LEVELS) {
            block(OverlaySettings(d, s, k, b))
        }
    }

    @Test
    fun noDimNoTint_isFullyTransparent() {
        val argb = OverlayColorCalculator.overlayColor(OverlaySettings(0f, 0f, 3400f))
        assertEquals(0, alpha(argb))
    }

    @Test
    fun pureDimming_isBlackWithAlphaCappedBelowOpaque() {
        val argb = OverlayColorCalculator.overlayColor(OverlaySettings(1f, 0f, 3400f))
        assertEquals(0, channel(argb, R))
        assertEquals(0, channel(argb, G))
        assertEquals(0, channel(argb, B))
        assertTrue("screen must never become fully black", alpha(argb) < 255)
        assertTrue(abs(alpha(argb) - OverlayColorCalculator.MAX_DIM_OPACITY * 255) <= 1f)
    }

    @Test
    fun blackLevelZero_keepsBlackPerfectlyBlack_forEverySetting() {
        for (d in DIMS) for (s in STRENGTHS) for (k in KELVINS) {
            val argb = OverlayColorCalculator.overlayColor(OverlaySettings(d, s, k, 0f))
            // Overlay colour is pure black, so black pixels (src 0) stay exactly 0.
            assertEquals("dim=$d s=$s K=$k", 0, argb and 0xFFFFFF)
        }
    }

    @Test
    fun blackLift_neverExceedsBudget() {
        forEachSetting { s ->
            val argb = OverlayColorCalculator.overlayColor(s)
            val budget = s.blackLevel * OverlayColorCalculator.MAX_BLACK_LIFT
            for (shift in intArrayOf(R, G, B)) {
                val lift = composite(argb, 0, shift)
                assertTrue("$s shift=$shift lift=$lift budget=$budget", lift <= budget + 1f)
            }
        }
    }

    @Test
    fun blueChannel_isNeverLifted_forWarmFilters() {
        // Below ~6600K blue is the lowest-gain channel: it is matched exactly, with no offset.
        for (d in DIMS) for (s in STRENGTHS) for (k in floatArrayOf(1000f, 2200f, 3400f, 5000f)) {
            for (b in BLACK_LEVELS) {
                val argb = OverlayColorCalculator.overlayColor(OverlaySettings(d, s, k, b))
                assertTrue("dim=$d s=$s K=$k b=$b", composite(argb, 0, B) <= 1f)
            }
        }
    }

    @Test
    fun blueLight_matchesTheFilterTarget_onWhite() {
        // The point of the filter: emitted blue at white == dimGain * (1 - s * (1 - kBlue)).
        for (d in DIMS) for (s in STRENGTHS) for (k in floatArrayOf(1000f, 2200f, 3400f, 5000f)) {
            for (b in BLACK_LEVELS) {
                val rgb = OverlayColorCalculator.kelvinToRgb(k.toInt())
                val peak = maxOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
                val kBlue = (rgb and 0xFF) / peak.toFloat()
                val dimGain = 1f - d * OverlayColorCalculator.MAX_DIM_OPACITY
                // The layer's gain is floored by the opacity cap (see MAX_DIM_OPACITY).
                val expected = (dimGain * (1f - s * (1f - kBlue))).coerceAtLeast(1f - OverlayColorCalculator.MAX_DIM_OPACITY)

                val argb = OverlayColorCalculator.overlayColor(OverlaySettings(d, s, k, b))
                val actual = composite(argb, 255, B) / 255f
                assertTrue("dim=$d s=$s K=$k b=$b expected=$expected actual=$actual", abs(expected - actual) < 0.015f)
            }
        }
    }

    @Test
    fun moreBlackLevel_givesWarmerWhite_andNeverLessRed() {
        val low = OverlayColorCalculator.overlayColor(OverlaySettings(0f, 1f, 3400f, 0.1f))
        val high = OverlayColorCalculator.overlayColor(OverlaySettings(0f, 1f, 3400f, 0.9f))
        assertTrue(composite(high, 255, R) >= composite(low, 255, R))
        // red-to-blue ratio on white is the warmth
        val warmthLow = composite(low, 255, R) / composite(low, 255, B)
        val warmthHigh = composite(high, 255, R) / composite(high, 255, B)
        assertTrue(warmthHigh > warmthLow)
    }

    @Test
    fun defaultsAtFullFilter_liftBlackFarLessThanTheOldBlend() {
        // The previous implementation produced about (180, 133, 95) here.
        val argb = OverlayColorCalculator.overlayColor(OverlaySettings(0f, 1f, 3400f, OverlaySettings.DEFAULT.blackLevel))
        val worst = maxOf(composite(argb, 0, R), composite(argb, 0, G), composite(argb, 0, B))
        assertTrue("black lift was $worst", worst <= 14f)
    }

    @Test
    fun neverOpaque_evenAtTheMostExtremeFilter() {
        forEachSetting { s ->
            val argb = OverlayColorCalculator.overlayColor(s)
            assertTrue("$s", alpha(argb) <= (OverlayColorCalculator.MAX_DIM_OPACITY * 255).toInt() + 1)
        }
        // 1000K has no blue at all: the target gain is 0, which must not become a solid screen.
        val extreme = OverlayColorCalculator.overlayColor(OverlaySettings(0f, 1f, 1000f, 0f))
        assertTrue(alpha(extreme) < 255)
    }

    @Test
    fun candlelight_hasNoBlue() {
        val rgb = OverlayColorCalculator.kelvinToRgb(1000)
        assertEquals(255, (rgb shr 16) and 0xFF)
        assertEquals(0, rgb and 0xFF)
    }

    @Test
    fun warmerTemperature_hasLessBlueThanCooler() {
        val warm = OverlayColorCalculator.kelvinToRgb(2500) and 0xFF
        val cool = OverlayColorCalculator.kelvinToRgb(5500) and 0xFF
        assertTrue(warm < cool)
    }

    @Test
    fun outOfRangeInput_isClampedNotCrashing() {
        val argb = OverlayColorCalculator.overlayColor(OverlaySettings(5f, -3f, 99999f, 9f))
        assertTrue(alpha(argb) in 0..255)
    }

    @Test
    fun sanitized_clampsAndReplacesNonFinite() {
        val s = OverlaySettings(2f, Float.NaN, 50f, -1f).sanitized()
        assertEquals(1f, s.dimIntensity, 0f)
        assertEquals(OverlaySettings.DEFAULT.blueLightIntensity, s.blueLightIntensity, 0f)
        assertEquals(OverlaySettings.MIN_KELVIN, s.colorTemperature, 0f)
        assertEquals(0f, s.blackLevel, 0f)
    }
}
