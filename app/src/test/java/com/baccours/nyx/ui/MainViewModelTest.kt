package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlayController
import com.baccours.nyx.data.OverlayStateStore
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.data.OverlaySettingsStore
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

    private class FakeStore : OverlaySettingsStore {
        override fun loadOverlaySettings() = OverlaySettings.DEFAULT
        override fun saveOverlaySettings(settings: OverlaySettings) = Unit
    }

    private class FakeOverlayStore : OverlayStateStore {
        override fun isOverlayActive() = true
        override fun saveOverlayState(active: Boolean) = Unit
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val repository = OverlayRepository(FakeStore(), scope, persistDebounce = 1.hours)
    private val controller = OverlayController(FakeOverlayStore())
    private var systemChecks = 0
    private var enabledInSystem = false

    // Lazy: the first access makes the initial system check, so tests set the scene first.
    private val viewModel by lazy {
        MainViewModel(
            repository,
            controller,
            isServiceEnabledInSystem = {
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
        controller.onServiceStarted()
        assertEquals(UiState.OverlayOn(OverlaySettings.DEFAULT), viewModel.uiState.value)
        viewModel.refresh()
        assertEquals(0, systemChecks)
    }

    @Test
    fun afterTheServiceStops_theSystemIsAskedAgain_notTrustedFromBefore() {
        enabledInSystem = true
        viewModel.uiState.value                        // created: asks once, "enabled"
        controller.onServiceStarted()                  // running is enabled by definition: no ask
        enabledInSystem = false                        // e.g. disabled via the accessibility shortcut
        controller.onServiceStopped()                  // stopped: asks again, doesn't trust the old "yes"
        assertEquals(UiState.NeedsPermission(), viewModel.uiState.value)
        assertEquals(2, systemChecks)
    }

    @Test
    fun overlayError_showsThePermissionCardWithTheReason() {
        enabledInSystem = true // stale: the service turned itself off after failing
        controller.onOverlayFailed("BadTokenException: no token")
        assertEquals(UiState.NeedsPermission("BadTokenException: no token"), viewModel.uiState.value)
    }

    @Test
    fun setSlider_updatesOnlyThatSetting() {
        viewModel.setSlider(SliderType.DIM_INTENSITY, 50)
        assertEquals(OverlaySettings.DEFAULT.copy(dimIntensity = 0.5f), repository.settings.value)
    }
}
