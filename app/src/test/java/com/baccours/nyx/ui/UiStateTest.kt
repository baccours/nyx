package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {

    private val settings = OverlaySettings.DEFAULT

    private fun derive(
        running: Boolean,
        filterEnabled: Boolean,
        enabledInSystem: Boolean,
        error: String? = null
    ) = deriveUiState(running, filterEnabled, settings, enabledInSystem, error)

    @Test
    fun runningWithFilter_showsControls() {
        assertEquals(UiState.FilterOn(settings), derive(running = true, filterEnabled = true, enabledInSystem = true))
    }

    @Test
    fun runningWithoutFilter_isIdle() {
        assertEquals(UiState.ServiceIdle, derive(running = true, filterEnabled = false, enabledInSystem = true))
    }

    @Test
    fun runningImpliesEnabled_evenIfTheSystemCheckIsStale() {
        assertEquals(UiState.ServiceIdle, derive(running = true, filterEnabled = false, enabledInSystem = false))
    }

    @Test
    fun enabledButNotConnected_isConnecting_notAPermissionPrompt() {
        assertEquals(UiState.Connecting, derive(running = false, filterEnabled = true, enabledInSystem = true))
    }

    @Test
    fun notEnabled_needsPermission() {
        assertEquals(UiState.NeedsPermission(), derive(running = false, filterEnabled = true, enabledInSystem = false))
        assertEquals(UiState.NeedsPermission(), derive(running = false, filterEnabled = false, enabledInSystem = false))
    }

    @Test
    fun overlayError_whileStopped_needsPermissionWithTheReason() {
        // Even if the system check is stale and still says "enabled".
        assertEquals(
            UiState.NeedsPermission("boom"),
            derive(running = false, filterEnabled = true, enabledInSystem = true, error = "boom")
        )
    }

    @Test
    fun overlayError_isIgnoredWhileRunning() {
        assertEquals(
            UiState.FilterOn(settings),
            derive(running = true, filterEnabled = true, enabledInSystem = true, error = "stale")
        )
    }
}
