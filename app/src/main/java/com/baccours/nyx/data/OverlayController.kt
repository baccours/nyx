package com.baccours.nyx.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the UI sees and can ask for. */
interface OverlayControls {
    /** Whether the accessibility service is connected. */
    val serviceRunning: StateFlow<Boolean>

    /** Whether the overlay is active, independently of [serviceRunning]. */
    val overlayActive: StateFlow<Boolean>

    /** Why the service last gave up on the overlay; cleared when it reconnects. */
    val overlayError: StateFlow<String?>

    /** Switches the overlay on or off; the choice survives service restarts. */
    fun setOverlayActive(active: Boolean)

    /** Asks the service to turn itself off. A no-op when it isn't running (nobody is collecting). */
    fun requestDisableService()
}

/** What the service reads and reports. */
interface ServiceReports {
    val overlayActive: StateFlow<Boolean>

    /** Emits when the UI asks the running service to turn itself off. */
    val disableServiceRequests: SharedFlow<Unit>

    /** The service is connecting: restores the last on/off choice and clears the last error. */
    fun onServiceConnecting()
    fun onServiceStarted()
    fun onServiceStopped()
    fun onOverlayFailed(reason: String)

    /** Orderly shutdown: the next enable starts with the overlay ON. */
    fun onServiceDestroyed()
}

/**
 * Runtime overlay/service state shared by the UI and the accessibility service. Not persisted,
 * except the overlay active/inactive choice (via [store]). Each side gets only its own interface.
 */
class OverlayController(private val store: OverlayStateStore) : OverlayControls, ServiceReports {

    private val _serviceRunning = MutableStateFlow(false)
    private val _overlayActive = MutableStateFlow(true)
    private val _overlayError = MutableStateFlow<String?>(null)
    private val _disableServiceRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()
    override val overlayActive: StateFlow<Boolean> = _overlayActive.asStateFlow()
    override val overlayError: StateFlow<String?> = _overlayError.asStateFlow()
    override val disableServiceRequests: SharedFlow<Unit> = _disableServiceRequests.asSharedFlow()

    // --- Commands (UI) ---------------------------------------------------------------------

    override fun setOverlayActive(active: Boolean) {
        _overlayActive.value = active
        store.saveOverlayState(active)
    }

    override fun requestDisableService() {
        _disableServiceRequests.tryEmit(Unit)
    }

    // --- Reports (service) -----------------------------------------------------------------

    override fun onServiceConnecting() {
        _overlayError.value = null
        _overlayActive.value = store.isOverlayActive()
    }

    override fun onServiceStarted() {
        _serviceRunning.value = true
    }

    override fun onServiceStopped() {
        _serviceRunning.value = false
    }

    override fun onOverlayFailed(reason: String) {
        _overlayError.value = reason
    }

    override fun onServiceDestroyed() = setOverlayActive(true)
}
