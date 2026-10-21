package com.baccours.nyx.ui

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES)
}

/**
 * Saves and applies the app's theme choice.
 * Reads synchronously so the right theme is in place before the first frame (no flicker).
 */
class ThemeManager(private val prefs: SharedPreferences) {

    /** Applies the stored mode. Call before AppCompat attaches to the activity. */
    fun applyStored() = AppCompatDelegate.setDefaultNightMode(load().nightMode)

    fun set(mode: ThemeMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    private fun load(): ThemeMode {
        val stored = prefs.getString(KEY_MODE, null)
        return stored?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: DEFAULT_MODE
    }

    private companion object {
        val DEFAULT_MODE = ThemeMode.SYSTEM
        const val KEY_MODE = "theme_mode"
    }
}