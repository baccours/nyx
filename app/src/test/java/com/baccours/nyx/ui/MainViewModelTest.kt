package com.baccours.nyx.ui

import com.baccours.nyx.data.FilterController
import com.baccours.nyx.data.FilterStateStore
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
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

    private class FakeFilterStore : FilterStateStore {
        override fun isFilterEnabled() = true
        override fun saveFilterEnabled(enabled: Boolean) = Unit
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val repository = OverlayRepository(FakeStore(), scope, persistDebounce = 1.hours)
    private val filter = FilterController(FakeFilterStore())
    private var systemChecks = 0
    private var enabledInSystem = false

    // Lazy: the first access makes the initial system check, so tests set the scene first.
    private val viewModel by lazy {
        MainViewModel(
            repository,
            filter,
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
        filter.setServiceRunning(true)
        assertEquals(UiState.FilterOn(OverlaySettings.DEFAULT), viewModel.uiState.value)
        viewModel.refresh()
        assertEquals(0, systemChecks)
    }

    @Test
    fun afterTheServiceStops_theSystemIsAskedAgain_notTrustedFromBefore() {
        enabledInSystem = true
        viewModel.uiState.value                        // created: asks once, "enabled"
        filter.setServiceRunning(true)                 // running is enabled by definition: no ask
        enabledInSystem = false                        // e.g. disabled via the accessibility shortcut
        filter.setServiceRunning(false)                // stopped: asks again, doesn't trust the old "yes"
        assertEquals(UiState.NeedsPermission(), viewModel.uiState.value)
        assertEquals(2, systemChecks)
    }

    @Test
    fun overlayError_showsThePermissionCardWithTheReason() {
        enabledInSystem = true // stale: the service turned itself off after failing
        filter.onOverlayFailed("BadTokenException: no token")
        assertEquals(UiState.NeedsPermission("BadTokenException: no token"), viewModel.uiState.value)
    }

    @Test
    fun setSlider_updatesOnlyThatSetting() {
        viewModel.setSlider(SliderKind.DIMMING, 50)
        assertEquals(OverlaySettings.DEFAULT.copy(dimIntensity = 0.5f), repository.settings.value)
    }
}
