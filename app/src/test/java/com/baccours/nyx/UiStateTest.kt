package com.baccours.nyx

import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.ui.UiState
import com.baccours.nyx.ui.deriveUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {

    private val settings = OverlaySettings.DEFAULT

    private fun derive(running: Boolean, filterEnabled: Boolean, enabledInSystem: Boolean) =
        deriveUiState(running, filterEnabled, settings) { enabledInSystem }

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
    fun runningService_neverQueriesTheSystem() {
        deriveUiState(true, true, settings) { error("must not be called") }
        deriveUiState(true, false, settings) { error("must not be called") }
    }

    @Test
    fun enabledButNotConnected_isConnecting_notAPermissionPrompt() {
        assertEquals(UiState.Connecting, derive(running = false, filterEnabled = true, enabledInSystem = true))
    }

    @Test
    fun notEnabled_needsPermission() {
        assertEquals(UiState.NeedsPermission, derive(running = false, filterEnabled = true, enabledInSystem = false))
        assertEquals(UiState.NeedsPermission, derive(running = false, filterEnabled = false, enabledInSystem = false))
    }
}
