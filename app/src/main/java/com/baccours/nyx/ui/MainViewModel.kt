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

    val uiState: Flow<UiState> = combine(
        repository.settings, status.serviceRunning, status.filterEnabled, refreshTicks
    ) { settings, running, filterEnabled, _ ->
        deriveUiState(running, filterEnabled, settings, isEnabledInSystem)
    }.distinctUntilChanged()

    /** Whether the service is connected right now. */
    val isServiceRunning: Boolean get() = status.serviceRunning.value

    /** Synchronous snapshot, so the first frame is already correct. */
    fun currentState(): UiState = deriveUiState(
        status.serviceRunning.value, status.filterEnabled.value, repository.settings.value, isEnabledInSystem
    )

    /** Re-evaluates the state, e.g. after returning from system settings. */
    fun refresh() = refreshTicks.update { it + 1 }

    fun updateSettings(transform: (OverlaySettings) -> OverlaySettings) = repository.update(transform)

    fun setFilterEnabled(enabled: Boolean) = status.setFilterEnabled(enabled)

    fun requestDisableService() = status.requestDisableService()

    fun flush() = repository.flush()

    companion object {
        fun factory(container: AppContainer, isEnabledInSystem: () -> Boolean) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(container.repository, container.status, isEnabledInSystem) as T
            }
    }
}
