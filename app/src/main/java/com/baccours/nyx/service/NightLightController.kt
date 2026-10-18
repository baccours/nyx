package com.baccours.nyx.service

/** The few Settings.Secure operations Night Light control needs (a seam so the logic is testable). */
internal interface SecureSettings {
    fun getString(key: String): String?

    /** @throws SecurityException if the permission is missing. */
    fun putInt(key: String, value: Int): Boolean

    /** @throws SecurityException if the permission is missing. */
    fun putString(key: String, value: String): Boolean
}

/** Durable record of the user's own Night Light state, kept while we borrow it. */
internal interface SnapshotStore {
    val active: Boolean
    val prevActivated: String?
    val prevTemperature: String?

    /** Durably records the user's state and marks the snapshot active. Must not return before it is stored. */
    fun save(prevActivated: String?, prevTemperature: String?)
    fun clear()
}

/**
 * The borrow-and-return logic for the system's Night Light, free of Android types.
 *
 * The user's own state is snapshotted once, before the first change, and kept across restarts
 * (the original is never overwritten by a later snapshot). It is put back by `apply(null)` or
 * [close], so the filter never leaves Night Light in a state the user did not choose.
 */
internal class NightLightController(
    private val settings: SecureSettings,
    private val store: SnapshotStore,
    private val warn: (String, Throwable?) -> Unit = { _, _ -> }
) {
    private val lock = Any()
    private var closed = false

    val isHolding: Boolean
        get() = store.active

    /**
     * Turns Night Light on at [kelvin], or gives it back to the user when [kelvin] is `null`.
     * @param allowed whether we may write right now (permission granted, feature present).
     * @return false if the change was not made (not allowed, rejected, denied, or already closed).
     */
    fun apply(kelvin: Int?, allowed: Boolean): Boolean = synchronized(lock) {
        if (closed) return@synchronized false
        if (kelvin == null) {
            restoreLocked()
            return@synchronized true
        }
        if (!allowed) return@synchronized false
        try {
            if (!store.active) store.save(settings.getString(KEY_ACTIVATED), settings.getString(KEY_TEMPERATURE))
            val ok = settings.putInt(KEY_TEMPERATURE, kelvin) && settings.putInt(KEY_ACTIVATED, 1)
            if (!ok) warn("Night Light write was rejected", null)
            ok
        } catch (e: SecurityException) {
            warn("Night Light write was denied", e)
            false
        }
    }

    /** Restores the user's own state and refuses any further changes. */
    fun close() = synchronized(lock) {
        closed = true
        restoreLocked()
    }

    private fun restoreLocked() {
        if (!store.active) return
        val prevActivated = store.prevActivated ?: "0"
        val prevTemperature = store.prevTemperature
        try {
            if (prevActivated == "1") {
                // The user had Night Light on: put their temperature back first, then keep it on.
                prevTemperature?.let { settings.putString(KEY_TEMPERATURE, it) }
                settings.putString(KEY_ACTIVATED, prevActivated)
            } else {
                // Switch it off first so the screen never flashes the old temperature.
                settings.putString(KEY_ACTIVATED, prevActivated)
                prevTemperature?.let { settings.putString(KEY_TEMPERATURE, it) }
            }
        } catch (e: SecurityException) {
            // Permission was revoked meanwhile; nothing more we can do, and keeping the snapshot
            // forever would not help. The user can switch Night Light off in Quick Settings.
            warn("Could not restore Night Light", e)
        }
        store.clear()
    }

    companion object {
        // Settings.Secure keys (AOSP ColorDisplayService / NightDisplay*).
        const val KEY_ACTIVATED = "night_display_activated"
        const val KEY_TEMPERATURE = "night_display_color_temperature"
    }
}
