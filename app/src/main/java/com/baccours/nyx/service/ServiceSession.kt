package com.baccours.nyx.service

/** Durable store backing [ServiceSession]. */
internal interface SessionStore {
    /** True while a session is active; stays true across process kills or reboots. */
    var sessionOpen: Boolean

    /** User's last filter toggle choice. */
    var filterEnabled: Boolean
}

/**
 * Determines whether the filter starts ON or OFF when the service connects.
 *
 * - **Fresh start** (after orderly shutdown): Defaults filter to ON.
 * - **Restart** (after process kill or reboot): Restores the user's last filter state.
 */
internal class ServiceSession(private val store: SessionStore) {

    /** Handles service connection. @return whether the filter should start ON. */
    fun onConnected(): Boolean {
        val isRestart = store.sessionOpen
        store.sessionOpen = true
        if (!isRestart) store.filterEnabled = true
        return store.filterEnabled
    }

    /** Updates the user's filter preference. */
    fun onFilterChanged(enabled: Boolean) {
        store.filterEnabled = enabled
    }

    /** Marks the session closed on orderly service shutdown. */
    fun onDestroyed() {
        store.sessionOpen = false
    }
}
