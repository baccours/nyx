package com.baccours.nyx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baccours.nyx.AppContainer
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.data.OverlaySettingsRepository
import com.baccours.nyx.overlay.OverlayControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MainViewModel(
    private val repository: OverlaySettingsRepository,
    private val overlayControls: OverlayControls,
    private val themeManager: ThemeManager,
    /** Whether the accessibility service is enabled in system settings. Must not hold an Activity. */
    private val isServiceEnabledInSystem: () -> Boolean,
    /** Emits whenever the system's enabled-services setting changes. */
    systemChanges: Flow<Unit> = emptyFlow(),
    /** Owned by this ViewModel: canceled in [onCleared]. */
    private val scope: CoroutineScope,
    /** How long "enabled but not connected" may last before it is reported as [UiState.NotResponding]. */
    connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT
) : ViewModel() {

    private val systemServiceEnabled: StateFlow<Boolean> = systemChanges
        .onStart { emit(Unit) }
        .map { isServiceEnabledInSystem() }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), isServiceEnabledInSystem())

    private val connectTimedOut: Flow<Boolean> = channelFlow {
        combine(overlayControls.serviceRunning, systemServiceEnabled) { running, enabled -> enabled && !running }
            .distinctUntilChanged()
            .collectLatest { waiting ->
                send(false)
                if (waiting) {
                    delay(connectTimeout)
                    send(true)
                }
            }
    }

    /** Screen mode. Always current, so [StateFlow.value] doubles as the synchronous first-frame snapshot. */
    val uiState: StateFlow<UiState> = combine(
        overlayControls.serviceRunning,
        systemServiceEnabled,
        overlayControls.overlayActive,
        overlayControls.overlayError,
        connectTimedOut,
        ::deriveUiState
    ).stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), currentState())

    /** Slider values, separate from [uiState] so a slider tick doesn't re-render the cards. */
    val settings: StateFlow<OverlaySettings> = repository.settings

    // replay = 0: dropped if nobody is collecting. One slot of buffer so tryEmit never fails while a collector is attached.
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)

    /** One-shot effects. Not replayed: an event emitted while the UI is stopped is dropped. */
    val events: Flow<UiEvent> = _events.asSharedFlow()

    /** Applies a slider's [progress] to its setting. */
    fun setSlider(type: SliderType, progress: Int) = repository.update { type.withProgress(it, progress) }

    /** The toggle was flipped: switch the overlay, or send the user to enable the service first. */
    fun onToggle(checked: Boolean) {
        if (overlayControls.serviceRunning.value) {
            overlayControls.setOverlayActive(checked)
        } else {
            _events.tryEmit(UiEvent.OpenAccessibilitySettings)
        }
    }

    fun requestDisableService() = overlayControls.requestDisableService()

    /** Switches to the opposite of the theme currently shown. */
    fun onThemeToggle(isNight: Boolean) = themeManager.set(if (isNight) ThemeMode.LIGHT else ThemeMode.DARK)

    fun flush() = repository.flush()

    override fun onCleared() {
        scope.cancel()
    }

    private fun currentState() = deriveUiState(
        overlayControls.serviceRunning.value,
        systemServiceEnabled.value,
        overlayControls.overlayActive.value,
        overlayControls.overlayError.value,
        false
    )

    companion object {
        val DEFAULT_CONNECT_TIMEOUT = 8.seconds
        const val STOP_TIMEOUT_MS = 5_000L

        /** Captures only the app-scoped [container], never an Activity. */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val monitor = container.serviceEnabledMonitor
                MainViewModel(
                    repository = container.repository,
                    overlayControls = container.overlayController,
                    themeManager = container.themeManager,
                    isServiceEnabledInSystem = monitor::isEnabled,
                    systemChanges = monitor.changes(),
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                )
            }
        }
    }
}
