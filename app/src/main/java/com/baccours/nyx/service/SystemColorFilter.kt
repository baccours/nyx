package com.baccours.nyx.service

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.baccours.nyx.overlay.SystemFilterPlanner

/**
 * Drives the system's Night Light (a display-level color transform) through Settings.Secure.
 *
 * Needs WRITE_SECURE_SETTINGS, which Android only lets a user grant over adb:
 * `adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS`.
 *
 * It borrows a setting the user may also use themselves, so the borrow-and-return rules live in
 * [NightLightController]: the user's own state is snapshotted (durably) before the first change
 * and restored when the filter is released.
 */
class SystemColorFilter(context: Context) {

    enum class Availability {
        /** Android too old, or this build of Android has no Night Light color transform. */
        UNSUPPORTED,

        /** Supported, but the user has not run the one-time adb command yet. */
        NEEDS_PERMISSION,
        AVAILABLE
    }

    private val appContext = context.applicationContext
    private val controller = NightLightController(
        settings = ResolverSettings(appContext.contentResolver),
        store = PrefsSnapshotStore(appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)),
        warn = { message, error -> Log.w(TAG, message, error) }
    )

    /** True while Night Light is borrowed, i.e. a snapshot of the user's state exists. */
    val isHoldingSystemState: Boolean
        get() = controller.isHolding

    /**
     * Turns Night Light on at [kelvin], or gives it back to the user when [kelvin] is `null`.
     * @return false if the system refused (permission revoked, unsupported, or already closed).
     */
    fun applyNightLight(kelvin: Int?): Boolean =
        controller.apply(kelvin, allowed = availability(appContext) == Availability.AVAILABLE)

    /** Restores the user's own Night Light state and refuses any further changes. */
    fun close() = controller.close()

    private class ResolverSettings(private val resolver: ContentResolver) : SecureSettings {
        override fun getString(key: String): String? = try {
            Settings.Secure.getString(resolver, key)
        } catch (e: SecurityException) {
            null
        }

        override fun putInt(key: String, value: Int) = Settings.Secure.putInt(resolver, key, value)
        override fun putString(key: String, value: String) = Settings.Secure.putString(resolver, key, value)
    }

    private class PrefsSnapshotStore(private val prefs: SharedPreferences) : SnapshotStore {
        override val active get() = prefs.getBoolean(KEY_ACTIVE, false)
        override val prevActivated get() = prefs.getString(KEY_PREV_ACTIVATED, null)
        override val prevTemperature get() = prefs.getString(KEY_PREV_TEMPERATURE, null)

        override fun save(prevActivated: String?, prevTemperature: String?) {
            // commit = true: the snapshot must be on disk before we change anything.
            prefs.edit(commit = true) {
                putBoolean(KEY_ACTIVE, true)
                putString(KEY_PREV_ACTIVATED, prevActivated)
                putString(KEY_PREV_TEMPERATURE, prevTemperature)
            }
        }

        override fun clear() {
            // `this` is the SharedPreferences.Editor here, not this store: spelled out on purpose.
            prefs.edit(commit = true) { this.clear() }
        }
    }

    companion object {
        private const val TAG = "SystemColorFilter"
        private const val PREFS_NAME = "nyx_system_filter"
        private const val KEY_ACTIVE = "active"
        private const val KEY_PREV_ACTIVATED = "prev_activated"
        private const val KEY_PREV_TEMPERATURE = "prev_temperature"

        const val PERMISSION = Manifest.permission.WRITE_SECURE_SETTINGS

        fun availability(context: Context): Availability = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !nightDisplayAvailable() -> Availability.UNSUPPORTED
            ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED ->
                Availability.NEEDS_PERMISSION
            else -> Availability.AVAILABLE
        }

        /** The temperature range this device's Night Light supports (AOSP defaults as fallback). */
        fun capabilities(): SystemFilterPlanner.Capabilities {
            val min = frameworkInt("config_nightDisplayColorTemperatureMin", SystemFilterPlanner.DEFAULT_MIN_KELVIN)
            val max = frameworkInt("config_nightDisplayColorTemperatureMax", SystemFilterPlanner.DEFAULT_MAX_KELVIN)
            return SystemFilterPlanner.Capabilities(minKelvin = min, maxKelvin = max.coerceAtLeast(min))
        }

        /** The one-time command the user has to run from a computer. */
        fun grantCommand(context: Context) =
            "adb shell pm grant ${context.packageName} $PERMISSION"

        // Hardware dependent: the transform runs in the display pipeline (HWC).
        private fun nightDisplayAvailable(): Boolean {
            val res = Resources.getSystem()
            val id = res.getIdentifier("config_nightDisplayAvailable", "bool", "android")
            return id != 0 && res.getBoolean(id)
        }

        private fun frameworkInt(name: String, fallback: Int): Int {
            val res = Resources.getSystem()
            val id = res.getIdentifier(name, "integer", "android")
            return if (id != 0) res.getInteger(id) else fallback
        }
    }
}
