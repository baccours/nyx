package com.baccours.nyx.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.IOException

private const val TAG = "SettingsManager"

// A corrupt file is replaced with empty preferences (-> defaults) instead of crashing.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** Thin DataStore wrapper. Never throws on I/O problems: reads fall back to defaults. */
class SettingsManager(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    private companion object {
        val DIM_INTENSITY = floatPreferencesKey("dim_intensity")
        val BLUE_LIGHT_INTENSITY = floatPreferencesKey("blue_light_intensity")
        val COLOR_TEMPERATURE = floatPreferencesKey("color_temperature")
        val BLACK_LEVEL = floatPreferencesKey("black_level")
        val ENGINE = stringPreferencesKey("engine")
    }

    /** One-shot read of all values; falls back to defaults on I/O errors. */
    suspend fun restore(): OverlaySettings = try {
        dataStore.data.first().toOverlaySettings()
    } catch (e: IOException) {
        Log.w(TAG, "Could not read settings, using defaults", e)
        OverlaySettings.DEFAULT
    }

    /**
     * Persists all values in a single DataStore transaction (one disk write).
     * Callers should debounce rapid changes (e.g. slider drags) before calling this.
     */
    suspend fun persist(settings: OverlaySettings) {
        try {
            dataStore.edit { preferences ->
                preferences[DIM_INTENSITY] = settings.dimIntensity
                preferences[BLUE_LIGHT_INTENSITY] = settings.blueLightIntensity
                preferences[COLOR_TEMPERATURE] = settings.colorTemperature
                preferences[BLACK_LEVEL] = settings.blackLevel
                preferences[ENGINE] = settings.engine.name
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not persist settings", e)
        }
    }

    private fun Preferences.toOverlaySettings(): OverlaySettings {
        val d = OverlaySettings.DEFAULT
        return OverlaySettings(
            dimIntensity = this[DIM_INTENSITY] ?: d.dimIntensity,
            blueLightIntensity = this[BLUE_LIGHT_INTENSITY] ?: d.blueLightIntensity,
            colorTemperature = this[COLOR_TEMPERATURE] ?: d.colorTemperature,
            blackLevel = this[BLACK_LEVEL] ?: d.blackLevel,
            engine = this[ENGINE]?.let { stored -> FilterEngine.entries.firstOrNull { it.name == stored } } ?: d.engine
        ).sanitized()
    }
}
