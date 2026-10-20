package com.baccours.nyx.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.edit
import com.baccours.nyx.appContainer
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.overlay.OverlayColorCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Hosts the full-screen overlay window.
 *
 * Renders the settings from the repository and reports service lifecycle state to `ServiceStatus`.
 *
 * The last filter on/off choice is persisted so that a restart (process kill, reboot) keeps it.
 * An orderly shutdown ([onDestroy]) resets it to ON, so enabling the service always starts the filter.
 */
@SuppressLint("AccessibilityPolicy")
class NyxService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { appContainer }
    private val overlay by lazy { OverlayWindow(this) }
    private var renderJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val status = container.status
        status.setFilterEnabled(container.serviceState.getBoolean(KEY_FILTER_ENABLED, true))

        // Draw once, synchronously: a failure is known before the UI is told the service runs.
        if (!draw(overlayArgb(container.repository.settings.value, status.filterEnabled.value))) {
            giveUp()
            return
        }
        status.setServiceRunning(true)

        renderJob?.cancel()
        renderJob = serviceScope.launch {
            launch { status.filterEnabled.collect { saveFilterEnabled(it) } }
            launch { status.disableRequests.collect { disableSelf() } }

            combine(container.repository.settings, status.filterEnabled, ::overlayArgb)
                .distinctUntilChanged()
                .collect { argb -> if (!draw(argb)) giveUp() }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** ARGB to draw; 0 (fully transparent) means nothing to draw. */
    private fun overlayArgb(settings: OverlaySettings, enabled: Boolean): Int =
        if (enabled) OverlayColorCalculator.overlayColor(settings) else 0

    /** @return false if the overlay could not be attached. */
    private fun draw(argb: Int): Boolean {
        // Don't keep an empty full-screen layer attached.
        if (argb == 0) {
            overlay.hide()
            return true
        }
        return overlay.show(argb)
    }

    private fun giveUp() {
        Log.e(TAG, "Overlay unavailable, disabling the service")
        disableSelf()
    }

    private fun saveFilterEnabled(enabled: Boolean) =
        container.serviceState.edit { putBoolean(KEY_FILTER_ENABLED, enabled) }

    /** Stops rendering and removes the overlay. Idempotent. */
    private fun stopRendering() {
        renderJob?.cancel()
        renderJob = null
        overlay.hide()
        container.status.setServiceRunning(false)
    }

    // The overlay must not outlive the connection, even if onDestroy is delayed.
    override fun onUnbind(intent: Intent?): Boolean {
        stopRendering()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopRendering()
        saveFilterEnabled(true) // orderly shutdown: the next enable starts with the filter ON
        container.repository.flush()
    }

    private companion object {
        const val TAG = "NyxService"
        const val KEY_FILTER_ENABLED = "filter_enabled"
    }
}
