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

/** SharedPreferences-backed [SettingsStore]. */
class PrefsSettingsStore(private val prefs: SharedPreferences) : SettingsStore {

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

    /**
     * A key holding another type (e.g. after restoring a backup made by a different schema) makes
     * `getFloat` throw. This runs in `Application.onCreate`, where a crash would also take the
     * accessibility service down on every start, so fall back to the default instead.
     */
    private fun SharedPreferences.readFloat(key: String, fallback: Float): Float =
        try {
            getFloat(key, fallback)
        } catch (_: ClassCastException) {
            fallback
        }

    companion object {
        private const val KEY_DIM_INTENSITY = "dim_intensity"
        private const val KEY_BLUE_LIGHT_INTENSITY = "blue_light_intensity"
        private const val KEY_COLOR_TEMPERATURE = "color_temperature"
        private const val KEY_BLACK_LEVEL = "black_level"

        fun create(context: Context) = PrefsSettingsStore(
            context.applicationContext.getSharedPreferences(PrefNames.SETTINGS, Context.MODE_PRIVATE)
        )
    }
}
