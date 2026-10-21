package com.baccours.nyx.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runtime filter/service state shared by the UI and the accessibility service. Not persisted,
 * except the filter on/off choice (via [store]). The UI sends commands; the service reports.
 */
class FilterController(private val store: FilterStateStore) {

    private val _serviceRunning = MutableStateFlow(false)
    private val _filterEnabled = MutableStateFlow(true)
    private val _overlayError = MutableStateFlow<String?>(null)
    private val _disableRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Whether the accessibility service is connected. */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    /** Whether the filter overlay is enabled, independently of [serviceRunning]. */
    val filterEnabled: StateFlow<Boolean> = _filterEnabled.asStateFlow()

    /** Why the service last gave up on the overlay; cleared when it reconnects. */
    val overlayError: StateFlow<String?> = _overlayError.asStateFlow()

    /** Emits when the UI asks the running service to turn itself off. */
    val disableRequests: SharedFlow<Unit> = _disableRequests.asSharedFlow()

    // --- Commands (UI) ---------------------------------------------------------------------

    /** Switches the filter on or off; the choice survives service restarts. */
    fun setFilterEnabled(enabled: Boolean) {
        _filterEnabled.value = enabled
        store.saveFilterEnabled(enabled)
    }

    /** Asks the service to turn itself off. A no-op when it isn't running (nobody is collecting). */
    fun requestDisableService() {
        _disableRequests.tryEmit(Unit)
    }

    // --- Reports (service) -----------------------------------------------------------------

    /** The service is connecting: restores the last on/off choice and clears the last error. */
    fun onServiceConnecting() {
        _overlayError.value = null
        _filterEnabled.value = store.isFilterEnabled()
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun onOverlayFailed(reason: String) {
        _overlayError.value = reason
    }

    /** Orderly shutdown: the next enable starts with the filter ON. */
    fun onServiceDestroyed() = setFilterEnabled(true)
}
