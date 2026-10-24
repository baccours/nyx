package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlayController
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.data.OverlaySettingsStore
import com.baccours.nyx.data.OverlayStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/** Pins how often the "is the service enabled in system settings" check is made, and the toggle logic. */
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
    private val systemChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var systemChecks = 0
    private var enabledInSystem = false
    private var connectTimeout: Duration = 1.hours

    // Lazy: the first access makes the initial system check, so tests set the scene first.
    private val viewModel by lazy {
        MainViewModel(
            repository,
            controller,
            isServiceEnabledInSystem = {
                systemChecks++
                enabledInSystem
            },
            systemChanges = systemChanges,
            scope = scope,
            connectTimeout = connectTimeout
        )
    }

    @After
    fun tearDown() = scope.cancel()

    private fun nextEvent() = runBlocking { withTimeoutOrNull(100.milliseconds) { viewModel.events.first() } }

    @Test
    fun repeatedDerivation_asksTheSystemOnce() {
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
    fun settingsChange_asksAgain() {
        viewModel.uiState.value // created: asks once, "not enabled"
        enabledInSystem = true
        systemChanges.tryEmit(Unit)
        assertEquals(UiState.Connecting, viewModel.uiState.value)
        assertEquals(2, systemChecks)
    }

    @Test
    fun runningService_showsControls_whateverTheSystemSays() {
        controller.onServiceStarted()
        assertEquals(UiState.OverlayOn, viewModel.uiState.value)
    }

    @Test
    fun enabledButNeverConnecting_becomesNotResponding_afterTheTimeout() {
        enabledInSystem = true
        connectTimeout = Duration.ZERO
        assertEquals(UiState.NotResponding, viewModel.uiState.value)
    }

    @Test
    fun connecting_beforeTheTimeout_staysConnecting() {
        enabledInSystem = true
        assertEquals(UiState.Connecting, viewModel.uiState.value)
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
        assertEquals(OverlaySettings.DEFAULT.copy(dimIntensity = 0.5f), viewModel.settings.value)
        assertEquals(OverlaySettings.DEFAULT.copy(dimIntensity = 0.5f), repository.settings.value)
    }

    @Test
    fun toggle_whileRunning_switchesTheOverlay() {
        controller.onServiceStarted()
        viewModel.onToggle(false)
        assertFalse(controller.overlayActive.value)
        assertNull(nextEvent())
        viewModel.onToggle(true)
        assertTrue(controller.overlayActive.value)
    }

    @Test
    fun toggle_whileNotRunning_opensAccessibilitySettings() {
        viewModel.onToggle(true)
        assertEquals(UiEvent.OpenAccessibilitySettings, nextEvent())
        assertTrue(controller.overlayActive.value) // untouched
    }
}
