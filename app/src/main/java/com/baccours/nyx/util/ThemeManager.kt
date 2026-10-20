package com.baccours.nyx.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES)
}

/**
 * Manages saving and applying the app's theme choice.
 * Uses SharedPreferences for synchronous loading at startup to avoid theme flickering.
 */
object ThemeManager {
    private val DEFAULT_MODE = ThemeMode.SYSTEM

    private const val PREFS_NAME = "nyx_theme_prefs"
    private const val KEY_MODE = "theme_mode"

    /** Call once from `Application.onCreate`. */
    fun init(context: Context) = applyMode(load(context))

    fun set(context: Context, mode: ThemeMode) {
        prefs(context).edit { putString(KEY_MODE, mode.name) }
        applyMode(mode)
    }

    private fun load(context: Context): ThemeMode {
        val stored = prefs(context).getString(KEY_MODE, null)
        return stored?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: DEFAULT_MODE
    }

    private fun applyMode(mode: ThemeMode) = AppCompatDelegate.setDefaultNightMode(mode.nightMode)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
