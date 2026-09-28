package com.baccours.nyx.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The three overlay values, always read and written together as a single unit. */
data class OverlaySettings(
    val dimIntensity: Float = 0.3f,
    val blueLightIntensity: Float = 0.0f,
    val colorTemperature: Float = 3400f
)

class SettingsManager(private val context: Context) {

    private companion object {
        val DIM_INTENSITY = floatPreferencesKey("dim_intensity")
        val BLUE_LIGHT_INTENSITY = floatPreferencesKey("blue_light_intensity")
        val COLOR_TEMPERATURE = floatPreferencesKey("color_temperature")
    }

    /** Live stream of all three settings, read from a single underlying DataStore flow. */
    val settings: Flow<OverlaySettings> = context.dataStore.data.map { it.toOverlaySettings() }

    /** One-shot read, e.g. for restoring state when the service starts. */
    suspend fun restore(): OverlaySettings = context.dataStore.data.first().toOverlaySettings()

    /**
     * Persists all three values in a single DataStore transaction (one disk write) instead
     * of three separate `edit {}` calls, which each serialize and atomically rewrite the
     * whole preferences file. Callers should debounce rapid changes (e.g. slider drags)
     * before calling this.
     */
    suspend fun persist(settings: OverlaySettings) {
        context.dataStore.edit { preferences ->
            preferences[DIM_INTENSITY] = settings.dimIntensity
            preferences[BLUE_LIGHT_INTENSITY] = settings.blueLightIntensity
            preferences[COLOR_TEMPERATURE] = settings.colorTemperature
        }
    }

    private fun Preferences.toOverlaySettings() = OverlaySettings(
        dimIntensity = this[DIM_INTENSITY] ?: 0.3f,
        blueLightIntensity = this[BLUE_LIGHT_INTENSITY] ?: 0.0f,
        colorTemperature = this[COLOR_TEMPERATURE] ?: 3400f
    )
}
