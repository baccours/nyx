package com.baccours.nyx.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.baccours.nyx.appContainer
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.overlay.OverlayColorCalculator
import com.baccours.nyx.overlay.ServiceReports
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
 * Renders the settings from the repository and reports its lifecycle to [OverlayController][com.baccours.nyx.overlay.OverlayController].
 * If the overlay can't be drawn, it records why and turns itself off.
 *
 * An orderly shutdown ([onDestroy]) resets the overlay choice to ON, so enabling the service
 * always starts the overlay; a process kill or reboot keeps the last choice.
 */
@SuppressLint("AccessibilityPolicy")
class NyxService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { appContainer }
    private val reports: ServiceReports by lazy { container.overlayController }
    private val overlay = OverlayWindow(this) // Touches no system service until show().
    private var renderJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val repository = container.repository
        reports.onServiceConnecting()

        // Draw once, synchronously: a failure is known before the UI is told the service runs.
        draw(overlayArgb(repository.settings.value, reports.overlayActive.value)).onFailure {
            handleOverlayFailure(it)
            return
        }
        reports.onServiceStarted()

        renderJob?.cancel()
        renderJob = serviceScope.launch {
            launch { reports.disableServiceRequests.collect { disableSelf() } }

            combine(repository.settings, reports.overlayActive, ::overlayArgb)
                .distinctUntilChanged()
                .collect { argb -> draw(argb).onFailure { handleOverlayFailure(it) } }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** ARGB to draw; 0 (fully transparent) means nothing to draw. */
    private fun overlayArgb(settings: OverlaySettings, enabled: Boolean): Int =
        if (enabled) OverlayColorCalculator.overlayColor(settings) else 0

    private fun draw(argb: Int): Result<Unit> {
        // Don't keep an empty full-screen layer attached.
        if (argb == 0) {
            overlay.hide()
            return Result.success(Unit)
        }
        // One retry: attaching can fail transiently (e.g. during a display change).
        return overlay.show(argb).recoverCatching { overlay.show(argb).getOrThrow() }
    }

    /** Records why for the UI, then turns the service off. */
    private fun handleOverlayFailure(error: Throwable) {
        Log.e(TAG, "Overlay unavailable, disabling the service", error)
        renderJob?.cancel()
        renderJob = null
        reports.onOverlayFailed(error.javaClass.simpleName + error.message?.let { ": $it" }.orEmpty())
        disableSelf()
    }

    /** Stops rendering and removes the overlay. Idempotent. */
    private fun stopRendering() {
        renderJob?.cancel()
        renderJob = null
        overlay.hide()
        reports.onServiceStopped()
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
        reports.onServiceDestroyed()
        container.repository.flush()
    }

    private companion object {
        const val TAG = "NyxService"
    }
}
