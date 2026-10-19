package com.baccours.nyx.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Application-scoped source of truth for overlay settings and service status. */
object OverlayRepository {

    /** How long to wait after the last change before writing to disk. */
    private val PERSIST_DEBOUNCE = 300.milliseconds

    private const val PREFS_NAME = "nyx_settings"
    private const val KEY_DIM_INTENSITY = "dim_intensity"
    private const val KEY_BLUE_LIGHT_INTENSITY = "blue_light_intensity"
    private const val KEY_COLOR_TEMPERATURE = "color_temperature"
    private const val KEY_BLACK_LEVEL = "black_level"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val _settings = MutableStateFlow(OverlaySettings.DEFAULT)
    private val _serviceRunning = MutableStateFlow(false)
    private val _filterEnabled = MutableStateFlow(true)

    private var prefs: SharedPreferences? = null

    /** Last value written to [prefs]; guarded by [lock]. */
    private var lastPersisted = OverlaySettings.DEFAULT

    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    /** Whether the filter overlay is enabled independently of [serviceRunning]. */
    val filterEnabled: StateFlow<Boolean> = _filterEnabled.asStateFlow()

    /** Loads the stored settings synchronously, so the first reader already sees them. */
    @OptIn(FlowPreview::class)
    fun init(context: Context) {
        val p = synchronized(lock) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val loaded = p.toOverlaySettings()
            prefs = p
            lastPersisted = loaded
            _settings.value = loaded
            p
        }
        scope.launch { _settings.debounce(PERSIST_DEBOUNCE).collect { persistIfChanged(p, it) } }
    }

    fun update(transform: (OverlaySettings) -> OverlaySettings) {
        synchronized(lock) {
            _settings.value = transform(_settings.value).sanitized()
        }
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun setFilterEnabled(enabled: Boolean) {
        _filterEnabled.value = enabled
    }

    /** Immediately persists settings, bypassing debounce. Cheap: `apply()` doesn't block on disk. */
    fun flush() {
        val p = prefs ?: return
        persistIfChanged(p, _settings.value)
    }

    private fun persistIfChanged(p: SharedPreferences, value: OverlaySettings) {
        // The write stays inside the lock so a stale value can't be applied after a newer one.
        synchronized(lock) {
            if (value == lastPersisted) return
            lastPersisted = value
            p.edit {
                putFloat(KEY_DIM_INTENSITY, value.dimIntensity)
                putFloat(KEY_BLUE_LIGHT_INTENSITY, value.blueLightIntensity)
                putFloat(KEY_COLOR_TEMPERATURE, value.colorTemperature)
                putFloat(KEY_BLACK_LEVEL, value.blackLevel)
            }
        }
    }

    private fun SharedPreferences.toOverlaySettings(): OverlaySettings {
        val d = OverlaySettings.DEFAULT
        return OverlaySettings(
            dimIntensity = getFloat(KEY_DIM_INTENSITY, d.dimIntensity),
            blueLightIntensity = getFloat(KEY_BLUE_LIGHT_INTENSITY, d.blueLightIntensity),
            colorTemperature = getFloat(KEY_COLOR_TEMPERATURE, d.colorTemperature),
            blackLevel = getFloat(KEY_BLACK_LEVEL, d.blackLevel)
        ).sanitized()
    }
}
