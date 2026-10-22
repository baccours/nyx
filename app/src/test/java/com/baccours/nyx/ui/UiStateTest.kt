package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {

    private val settings = OverlaySettings.DEFAULT

    private fun derive(
        running: Boolean,
        overlayActive: Boolean,
        enabledInSystem: Boolean,
        error: String? = null
    ) = deriveUiState(running, enabledInSystem, settings, overlayActive, error)

    @Test
    fun runningWithOverlay_showsControls() {
        assertEquals(UiState.OverlayOn(settings), derive(running = true, overlayActive = true, enabledInSystem = true))
    }

    @Test
    fun runningWithoutOverlay_isIdle() {
        assertEquals(UiState.ServiceIdle, derive(running = true, overlayActive = false, enabledInSystem = true))
    }

    @Test
    fun runningImpliesEnabled_evenIfTheSystemCheckIsStale() {
        assertEquals(UiState.ServiceIdle, derive(running = true, overlayActive = false, enabledInSystem = false))
    }

    @Test
    fun enabledButNotConnected_isConnecting_notAPermissionPrompt() {
        assertEquals(UiState.Connecting, derive(running = false, overlayActive = true, enabledInSystem = true))
    }

    @Test
    fun notEnabled_needsPermission() {
        assertEquals(UiState.NeedsPermission(), derive(running = false, overlayActive = true, enabledInSystem = false))
        assertEquals(UiState.NeedsPermission(), derive(running = false, overlayActive = false, enabledInSystem = false))
    }

    @Test
    fun overlayError_whileStopped_needsPermissionWithTheReason() {
        // Even if the system check is stale and still says "enabled".
        assertEquals(
            UiState.NeedsPermission("boom"),
            derive(running = false, overlayActive = true, enabledInSystem = true, error = "boom")
        )
    }

    @Test
    fun overlayError_isIgnoredWhileRunning() {
        assertEquals(
            UiState.OverlayOn(settings),
            derive(running = true, overlayActive = true, enabledInSystem = true, error = "stale")
        )
    }
}
