package com.baccours.nyx.data

import android.content.Context
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val _settings = MutableStateFlow(OverlaySettings.DEFAULT)
    private val _serviceRunning = MutableStateFlow(false)
    private val _filterEnabled = MutableStateFlow(true)

    private var store: SettingsManager? = null

    /** Prevents a slow restore from overwriting user edits. */
    private var touched = false

    /** Last persisted settings value, or null if initial restore is incomplete. */
    private var lastPersisted: OverlaySettings? = null

    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    /** Whether the filter overlay is enabled independently of [serviceRunning]. */
    val filterEnabled: StateFlow<Boolean> = _filterEnabled.asStateFlow()

    @OptIn(FlowPreview::class)
    fun init(context: Context) {
        val manager = synchronized(lock) {
            if (store != null) return
            SettingsManager(context).also { store = it }
        }
        scope.launch {
            val restored = manager.restore()
            synchronized(lock) {
                if (!touched) _settings.value = restored
                lastPersisted = restored
            }
            // Only persist after restore to avoid overwriting stored settings.
            _settings.debounce(PERSIST_DEBOUNCE).collect { persistIfChanged(manager, it) }
        }
    }

    fun update(transform: (OverlaySettings) -> OverlaySettings) {
        synchronized(lock) {
            touched = true
            _settings.value = transform(_settings.value).sanitized()
        }
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun setFilterEnabled(enabled: Boolean) {
        _filterEnabled.value = enabled
    }

    /** Immediately persists settings, bypassing debounce. */
    fun flush() {
        val manager = store ?: return
        val current = _settings.value
        scope.launch { persistIfChanged(manager, current) }
    }

    private suspend fun persistIfChanged(manager: SettingsManager, value: OverlaySettings) {
        val shouldWrite = synchronized(lock) {
            val last = lastPersisted ?: return // Restore not finished
            (value != last).also { if (it) lastPersisted = value }
        }
        if (shouldWrite) manager.persist(value)
    }
}
