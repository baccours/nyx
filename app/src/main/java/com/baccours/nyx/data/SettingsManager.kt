package com.baccours.nyx.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val DIM_INTENSITY = floatPreferencesKey("dim_intensity")
        val BLUE_LIGHT_INTENSITY = floatPreferencesKey("blue_light_intensity")
        val COLOR_TEMPERATURE = floatPreferencesKey("color_temperature")
    }

    val dimIntensity: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[DIM_INTENSITY] ?: 0.3f
        }

    val blueLightIntensity: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[BLUE_LIGHT_INTENSITY] ?: 0.0f
        }

    val colorTemperature: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[COLOR_TEMPERATURE] ?: 3400f
        }

    suspend fun setDimIntensity(intensity: Float) {
        context.dataStore.edit { preferences ->
            preferences[DIM_INTENSITY] = intensity
        }
    }

    suspend fun setBlueLightIntensity(intensity: Float) {
        context.dataStore.edit { preferences ->
            preferences[BLUE_LIGHT_INTENSITY] = intensity
        }
    }

    suspend fun setColorTemperature(kelvin: Float) {
        context.dataStore.edit { preferences ->
            preferences[COLOR_TEMPERATURE] = kelvin
        }
    }
}
