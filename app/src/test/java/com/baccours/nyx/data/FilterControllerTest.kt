package com.baccours.nyx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterControllerTest {

    private class FakeStore(var enabled: Boolean = true) : FilterStateStore {
        override fun isFilterEnabled() = enabled
        override fun saveFilterEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    private val store = FakeStore()
    private val controller = FilterController(store)

    @Test
    fun setFilterEnabled_updatesStateAndPersists() {
        controller.setFilterEnabled(false)
        assertFalse(controller.filterEnabled.value)
        assertFalse(store.enabled)
    }

    @Test
    fun connecting_restoresTheStoredChoice() {
        store.enabled = false
        controller.onServiceConnecting()
        assertFalse(controller.filterEnabled.value)
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
        controller.setFilterEnabled(false)
        controller.onServiceDestroyed()
        assertTrue(controller.filterEnabled.value)
        assertTrue(store.enabled)
    }
}
