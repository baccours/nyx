package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.data.OverlaySettingsRepository
import com.baccours.nyx.data.OverlaySettingsStore
import com.baccours.nyx.data.OverlayStateStore
import com.baccours.nyx.data.ThemeStore
import com.baccours.nyx.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** Pins how often the "is the service enabled in system settings" check is made, and the toggle logic. */
class MainViewModelTest {

    private class FakeStore : OverlaySettingsStore {
        override fun load() = OverlaySettings.DEFAULT
        override fun save(settings: OverlaySettings) = Unit
    }

    private class FakeOverlayStore : OverlayStateStore {
        override fun isActive() = true
        override fun save(active: Boolean) = Unit
    }

    private class FakeThemeStore : ThemeStore {
        var savedName: String? = null
        override fun load(): String? = savedName
        override fun save(name: String) {
            savedName = name
        }
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val themeStore = FakeThemeStore()
    private val themeManager = ThemeManager(themeStore)
    private val repository = OverlaySettingsRepository(FakeStore(), scope, persistDebounce = 1.hours)
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
            themeManager = themeManager,
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

    private fun collectEvents() =
        mutableListOf<UiEvent>().also { scope.launch { viewModel.events.toList(it) } } // scope is Unconfined

    @Test
    fun repeatedDerivation_asksTheSystemOnce() {
        repeat(3) { viewModel.uiState.value }
        assertEquals(1, systemChecks)
    }

    @Test
    fun settingsChange_asksAgain() {
        val collector = scope.launch { viewModel.uiState.collect {} }
        assertEquals(UiState.NeedsPermission(), viewModel.uiState.value)
        assertEquals(2, systemChecks)

        enabledInSystem = true
        systemChanges.tryEmit(Unit)
        assertEquals(UiState.Connecting, viewModel.uiState.value)
        assertEquals(3, systemChecks)
        collector.cancel()
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
        val collector = scope.launch { viewModel.uiState.collect {} }
        assertEquals(UiState.NotResponding, viewModel.uiState.value)
        collector.cancel()
    }

    @Test
    fun connecting_beforeTheTimeout_staysConnecting() {
        enabledInSystem = true
        val collector = scope.launch { viewModel.uiState.collect {} }
        assertEquals(UiState.Connecting, viewModel.uiState.value)
        collector.cancel()
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
        val events = collectEvents()
        controller.onServiceStarted()
        viewModel.onToggle(false)
        assertFalse(controller.overlayActive.value)
        assertTrue(events.isEmpty())
        viewModel.onToggle(true)
        assertTrue(controller.overlayActive.value)
    }

    @Test
    fun toggle_whileNotRunning_opensAccessibilitySettings() {
        val events = collectEvents()
        viewModel.onToggle(true)
        assertEquals(listOf(UiEvent.OpenAccessibilitySettings), events)
        assertTrue(controller.overlayActive.value) // untouched
    }

    @Test
    fun eventWithNoCollector_isNotReplayed() {
        viewModel.onToggle(true)
        assertTrue(collectEvents().isEmpty())
    }

    @Test
    fun themeToggle_switchesToTheOppositeMode() {
        viewModel.onThemeToggle(isNight = true)
        assertEquals(ThemeMode.LIGHT.name, themeStore.savedName)
        viewModel.onThemeToggle(isNight = false)
        assertEquals(ThemeMode.DARK.name, themeStore.savedName)
    }

    @Test
    fun themeReset_returnsToFollowingTheSystem() {
        viewModel.onThemeToggle(isNight = false)
        assertEquals(ThemeMode.DARK.name, themeStore.savedName)
        viewModel.onThemeReset()
        assertEquals(ThemeMode.SYSTEM.name, themeStore.savedName)
    }
}
