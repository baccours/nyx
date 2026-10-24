package com.baccours.nyx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baccours.nyx.AppContainer
import com.baccours.nyx.data.OverlayControls
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MainViewModel(
    private val repository: OverlayRepository,
    private val overlayControls: OverlayControls,
    /** Whether the accessibility service is enabled in system settings. Must not hold an Activity. */
    private val isServiceEnabledInSystem: () -> Boolean,
    /** Emits whenever the system's enabled-services setting changes. */
    systemChanges: Flow<Unit> = emptyFlow(),
    /** Owned by this ViewModel: canceled in [onCleared]. */
    private val scope: CoroutineScope,
    /** How long "enabled but not connected" may last before it is reported as [UiState.NotResponding]. */
    connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT
) : ViewModel() {

    private val systemServiceEnabled = MutableStateFlow(isServiceEnabledInSystem())
    private val connectTimedOut = MutableStateFlow(false)

    /** Screen mode. Always current, so [StateFlow.value] doubles as the synchronous first-frame snapshot. */
    val uiState: StateFlow<UiState> = combine(
        overlayControls.serviceRunning,
        systemServiceEnabled,
        overlayControls.overlayActive,
        overlayControls.overlayError,
        connectTimedOut,
        ::deriveUiState
    ).stateIn(scope, SharingStarted.Eagerly, currentState())

    /** Slider values, separate from [uiState] so a slider tick doesn't re-render the cards. */
    val settings: StateFlow<OverlaySettings> = repository.settings

    private val _events = Channel<UiEvent>(Channel.BUFFERED)

    /** One-shot effects; each is delivered once. */
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    init {
        scope.launch { systemChanges.collect { refresh() } }
        scope.launch {
            // Enabled but not connected: after connectTimeout, report it as not responding.
            combine(overlayControls.serviceRunning, systemServiceEnabled) { running, enabled -> enabled && !running }
                .distinctUntilChanged()
                .collectLatest { waiting ->
                    connectTimedOut.value = false
                    if (waiting) {
                        delay(connectTimeout)
                        connectTimedOut.value = true
                    }
                }
        }
    }

    /** Asks the system again. */
    fun refresh() {
        systemServiceEnabled.value = isServiceEnabledInSystem()
    }

    /** Rechecks only while waiting, in case a settings change was missed. */
    fun onResume() {
        if (uiState.value is UiState.Connecting || uiState.value is UiState.NotResponding) refresh()
    }

    /** Applies a slider's [progress] to its setting. */
    fun setSlider(type: SliderType, progress: Int) = repository.update { type.withProgress(it, progress) }

    /** The toggle was flipped: switch the overlay, or send the user to enable the service first. */
    fun onToggle(checked: Boolean) {
        if (overlayControls.serviceRunning.value) {
            overlayControls.setOverlayActive(checked)
        } else {
            _events.trySend(UiEvent.OpenAccessibilitySettings)
        }
    }

    fun requestDisableService() = overlayControls.requestDisableService()

    fun flush() = repository.flush()

    override fun onCleared() {
        scope.cancel()
    }

    private fun currentState() = deriveUiState(
        overlayControls.serviceRunning.value,
        systemServiceEnabled.value,
        overlayControls.overlayActive.value,
        overlayControls.overlayError.value,
        connectTimedOut.value
    )

    companion object {
        val DEFAULT_CONNECT_TIMEOUT = 8.seconds

        fun factory(
            container: AppContainer,
            isServiceEnabledInSystem: () -> Boolean,
            systemChanges: Flow<Unit>
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(
                    repository = container.repository,
                    overlayControls = container.overlayController,
                    isServiceEnabledInSystem = isServiceEnabledInSystem,
                    systemChanges = systemChanges,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                )
            }
        }
    }
}
