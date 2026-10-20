package com.baccours.nyx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baccours.nyx.AppContainer
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.data.ServiceStatus
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
    private val status: ServiceStatus,
    /** Whether the accessibility service is enabled in system settings. Must not hold an Activity. */
    private val isEnabledInSystem: () -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel() {

    /**
     * Whether the service is enabled in system settings. The system is a binder call, so it is
     * asked once, then again only on [refresh] or when the service stops. A running service is
     * enabled by definition and never triggers a call.
     */
    private val systemEnabled = MutableStateFlow(queryEnabled())

    /** Always current, so [StateFlow.value] doubles as the synchronous snapshot for the first frame. */
    val uiState: StateFlow<UiState> = combine(
        repository.settings, status.serviceRunning, status.filterEnabled, systemEnabled
    ) { settings, running, filterEnabled, enabled ->
        deriveUiState(running, filterEnabled, settings, enabled)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        deriveUiState(status.serviceRunning.value, status.filterEnabled.value, repository.settings.value, systemEnabled.value)
    )

    init {
        // Once the service stops (e.g. disabled via the shortcut), an older "enabled" is stale.
        scope.launch { status.serviceRunning.drop(1).collect { refresh() } }
    }

    /** Whether the service is connected right now. */
    val isServiceRunning: Boolean get() = status.serviceRunning.value

    /** Asks the system again, e.g. after returning from system settings. */
    fun refresh() {
        systemEnabled.value = queryEnabled()
    }

    fun updateSettings(transform: (OverlaySettings) -> OverlaySettings) = repository.update(transform)

    fun setFilterEnabled(enabled: Boolean) = status.setFilterEnabled(enabled)

    fun requestDisableService() = status.requestDisableService()

    fun flush() = repository.flush()

    override fun onCleared() {
        scope.cancel()
    }

    private fun queryEnabled() = status.serviceRunning.value || isEnabledInSystem()

    companion object {
        fun factory(container: AppContainer, isEnabledInSystem: () -> Boolean) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(container.repository, container.status, isEnabledInSystem) as T
            }
    }
}
