package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.data.ServiceStatus
import com.baccours.nyx.data.SettingsStore
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

    // Lazy: the first access makes the initial system check, so tests set the scene first.
    private val viewModel by lazy {
        MainViewModel(
            OverlayRepository(FakeStore(), scope, persistDebounce = 1.hours),
            status,
            isEnabledInSystem = {
                systemChecks++
                enabledInSystem
            },
            scope = scope
        )
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun repeatedDerivation_whileNotRunning_asksTheSystemOnce() {
        repeat(3) { viewModel.uiState.value }
        assertEquals(1, systemChecks)
    }

    @Test
    fun refresh_asksAgain() {
        enabledInSystem = true
        viewModel.refresh()
        assertEquals(UiState.Connecting, viewModel.uiState.value)
        assertEquals(2, systemChecks)
    }

    @Test
    fun runningService_neverAsksTheSystem() {
        status.setServiceRunning(true)
        assertEquals(UiState.FilterOn(OverlaySettings.DEFAULT), viewModel.uiState.value)
        viewModel.refresh()
        assertEquals(0, systemChecks)
    }

    @Test
    fun afterTheServiceStops_theSystemIsAskedAgain_notTrustedFromBefore() {
        enabledInSystem = true
        viewModel.uiState.value                        // created: asks once, "enabled"
        status.setServiceRunning(true)                 // running is enabled by definition: no ask
        enabledInSystem = false                        // e.g. disabled via the accessibility shortcut
        status.setServiceRunning(false)                // stopped: asks again, doesn't trust the old "yes"
        assertEquals(UiState.NeedsPermission, viewModel.uiState.value)
        assertEquals(2, systemChecks)
    }
}
