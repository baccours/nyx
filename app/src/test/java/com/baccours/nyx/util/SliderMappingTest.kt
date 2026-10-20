package com.baccours.nyx.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderMappingTest {

    @Test
    fun percentRoundTrip_isLossless_forEveryStep() {
        for (p in 0..SliderMapping.STEPS) {
            assertEquals("step $p", p, SliderMapping.fractionToProgress(SliderMapping.progressToFraction(p)))
        }
    }

    @Test
    fun kelvinRoundTrip_isLossless_forEveryStep() {
        for (p in 0..SliderMapping.STEPS) {
            assertEquals("step $p", p, SliderMapping.kelvinToProgress(SliderMapping.progressToKelvin(p)))
        }
    }

    @Test
    fun kelvinEndpoints() {
        assertEquals(1000f, SliderMapping.progressToKelvin(0), 0.001f)
        assertEquals(7000f, SliderMapping.progressToKelvin(100), 0.001f)
    }

    @Test
    fun outOfRange_isClamped() {
        assertEquals(0, SliderMapping.fractionToProgress(-1f))
        assertEquals(100, SliderMapping.fractionToProgress(7f))
        assertEquals(0, SliderMapping.kelvinToProgress(10f))
    }
}
