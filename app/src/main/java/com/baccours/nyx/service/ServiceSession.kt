package com.baccours.nyx.service

/** Tiny durable store for [ServiceSession] (a seam so the policy is testable). */
internal interface SessionStore {
    /** True from a service connect until its orderly shutdown; stays true if the process just dies. */
    var sessionOpen: Boolean

    /** The user's last overlay on/off choice. */
    var filterEnabled: Boolean
}

/**
 * Decides whether the filter starts on or off when the accessibility service connects.
 *
 * The service can stay enabled with the filter switched off, so a connect has two meanings:
 *  - **Fresh start** (the user enabled the service via Settings or the accessibility shortcut):
 *    they want the filter, so it always starts ON, whatever it was before.
 *  - **Restart** (the system re-bound the service after the process was killed, or after a reboot):
 *    nobody asked for anything, so the user's last on/off choice is kept. Otherwise a filter the
 *    user had switched off would suddenly light up again.
 *
 * The two are told apart by [SessionStore.sessionOpen]: an orderly shutdown ([onDestroyed])
 * clears it, a killed process or a reboot does not.
 */
internal class ServiceSession(private val store: SessionStore) {

    /** Call when the service connects. @return whether the filter should start on. */
    fun onConnected(): Boolean {
        val isRestart = store.sessionOpen
        store.sessionOpen = true
        if (!isRestart) store.filterEnabled = true
        return store.filterEnabled
    }

    /** Call whenever the user switches the filter on or off. */
    fun onFilterChanged(enabled: Boolean) {
        store.filterEnabled = enabled
    }

    /** Call from the service's orderly shutdown. */
    fun onDestroyed() {
        store.sessionOpen = false
    }
}
