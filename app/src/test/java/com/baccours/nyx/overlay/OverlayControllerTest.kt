package com.baccours.nyx.overlay

import com.baccours.nyx.data.OverlayStateStore
import org.junit.Assert
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
        Assert.assertFalse(controller.overlayActive.value)
        Assert.assertFalse(store.enabled)
    }

    @Test
    fun connecting_restoresTheStoredChoice() {
        store.enabled = false
        controller.onServiceConnecting()
        Assert.assertFalse(controller.overlayActive.value)
    }

    @Test
    fun connecting_clearsThePreviousError() {
        controller.onOverlayFailed("boom")
        Assert.assertEquals("boom", controller.overlayError.value)
        controller.onServiceConnecting()
        Assert.assertNull(controller.overlayError.value)
    }

    @Test
    fun orderlyShutdown_resetsTheChoiceToOn() {
        controller.setOverlayActive(false)
        controller.onServiceDestroyed()
        Assert.assertTrue(controller.overlayActive.value)
        Assert.assertTrue(store.enabled)
    }
}