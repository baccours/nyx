package com.baccours.nyx.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.baccours.nyx.R
import com.baccours.nyx.data.ThemeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The theme choices, declared in tap order: system -> light -> dark -> system. */
enum class ThemeMode(
    val nightMode: Int,
    @get:DrawableRes val icon: Int,
    @get:StringRes val label: Int
) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, R.drawable.ic_theme_system, R.string.theme_system),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO, R.drawable.ic_sun, R.string.theme_light),
    DARK(AppCompatDelegate.MODE_NIGHT_YES, R.drawable.ic_moon, R.string.theme_dark);

    /** The mode a tap moves to. */
    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Saves and applies the app's theme choice.
 * Reads synchronously so the right theme is in place before the first frame (no flicker).
 */
class ThemeManager(private val store: ThemeStore) {

    private val _mode = MutableStateFlow(load())

    /** The chosen mode, not the resulting look: [ThemeMode.SYSTEM] can be light or dark. */
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** Applies the stored mode. Call before AppCompat attaches to the activity. */
    fun applyStored() = AppCompatDelegate.setDefaultNightMode(_mode.value.nightMode)

    fun set(mode: ThemeMode) {
        store.save(mode.name)
        // Before applying: the change can recreate the activity, which reads the new value.
        _mode.value = mode
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    private fun load(): ThemeMode =
        store.load()?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: DEFAULT_MODE

    private companion object {
        val DEFAULT_MODE = ThemeMode.SYSTEM
    }
}
