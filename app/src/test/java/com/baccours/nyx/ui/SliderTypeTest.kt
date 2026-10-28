package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SliderTypeTest {

    private val default = OverlaySettings.DEFAULT
    private val minK = OverlaySettings.MIN_KELVIN
    private val maxK = OverlaySettings.MAX_KELVIN

    @Test
    fun progress_roundTripsForEverySlider() {
        for (type in SliderType.entries) {
            for (progress in 0..type.steps) {
                val settings = type.withProgress(default, progress)
                assertEquals("$type @ $progress", progress, type.progressOf(settings))
            }
        }
    }

    @Test
    fun eachSlider_changesOnlyItsOwnSetting() {
        assertEquals(default.copy(dimIntensity = 0.5f), SliderType.DIM_INTENSITY.withProgress(default, 50))
        assertEquals(default.copy(blueLightIntensity = 0.5f), SliderType.BLUE_LIGHT.withProgress(default, 50))
        // 100 K per step: step 25 is 1000 + 25 * 100 = 3500 K (the default is step 24 = 3400 K).
        assertEquals(default.copy(colorTemperature = 3500f), SliderType.COLOR_TEMPERATURE.withProgress(default, 25))
        assertEquals(default.copy(warmthBoost = 0.5f), SliderType.WARMTH_BOOST.withProgress(default, 50))
    }

    @Test
    fun temperatureEndpoints() {
        assertEquals(minK, SliderType.COLOR_TEMPERATURE.withProgress(default, 0).colorTemperature, 0.001f)
        assertEquals(maxK, SliderType.COLOR_TEMPERATURE.withProgress(default, SliderType.COLOR_TEMPERATURE.steps).colorTemperature, 0.001f)
    }

    @Test
    fun outOfRange_isClamped() {
        assertEquals(0, SliderType.DIM_INTENSITY.progressOf(default.copy(dimIntensity = -1f)))
        assertEquals(SliderType.DIM_INTENSITY.steps, SliderType.DIM_INTENSITY.progressOf(default.copy(dimIntensity = 7f)))
        assertEquals(0, SliderType.COLOR_TEMPERATURE.progressOf(default.copy(colorTemperature = 10f)))
        assertEquals(default.copy(dimIntensity = 1f), SliderType.DIM_INTENSITY.withProgress(default, 1000))
    }

    @Test
    fun displayValue_isPercentOrKelvin() {
        for (progress in 0..SliderType.DIM_INTENSITY.steps) {
            assertEquals(progress, SliderType.DIM_INTENSITY.displayValue(progress))
        }
        assertEquals(minK.toInt(), SliderType.COLOR_TEMPERATURE.displayValue(0))
        assertEquals(maxK.toInt(), SliderType.COLOR_TEMPERATURE.displayValue(SliderType.COLOR_TEMPERATURE.steps))
    }
}
