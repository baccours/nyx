package com.baccours.nyx

import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.data.ServiceStatus
import com.baccours.nyx.data.SettingsStore
import com.baccours.nyx.ui.MainViewModel
import com.baccours.nyx.ui.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours

/** Pins how often the (binder) "is the service enabled in system settings" check is made. */
class MainViewModelTest {

    private class FakeStore : SettingsStore {
        override fun load() = OverlaySettings.DEFAULT
        override fun save(settings: OverlaySettings) = Unit
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val status = ServiceStatus()
    private var systemChecks = 0
    private var enabledInSystem = false

    private val viewModel = MainViewModel(
        OverlayRepository(FakeStore(), scope, persistDebounce = 1.hours),
        status
    ) {
        systemChecks++
        enabledInSystem
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun repeatedDerivation_whileNotRunning_asksTheSystemOnce() {
        repeat(3) { viewModel.currentState() }
        assertEquals(1, systemChecks)
    }

    @Test
    fun refresh_asksAgain() {
        viewModel.currentState()
        enabledInSystem = true
        viewModel.refresh()
        assertEquals(UiState.Connecting, viewModel.currentState())
        assertEquals(2, systemChecks)
    }

    @Test
    fun runningService_neverAsksTheSystem() {
        status.setServiceRunning(true)
        viewModel.currentState()
        assertEquals(0, systemChecks)
    }

    @Test
    fun afterTheServiceStops_theSystemIsAskedAgain_notTrustedFromBefore() {
        enabledInSystem = true
        viewModel.currentState()                       // cached "enabled"
        status.setServiceRunning(true)
        viewModel.currentState()                       // running: cache dropped
        enabledInSystem = false                        // e.g. disabled via the accessibility shortcut
        status.setServiceRunning(false)
        assertEquals(UiState.NeedsPermission, viewModel.currentState())
        assertEquals(2, systemChecks)
    }
}
