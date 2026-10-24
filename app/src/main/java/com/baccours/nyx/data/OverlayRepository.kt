package com.baccours.nyx.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Source of truth for the overlay settings. Loads them synchronously on construction, so the
 * first reader already sees them, and persists changes after [persistDebounce] of quiet.
 * If the store throws, the error goes to [onPersistError] and the write is retried on the next
 * write or [flush]. SharedPreferences' apply() never throws on disk failure, so with
 * [SharedPreferencesStore] this only guards stores that can fail loudly.
 */
@OptIn(FlowPreview::class)
class OverlayRepository(
    private val store: OverlaySettingsStore,
    scope: CoroutineScope,
    persistDebounce: Duration = 300.milliseconds,
    private val onPersistError: (Exception) -> Unit = { Log.w(TAG, "Persist failed", it) }
) {
    private val _settings = MutableStateFlow(store.loadOverlaySettings().sanitized())

    /** Serializes store writes and guards [lastPersisted]. */
    private val lock = Any()
    private var lastPersisted = _settings.value

    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()

    init {
        scope.launch { _settings.debounce(persistDebounce).collect { persistLatest() } }
    }

    /** [transform] must be pure: it is retried if another update races in. */
    fun update(transform: (OverlaySettings) -> OverlaySettings) {
        _settings.update { transform(it).sanitized() }
    }

    /** Immediately persists the current settings, bypassing the debounce. */
    fun flush() = persistLatest()

    /**
     * Always writes the newest value, never one-handed over by the debounce: that copy can be stale
     * if a [flush] already persisted something newer while it was in flight.
     */
    private fun persistLatest() {
        synchronized(lock) {
            val value = _settings.value
            if (value == lastPersisted) return
            try {
                store.saveOverlaySettings(value)
                lastPersisted = value
            } catch (e: Exception) {
                // Swallowed so it can't crash the app or end the collector; lastPersisted is
                // unchanged, so the value is retried.
                onPersistError(e)
            }
        }
    }

    private companion object {
        const val TAG = "OverlayRepository"
    }
}
