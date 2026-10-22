package com.baccours.nyx.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runtime overlay/service state shared by the UI and the accessibility service. Not persisted,
 * except the overlay active/inactive choice (via [store]). The UI sends commands; the service reports.
 */
class OverlayController(private val store: OverlayStateStore) {

    private val _serviceRunning = MutableStateFlow(false)
    private val _overlayActive = MutableStateFlow(true)
    private val _overlayError = MutableStateFlow<String?>(null)
    private val _disableServiceRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Whether the accessibility service is connected. */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    /** Whether the overlay is active, independently of [serviceRunning]. */
    val overlayActive: StateFlow<Boolean> = _overlayActive.asStateFlow()

    /** Why the service last gave up on the overlay; cleared when it reconnects. */
    val overlayError: StateFlow<String?> = _overlayError.asStateFlow()

    /** Emits when the UI asks the running service to turn itself off. */
    val disableServiceRequests: SharedFlow<Unit> = _disableServiceRequests.asSharedFlow()

    // --- Commands (UI) ---------------------------------------------------------------------

    /** Switches the overlay on or off; the choice survives service restarts. */
    fun setOverlayActive(active: Boolean) {
        _overlayActive.value = active
        store.saveOverlayState(active)
    }

    /** Asks the service to turn itself off. A no-op when it isn't running (nobody is collecting). */
    fun requestDisableService() {
        _disableServiceRequests.tryEmit(Unit)
    }

    // --- Reports (service) -----------------------------------------------------------------

    /** The service is connecting: restores the last on/off choice and clears the last error. */
    fun onServiceConnecting() {
        _overlayError.value = null
        _overlayActive.value = store.isOverlayActive()
    }

    fun onServiceStarted() {
        _serviceRunning.value = true
    }

    fun onServiceStopped() {
        _serviceRunning.value = false
    }

    fun onOverlayFailed(reason: String) {
        _overlayError.value = reason
    }

    /** Orderly shutdown: the next enable starts with the overlay ON. */
    fun onServiceDestroyed() = setOverlayActive(true)
}
