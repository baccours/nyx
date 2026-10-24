package com.baccours.nyx.state

import com.baccours.nyx.data.OverlayStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayControllerTest {

    private class FakeStore(var enabled: Boolean = true) : OverlayStateStore {
        override fun isActive() = enabled
        override fun save(active: Boolean) {
            this.enabled = active
        }
    }

    private val store = FakeStore()
    private val controller = OverlayController(store)

    @Test
    fun setOverlayActive_updatesStateAndPersists() {
        controller.setOverlayActive(false)
        assertFalse(controller.overlayActive.value)
        assertFalse(store.enabled)
    }

    @Test
    fun connecting_restoresTheStoredChoice() {
        store.enabled = false
        controller.onServiceConnecting()
        assertFalse(controller.overlayActive.value)
    }

    @Test
    fun connecting_clearsThePreviousError() {
        controller.onOverlayFailed("boom")
        assertEquals("boom", controller.overlayError.value)
        controller.onServiceConnecting()
        assertNull(controller.overlayError.value)
    }

    @Test
    fun orderlyShutdown_resetsTheChoiceToOn() {
        controller.setOverlayActive(false)
        controller.onServiceDestroyed()
        assertTrue(controller.overlayActive.value)
        assertTrue(store.enabled)
    }
}
