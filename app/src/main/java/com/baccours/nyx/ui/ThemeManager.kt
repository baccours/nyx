package com.baccours.nyx.ui

import androidx.appcompat.app.AppCompatDelegate
import com.baccours.nyx.data.ThemeStore

enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES)
}

/**
 * Saves and applies the app's theme choice.
 * Reads synchronously so the right theme is in place before the first frame (no flicker).
 */
class ThemeManager(private val store: ThemeStore) {

    /** Applies the stored mode. Call before AppCompat attaches to the activity. */
    fun applyStored() = AppCompatDelegate.setDefaultNightMode(load().nightMode)

    fun set(mode: ThemeMode) {
        store.saveTheme(mode.name)
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    private fun load(): ThemeMode =
        store.loadTheme()?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: DEFAULT_MODE

    private companion object {
        val DEFAULT_MODE = ThemeMode.SYSTEM
    }
}