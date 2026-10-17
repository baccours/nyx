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

/**
 * Process-wide source of truth for the overlay settings and the "filter is running" flag.
 *
 * Lives in the application scope (started from [com.baccours.nyx.NyxApplication]) so that
 *  - the UI always shows the persisted values, even while the accessibility service is off,
 *  - persistence is not tied to the service lifecycle, and
 *  - the UI and the service no longer write into each other's static state.
 */
object OverlayRepository {

    /** How long to wait after the last change before writing to disk. */
    private val PERSIST_DEBOUNCE = 300.milliseconds

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val _settings = MutableStateFlow(OverlaySettings.DEFAULT)
    private val _serviceRunning = MutableStateFlow(false)

    private var store: SettingsManager? = null

    /** True once the user changed something, so a slow restore never overwrites that edit. */
    private var touched = false

    /** Last value known to be on disk. `null` until the initial restore has completed. */
    private var lastPersisted: OverlaySettings? = null

    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

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
            // Only start persisting after the restore, so defaults can never overwrite disk.
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

    /** Writes the current value immediately (skipping the debounce), e.g. when the UI stops. */
    fun flush() {
        val manager = store ?: return
        val current = _settings.value
        scope.launch { persistIfChanged(manager, current) }
    }

    private suspend fun persistIfChanged(manager: SettingsManager, value: OverlaySettings) {
        val shouldWrite = synchronized(lock) {
            val last = lastPersisted ?: return // restore not finished: never write defaults
            (value != last).also { if (it) lastPersisted = value }
        }
        if (shouldWrite) manager.persist(value)
    }
}
