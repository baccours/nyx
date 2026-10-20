package com.baccours.nyx.service

import android.content.SharedPreferences
import androidx.core.content.edit

/** SharedPreferences-backed [SessionStore]. */
internal class PrefsSessionStore(private val prefs: SharedPreferences) : SessionStore {
    override var sessionOpen: Boolean
        get() = prefs.getBoolean(KEY_SESSION_OPEN, false)
        set(value) {
            prefs.edit(commit = true) { putBoolean(KEY_SESSION_OPEN, value) }
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
