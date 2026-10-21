package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SliderKindTest {

    private val default = OverlaySettings.DEFAULT

    @Test
    fun progress_roundTripsForEverySlider() {
        for (kind in SliderKind.entries) {
            for (progress in 0..SliderMapping.STEPS) {
                val settings = kind.withProgress(default, progress)
                assertEquals("$kind @ $progress", progress, kind.progressOf(settings))
            }
        }
    }

    @Test
    fun eachSlider_changesOnlyItsOwnSetting() {
        assertEquals(default.copy(dimIntensity = 0.5f), SliderKind.DIMMING.withProgress(default, 50))
        assertEquals(default.copy(blueLightIntensity = 0.5f), SliderKind.BLUE_LIGHT.withProgress(default, 50))
        assertEquals(default.copy(colorTemperature = 4000f), SliderKind.TEMPERATURE.withProgress(default, 50))
        assertEquals(default.copy(blackLevel = 0.5f), SliderKind.BLACK_LEVEL.withProgress(default, 50))
    }
}
