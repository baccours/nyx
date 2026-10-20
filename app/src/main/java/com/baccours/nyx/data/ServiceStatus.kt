package com.baccours.nyx.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory runtime state shared between the accessibility service and the UI. Not persisted. */
class ServiceStatus {

    private val _serviceRunning = MutableStateFlow(false)
    private val _filterEnabled = MutableStateFlow(true)
    private val _disableRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Whether the accessibility service is connected. */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    /** Whether the filter overlay is enabled, independently of [serviceRunning]. */
    val filterEnabled: StateFlow<Boolean> = _filterEnabled.asStateFlow()

    /** Emits when the UI asks the running service to turn itself off. */
    val disableRequests: SharedFlow<Unit> = _disableRequests.asSharedFlow()

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun setFilterEnabled(enabled: Boolean) {
        _filterEnabled.value = enabled
    }

    /** Asks the service to turn itself off. A no-op when it isn't running (nobody is collecting). */
    fun requestDisableService() {
        _disableRequests.tryEmit(Unit)
    }
}
