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
 * Persists and applies the user's theme choice.
 *
 * SharedPreferences (not DataStore) is deliberate: the value is needed synchronously, before
 * the first frame, and an async read would flash the wrong theme on startup. The value is
 * cached so the disk is only touched once per process and on changes.
 */
object ThemeManager {
    /** Used on a fresh installation. Change to [ThemeMode.DARK] to default to dark instead. */
    private val DEFAULT_MODE = ThemeMode.SYSTEM

    private const val PREFS_NAME = "nyx_theme_prefs"
    private const val KEY_MODE = "theme_mode"

    @Volatile
    private var cached: ThemeMode? = null

    /** Call once from `Application.onCreate`. */
    fun init(context: Context) = applyMode(current(context))

    fun current(context: Context): ThemeMode =
        cached ?: load(context).also { cached = it }

    fun set(context: Context, mode: ThemeMode) {
        cached = mode
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
