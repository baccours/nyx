package com.baccours.nyx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baccours.nyx.AppContainer
import com.baccours.nyx.data.OverlayController
import com.baccours.nyx.data.OverlayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: OverlayRepository,
    private val overlayController: OverlayController,
    /** Whether the accessibility service is enabled in system settings. Must not hold an Activity. */
    private val isServiceEnabledInSystem: () -> Boolean,
    /** Owned by this ViewModel: canceled in [onCleared]. */
    private val scope: CoroutineScope
) : ViewModel() {

    /**
     * Whether the service is enabled in system settings. The system is a binder call, so it is
     * asked once, then again only on [refresh] or when the service stops. A running service is
     * enabled by definition and never triggers a call.
     */
    private val systemServiceEnabled = MutableStateFlow(queryEnabled())

    /** Always current, so [StateFlow.value] doubles as the synchronous snapshot for the first frame. */
    val uiState: StateFlow<UiState> = combine(
        overlayController.serviceRunning,
        systemServiceEnabled,
        repository.settings,
        overlayController.overlayActive,
        overlayController.overlayError
    ) { srvRunning, srvEnabled, ovlSettings, ovlActive, ovlError ->
        deriveUiState(
            srvRunning,
            srvEnabled,
            ovlSettings,
            ovlActive,
            ovlError
        )
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        deriveUiState(
            overlayController.serviceRunning.value,
            systemServiceEnabled.value,
            repository.settings.value,
            overlayController.overlayActive.value,
            overlayController.overlayError.value
        )
    )

    init {
        // Once the service stops (e.g. disabled via the shortcut), an older "enabled" is stale.
        scope.launch { overlayController.serviceRunning.drop(1).collect { refresh() } }
    }

    /** Whether the service is connected right now. */
    val isServiceRunning: Boolean get() = overlayController.serviceRunning.value

    /** Asks the system again, e.g. after returning from system settings. */
    fun refresh() {
        systemServiceEnabled.value = queryEnabled()
    }

    /** Applies a slider's [progress] to its setting. */
    fun setSlider(type: SliderType, progress: Int) = repository.update { type.withProgress(it, progress) }

    fun setOverlayActive(enabled: Boolean) = overlayController.setOverlayActive(enabled)

    fun requestDisableService() = overlayController.requestDisableService()

    fun flush() = repository.flush()

    override fun onCleared() {
        scope.cancel()
    }

    private fun queryEnabled() = overlayController.serviceRunning.value || isServiceEnabledInSystem()

    companion object {
        fun factory(container: AppContainer, isServiceEnabledInSystem: () -> Boolean): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MainViewModel(
                        container.repository,
                        container.overlayController,
                        isServiceEnabledInSystem,
                        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                    )
                }
            }
    }
}
