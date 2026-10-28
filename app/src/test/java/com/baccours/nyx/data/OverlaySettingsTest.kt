package com.baccours.nyx.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlaySettingsTest {

    @Test
    fun sanitized_clampsAndReplacesNonFinite() {
        val s = OverlaySettings(2f, Float.NaN, 50f, -1f).sanitized()
        assertEquals(1f, s.dimIntensity, 0f)
        assertEquals(OverlaySettings.DEFAULT.blueLightIntensity, s.blueLightIntensity, 0f)
        assertEquals(OverlaySettings.MIN_KELVIN, s.colorTemperature, 0f)
        assertEquals(0f, s.warmthBoost, 0f)
    }
}
