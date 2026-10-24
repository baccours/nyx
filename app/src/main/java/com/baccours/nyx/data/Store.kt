package com.baccours.nyx.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Durable storage for [OverlaySettings]. */
interface OverlaySettingsStore {
    /** Never throws: unreadable or missing values fall back to the defaults. */
    fun load(): OverlaySettings

    fun save(settings: OverlaySettings)
}

/** Durable, device-local "overlay was last on" flag. */
interface OverlayStateStore {
    fun isActive(): Boolean

    fun save(active: Boolean)
}

/** Durable storage for the UI theme choice, kept as a name so the data layer stays UI-free. */
interface ThemeStore {
    /** The saved theme name, or null if none was saved. Never throws. */
    fun load(): String?

    fun save(name: String)
}

/** Opens the app's SharedPreferences file, which the stores below share. */
object AppPreferences {
    private const val FILE = "nyx_settings"

    fun open(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

// Reads never throw: a missing or wrongly typed value falls back to its default rather than
// crashing the app (and the service) on every start. A key holding another type makes the getters throw.
private inline fun <T> SharedPreferences.read(fallback: T, get: SharedPreferences.(T) -> T): T =
    try {
        get(fallback)
    } catch (_: ClassCastException) {
        fallback
    }

class OverlaySettingsPrefs(private val prefs: SharedPreferences) : OverlaySettingsStore {

    override fun load(): OverlaySettings {
        val d = OverlaySettings.DEFAULT
        return OverlaySettings(
            dimIntensity = prefs.read(d.dimIntensity) { getFloat(KEY_DIM_INTENSITY, it) },
            blueLightIntensity = prefs.read(d.blueLightIntensity) { getFloat(KEY_BLUE_LIGHT_INTENSITY, it) },
            colorTemperature = prefs.read(d.colorTemperature) { getFloat(KEY_COLOR_TEMPERATURE, it) },
            blackLevel = prefs.read(d.blackLevel) { getFloat(KEY_BLACK_LEVEL, it) }
        ).sanitized()
    }

    override fun save(settings: OverlaySettings) {
        prefs.edit {
            putFloat(KEY_DIM_INTENSITY, settings.dimIntensity)
            putFloat(KEY_BLUE_LIGHT_INTENSITY, settings.blueLightIntensity)
            putFloat(KEY_COLOR_TEMPERATURE, settings.colorTemperature)
            putFloat(KEY_BLACK_LEVEL, settings.blackLevel)
        }
    }

    private companion object {
        const val KEY_DIM_INTENSITY = "dim_intensity"
        const val KEY_BLUE_LIGHT_INTENSITY = "blue_light_intensity"
        const val KEY_COLOR_TEMPERATURE = "color_temperature"
        const val KEY_BLACK_LEVEL = "black_level"
    }
}

class OverlayStatePrefs(private val prefs: SharedPreferences) : OverlayStateStore {

    override fun isActive(): Boolean = prefs.read(true) { getBoolean(KEY_OVERLAY_STATE, it) }

    override fun save(active: Boolean) {
        prefs.edit { putBoolean(KEY_OVERLAY_STATE, active) }
    }

    private companion object {
        const val KEY_OVERLAY_STATE = "overlay_state"
    }
}

class ThemePrefs(private val prefs: SharedPreferences) : ThemeStore {

    override fun load(): String? = prefs.read(null) { getString(KEY_THEME, it) }

    override fun save(name: String) {
        prefs.edit { putString(KEY_THEME, name) }
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
