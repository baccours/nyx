package com.baccours.nyx

import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.overlay.OverlayColorCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayColorCalculatorTest {

    private fun alpha(argb: Int) = (argb ushr 24) and 0xFF
    private fun red(argb: Int) = (argb shr 16) and 0xFF
    private fun green(argb: Int) = (argb shr 8) and 0xFF
    private fun blue(argb: Int) = argb and 0xFF

    @Test
    fun noDimNoTint_isFullyTransparent() {
        val argb = OverlayColorCalculator.overlayColor(OverlaySettings(0f, 0f, 3400f))
        assertEquals(0, alpha(argb))
    }

    @Test
    fun pureDimming_isBlackWithAlphaCappedBelowOpaque() {
        val argb = OverlayColorCalculator.overlayColor(OverlaySettings(1f, 0f, 3400f))
        assertEquals(0, red(argb))
        assertEquals(0, green(argb))
        assertEquals(0, blue(argb))
        assertTrue("screen must never become fully black", alpha(argb) < 255)
        assertEquals((OverlayColorCalculator.MAX_DIM_OPACITY * 255).toInt(), alpha(argb))
    }

    @Test
    fun fullTintAtDaylight_isWhiteAtTintAlpha() {
        val argb = OverlayColorCalculator.overlayColor(OverlaySettings(0f, 1f, 6600f))
        assertEquals(180, alpha(argb))
        assertEquals(255, red(argb))
        assertEquals(255, green(argb))
        assertEquals(255, blue(argb))
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
        val argb = OverlayColorCalculator.overlayColor(OverlaySettings(5f, -3f, 99999f))
        assertTrue(alpha(argb) in 0..255)
    }

    @Test
    fun sanitized_clampsAndReplacesNonFinite() {
        val s = OverlaySettings(2f, Float.NaN, 50f).sanitized()
        assertEquals(1f, s.dimIntensity, 0f)
        assertEquals(OverlaySettings.DEFAULT.blueLightIntensity, s.blueLightIntensity, 0f)
        assertEquals(OverlaySettings.MIN_KELVIN, s.colorTemperature, 0f)
    }
}
