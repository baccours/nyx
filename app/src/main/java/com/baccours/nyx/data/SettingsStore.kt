package com.baccours.nyx.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Durable storage for [OverlaySettings]. */
interface SettingsStore {
    /** Never throws: unreadable or missing values fall back to the defaults. */
    fun load(): OverlaySettings

    fun save(settings: OverlaySettings)
}

/** Durable, device-local "filter was last on" flag. */
interface FilterStateStore {
    fun isFilterEnabled(): Boolean

    fun saveFilterEnabled(enabled: Boolean)
}

/** SharedPreferences-backed [SettingsStore] and [FilterStateStore]. */
class PrefsSettingsStore(private val prefs: SharedPreferences) : SettingsStore, FilterStateStore {

    override fun load(): OverlaySettings {
        val d = OverlaySettings.DEFAULT
        return OverlaySettings(
            dimIntensity = prefs.readFloat(KEY_DIM_INTENSITY, d.dimIntensity),
            blueLightIntensity = prefs.readFloat(KEY_BLUE_LIGHT_INTENSITY, d.blueLightIntensity),
            colorTemperature = prefs.readFloat(KEY_COLOR_TEMPERATURE, d.colorTemperature),
            blackLevel = prefs.readFloat(KEY_BLACK_LEVEL, d.blackLevel)
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

    override fun isFilterEnabled(): Boolean = prefs.readBoolean(KEY_FILTER_ENABLED, true)

    override fun saveFilterEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FILTER_ENABLED, enabled) }
    }

    // A key holding another type makes the getters throw; fall back to the default rather than
    // crash the app (and the service) on every start.
    private fun SharedPreferences.readFloat(key: String, fallback: Float): Float =
        try {
            getFloat(key, fallback)
        } catch (_: ClassCastException) {
            fallback
        }

    private fun SharedPreferences.readBoolean(key: String, fallback: Boolean): Boolean =
        try {
            getBoolean(key, fallback)
        } catch (_: ClassCastException) {
            fallback
        }

    companion object {
        private const val KEY_DIM_INTENSITY = "dim_intensity"
        private const val KEY_BLUE_LIGHT_INTENSITY = "blue_light_intensity"
        private const val KEY_COLOR_TEMPERATURE = "color_temperature"
        private const val KEY_BLACK_LEVEL = "black_level"
        private const val KEY_FILTER_ENABLED = "filter_enabled"

        fun create(context: Context) = PrefsSettingsStore(
            context.applicationContext.getSharedPreferences(PrefNames.SETTINGS, Context.MODE_PRIVATE)
        )
    }
}
