package com.baccours.nyx.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {

    private fun derive(
        running: Boolean,
        overlayActive: Boolean,
        enabledInSystem: Boolean,
        error: String? = null,
        timedOut: Boolean = false
    ) = deriveUiState(running, enabledInSystem, overlayActive, error, timedOut)

    @Test
    fun runningWithOverlay_showsControls() {
        assertEquals(UiState.OverlayOn, derive(running = true, overlayActive = true, enabledInSystem = true))
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
    fun enabledButNotConnected_afterTheTimeout_isNotResponding() {
        assertEquals(
            UiState.NotResponding,
            derive(running = false, overlayActive = true, enabledInSystem = true, timedOut = true)
        )
    }

    @Test
    fun timeout_isIgnoredWhileRunning_orWhenNotEnabled() {
        assertEquals(UiState.OverlayOn, derive(running = true, overlayActive = true, enabledInSystem = true, timedOut = true))
        assertEquals(
            UiState.NeedsPermission(),
            derive(running = false, overlayActive = true, enabledInSystem = false, timedOut = true)
        )
    }

    @Test
    fun notEnabled_needsPermission() {
        assertEquals(UiState.NeedsPermission(), derive(running = false, overlayActive = true, enabledInSystem = false))
        assertEquals(UiState.NeedsPermission(), derive(running = false, overlayActive = false, enabledInSystem = false))
    }

    @Test
    fun overlayError_whileStopped_needsPermissionWithTheReason() {
        // Even if the system check is stale and still says "enabled", or the timeout fired.
        assertEquals(
            UiState.NeedsPermission("boom"),
            derive(running = false, overlayActive = true, enabledInSystem = true, error = "boom")
        )
        assertEquals(
            UiState.NeedsPermission("boom"),
            derive(running = false, overlayActive = true, enabledInSystem = true, error = "boom", timedOut = true)
        )
    }

    @Test
    fun overlayError_isIgnoredWhileRunning() {
        assertEquals(
            UiState.OverlayOn,
            derive(running = true, overlayActive = true, enabledInSystem = true, error = "stale")
        )
    }
}
