package com.baccours.nyx

import com.baccours.nyx.service.NightLightController
import com.baccours.nyx.service.SecureSettings
import com.baccours.nyx.service.SnapshotStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightLightControllerTest {

    private val activated = NightLightController.KEY_ACTIVATED
    private val temperature = NightLightController.KEY_TEMPERATURE

    /** In-memory stand-in for Settings.Secure that logs every write, in order. */
    private class FakeSettings(initial: Map<String, String> = emptyMap()) : SecureSettings {
        val values = initial.toMutableMap()
        val writes = mutableListOf<String>()
        var denied = false
        var rejectInts = false

        override fun getString(key: String) = values[key]
        override fun putInt(key: String, value: Int): Boolean {
            if (denied) throw SecurityException("revoked")
            if (rejectInts) return false
            values[key] = value.toString(); writes += "$key=$value"; return true
        }
        override fun putString(key: String, value: String): Boolean {
            if (denied) throw SecurityException("revoked")
            values[key] = value; writes += "$key=$value"; return true
        }
    }

    /** Survives "process death": the tests share one instance between controllers. */
    private class FakeStore : SnapshotStore {
        override var active = false
        override var prevActivated: String? = null
        override var prevTemperature: String? = null
        override fun save(prevActivated: String?, prevTemperature: String?) {
            active = true; this.prevActivated = prevActivated; this.prevTemperature = prevTemperature
        }
        override fun clear() { active = false; prevActivated = null; prevTemperature = null }
    }

    @Test
    fun userHadNightLightOff_isRestoredToOff_withTheirTemperature() {
        val s = FakeSettings(mapOf(activated to "0", temperature to "3500"))
        val c = NightLightController(s, FakeStore())

        assertTrue(c.apply(2800, allowed = true))
        assertEquals("1", s.values[activated]); assertEquals("2800", s.values[temperature])

        assertTrue(c.apply(null, allowed = true))
        assertEquals("0", s.values[activated]); assertEquals("3500", s.values[temperature])
        assertTrue(!c.isHolding)
    }

    @Test
    fun userHadNightLightOn_staysOn_atTheirOwnTemperature() {
        val s = FakeSettings(mapOf(activated to "1", temperature to "3000"))
        val c = NightLightController(s, FakeStore())
        c.apply(2700, allowed = true)
        c.apply(null, allowed = true)
        assertEquals("1", s.values[activated]); assertEquals("3000", s.values[temperature])
    }

    @Test
    fun restoreOrder_neverFlashesTheWrongTemperature() {
        val off = FakeSettings(mapOf(activated to "0", temperature to "3500"))
        NightLightController(off, FakeStore()).also { it.apply(2800, true); off.writes.clear(); it.apply(null, true) }
        assertEquals(listOf("$activated=0", "$temperature=3500"), off.writes) // switch off, then tidy

        val on = FakeSettings(mapOf(activated to "1", temperature to "3000"))
        NightLightController(on, FakeStore()).also { it.apply(2800, true); on.writes.clear(); it.apply(null, true) }
        assertEquals(listOf("$temperature=3000", "$activated=1"), on.writes) // temperature first, stays on
    }

    @Test
    fun neverSetBefore_isTurnedOff_andTemperatureIsLeftAlone() {
        val s = FakeSettings() // both settings absent
        val c = NightLightController(s, FakeStore())
        c.apply(2800, allowed = true)
        c.apply(null, allowed = true)
        assertEquals("0", s.values[activated])
        assertEquals("2800", s.values[temperature]) // nothing to restore it to
    }

    @Test
    fun changingTemperature_doesNotReplaceTheOriginalSnapshot() {
        val s = FakeSettings(mapOf(activated to "0", temperature to "3500"))
        val c = NightLightController(s, FakeStore())
        c.apply(3000, allowed = true)
        c.apply(2800, allowed = true) // a later snapshot would wrongly record "1" / "3000"
        c.close()
        assertEquals("0", s.values[activated]); assertEquals("3500", s.values[temperature])
    }

    @Test
    fun afterProcessDeath_aNewControllerStillRestoresTheOriginalState() {
        val s = FakeSettings(mapOf(activated to "0", temperature to "3500"))
        val store = FakeStore()
        NightLightController(s, store).apply(2800, allowed = true) // ...then the process dies
        val revived = NightLightController(s, store)
        assertTrue(revived.isHolding)
        revived.close()
        assertEquals("0", s.values[activated]); assertEquals("3500", s.values[temperature])
    }

    @Test
    fun afterClose_nothingIsWrittenAnymore() {
        val s = FakeSettings(mapOf(activated to "0"))
        val c = NightLightController(s, FakeStore())
        c.close()
        s.writes.clear()
        assertEquals(false, c.apply(2800, allowed = true))
        assertTrue(s.writes.isEmpty())
    }

    @Test
    fun notAllowed_changesNothing_andDoesNotSnapshot() {
        val s = FakeSettings(mapOf(activated to "0"))
        val store = FakeStore()
        val c = NightLightController(s, store)
        assertEquals(false, c.apply(2800, allowed = false))
        assertTrue(s.writes.isEmpty()); assertTrue(!store.active)
    }

    @Test
    fun permissionRevokedMidRun_doesNotCrash_andClearsTheSnapshot() {
        val s = FakeSettings(mapOf(activated to "0", temperature to "3500"))
        val store = FakeStore()
        val c = NightLightController(s, store)
        c.apply(2800, allowed = true)
        s.denied = true
        assertEquals(false, c.apply(2600, allowed = true)) // write denied -> false, not an exception
        c.close()                                           // restore denied -> swallowed
        assertTrue(!store.active)
    }

    @Test
    fun rejectedWrite_isReportedAsFailure() {
        val s = FakeSettings(mapOf(activated to "0")).also { it.rejectInts = true }
        assertEquals(false, NightLightController(s, FakeStore()).apply(2800, allowed = true))
    }

    @Test
    fun releasingWhenNothingWasBorrowed_isANoOp() {
        val s = FakeSettings(mapOf(activated to "1", temperature to "3000"))
        val c = NightLightController(s, FakeStore())
        assertEquals(true, c.apply(null, allowed = true))
        assertTrue(s.writes.isEmpty()) // the user's own Night Light is never touched
    }
}
