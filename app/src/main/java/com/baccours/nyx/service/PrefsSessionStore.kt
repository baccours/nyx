package com.baccours.nyx.service

import android.content.SharedPreferences
import androidx.core.content.edit

/** SharedPreferences-backed [SessionStore]. */
internal class PrefsSessionStore(private val prefs: SharedPreferences) : SessionStore {
    override var sessionOpen: Boolean
        get() = prefs.getBoolean(KEY_SESSION_OPEN, false)
        set(value) {
            // apply(), not commit(): this runs on the main thread in onServiceConnected, and a
            // synchronous fsync there can stall for tens of ms on slow storage. The in-memory value
            // is updated immediately and the write is flushed in the background (and awaited on
            // orderly service shutdown). Only a process kill within milliseconds could lose it;
            // the cost then is that the filter restarts ON instead of restoring "off".
            prefs.edit { putBoolean(KEY_SESSION_OPEN, value) }
        }

    override var filterEnabled: Boolean
        get() = prefs.getBoolean(KEY_FILTER_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_FILTER_ENABLED, value) }
        }

    private companion object {
        const val KEY_SESSION_OPEN = "session_open"
        const val KEY_FILTER_ENABLED = "filter_enabled"
    }
}
