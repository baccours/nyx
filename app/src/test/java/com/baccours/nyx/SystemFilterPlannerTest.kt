package com.baccours.nyx

import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.overlay.SystemFilterPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SystemFilterPlannerTest {

    private val caps = SystemFilterPlanner.Capabilities() // AOSP default 2596..4082 K

    private fun plan(strength: Float, kelvin: Float, dim: Float = 0f, c: SystemFilterPlanner.Capabilities = caps) =
        SystemFilterPlanner.plan(OverlaySettings(dim, strength, kelvin), c)

    @Test
    fun noBlueLightFilter_meansNightLightIsNotUsed() {
        assertEquals(null, plan(strength = 0f, kelvin = 1000f).nightLightKelvin)
    }

    @Test
    fun neutralTemperature_meansNightLightIsNotUsed_evenAtFullStrength() {
        // 6600K is the neutral white point: the filter would not change anything.
        assertEquals(null, plan(strength = 1f, kelvin = 6600f).nightLightKelvin)
    }

    @Test
    fun veryMildRequest_isTreatedAsOff_insteadOfJumpingToTheMildestNightLight() {
        assertEquals(null, plan(strength = 0.05f, kelvin = 3400f).nightLightKelvin)
    }

    @Test
    fun strongestFilter_usesTheWarmestSupportedTemperature() {
        assertEquals(caps.minKelvin, plan(strength = 1f, kelvin = 1000f).nightLightKelvin)
    }

    @Test
    fun temperatureAlwaysStaysInsideTheDeviceRange() {
        for (s in 0..20) for (k in 1000..7000 step 250) {
            val t = plan(s / 20f, k.toFloat()).nightLightKelvin ?: continue
            assertTrue("s=$s k=$k t=$t", t in caps.minKelvin..caps.maxKelvin)
        }
    }

    @Test
    fun morePressure_neverMakesNightLightColder() {
        for (k in floatArrayOf(1000f, 2000f, 3400f)) {
            var previous = Int.MAX_VALUE
            for (s in 0..20) {
                val t = plan(s / 20f, k).nightLightKelvin ?: continue
                assertTrue("k=$k s=$s t=$t previous=$previous", t <= previous)
                previous = t
            }
        }
    }

    @Test
    fun blueRemovedByNightLight_matchesTheRequestedCut_whenInReach() {
        // Pick requests Night Light can actually deliver and check the blue lands close to target.
        for ((s, k) in listOf(0.6f to 2000f, 0.8f to 2500f, 0.7f to 3000f, 1f to 3500f)) {
            val settings = OverlaySettings(0f, s, k)
            val t = SystemFilterPlanner.plan(settings, caps).nightLightKelvin
            val target = 1f - SystemFilterPlanner.requestedBlueCut(settings)
            val inReach = target >= SystemFilterPlanner.blueGain(caps.minKelvin) &&
                    target <= SystemFilterPlanner.blueGain(caps.maxKelvin)
            if (inReach) {
                assertTrue("s=$s k=$k t=$t", t != null)
                assertTrue("s=$s k=$k target=$target actual=${SystemFilterPlanner.blueGain(t!!)}",
                    abs(SystemFilterPlanner.blueGain(t) - target) < 0.01f)
            }
        }
    }

    @Test
    fun customDeviceRange_isRespected() {
        val wide = SystemFilterPlanner.Capabilities(minKelvin = 1500, maxKelvin = 5000)
        assertEquals(1500, plan(strength = 1f, kelvin = 1000f, c = wide).nightLightKelvin)
        val narrow = SystemFilterPlanner.Capabilities(minKelvin = 3000, maxKelvin = 3200)
        val t = plan(strength = 1f, kelvin = 1000f, c = narrow).nightLightKelvin
        assertEquals(3000, t)
    }

    @Test
    fun dimming_isPassedThroughUntouched() {
        assertEquals(0.4f, plan(strength = 0.5f, kelvin = 2500f, dim = 0.4f).dimIntensity, 0.0001f)
    }

    @Test
    fun garbageInput_isSanitized() {
        val p = SystemFilterPlanner.plan(OverlaySettings(9f, Float.NaN, -50f), caps)
        assertEquals(1f, p.dimIntensity, 0f)
        assertTrue(p.nightLightKelvin == null || p.nightLightKelvin!! in caps.minKelvin..caps.maxKelvin)
    }
}
