package com.baccours.nyx.service

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Reads and watches whether [NyxService] is switched on in system settings. Reads the setting
 * itself, which the observer reports, instead of asking the accessibility manager, which may
 * update its own state after the observer fires.
 */
class ServiceEnabledMonitor(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val component = ComponentName(context.applicationContext, NyxService::class.java)

    fun isEnabled(): Boolean {
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    /** Emits whenever the enabled-services setting changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer
        )
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()
}
