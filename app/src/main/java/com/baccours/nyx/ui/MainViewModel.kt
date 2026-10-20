package com.baccours.nyx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baccours.nyx.AppContainer
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.data.ServiceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

class MainViewModel(
    private val repository: OverlayRepository,
    private val status: ServiceStatus,
    /** Whether the accessibility service is enabled in system settings. Must not hold an Activity. */
    private val isEnabledInSystem: () -> Boolean
) : ViewModel() {

    private val refreshTicks = MutableStateFlow(0)

    /**
     * Last answer of [isEnabledInSystem], which is a binder call: asked once, then reused until
     * [refresh] or until the service has been seen running. Main thread only.
     */
    private var enabledInSystemCache: Boolean? = null

    val uiState: Flow<UiState> = combine(
        repository.settings, status.serviceRunning, status.filterEnabled, refreshTicks
    ) { settings, running, filterEnabled, _ ->
        derive(settings, running, filterEnabled)
    }.distinctUntilChanged()

    /** Whether the service is connected right now. */
    val isServiceRunning: Boolean get() = status.serviceRunning.value

    /** Synchronous snapshot, so the first frame is already correct. */
    fun currentState(): UiState =
        derive(repository.settings.value, status.serviceRunning.value, status.filterEnabled.value)

    /** Forgets the cached system answer and re-evaluates, e.g. after returning from system settings. */
    fun refresh() {
        enabledInSystemCache = null
        refreshTicks.update { it + 1 }
    }

    fun updateSettings(transform: (OverlaySettings) -> OverlaySettings) = repository.update(transform)

    fun setFilterEnabled(enabled: Boolean) = status.setFilterEnabled(enabled)

    fun requestDisableService() = status.requestDisableService()

    fun flush() = repository.flush()

    private fun derive(settings: OverlaySettings, running: Boolean, filterEnabled: Boolean): UiState {
        // A running service is enabled by definition. Forget the cached answer so that, once the
        // service stops, we ask the system afresh instead of trusting an older "yes".
        if (running) enabledInSystemCache = null
        return deriveUiState(running, filterEnabled, settings) {
            enabledInSystemCache ?: isEnabledInSystem().also { enabledInSystemCache = it }
        }
    }

    companion object {
        fun factory(container: AppContainer, isEnabledInSystem: () -> Boolean) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(container.repository, container.status, isEnabledInSystem) as T
            }
    }
}
