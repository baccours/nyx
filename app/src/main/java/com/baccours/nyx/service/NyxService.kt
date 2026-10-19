package com.baccours.nyx.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.edit
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.overlay.OverlayColorCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Hosts the full-screen overlay window.
 *
 * Renders state from [OverlayRepository] and updates service lifecycle state.
 */
@SuppressLint("AccessibilityPolicy")
class NyxService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session by lazy {
        ServiceSession(PrefsSessionStore(applicationContext.getSharedPreferences("nyx_service_session", MODE_PRIVATE)))
    }
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var renderJob: Job? = null
    private var overlayFailed = false

    companion object {
        private const val TAG = "NyxService"

        @Volatile
        private var instanceRef: WeakReference<NyxService>? = null

        /** Turns the accessibility service off (no-op if it isn't running). */
        fun stopService() {
            instanceRef?.get()?.disableSelf()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        OverlayRepository.setFilterEnabled(session.onConnected())

        renderJob?.cancel()
        renderJob = serviceScope.launch {
            launch { OverlayRepository.filterEnabled.collect { session.onFilterChanged(it) } }

            combine(OverlayRepository.settings, OverlayRepository.filterEnabled) { settings, enabled ->
                if (enabled) OverlayColorCalculator.overlayColor(settings) else null
            }
                .distinctUntilChanged()
                .collect { argb -> if (argb == null) removeOverlayView() else showOverlay(argb) }
        }
        if (!overlayFailed) OverlayRepository.setServiceRunning(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun showOverlay(argb: Int) {
        val view = overlayView
        if (view != null) {
            view.setBackgroundColor(argb)
        } else if (!addOverlayView(argb)) {
            overlayFailed = true
            disableSelf()
        }
    }

    private fun addOverlayView(argb: Int): Boolean {
        val wm = windowManager ?: return false

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val view = View(this).apply { setBackgroundColor(argb) }
        return try {
            wm.addView(view, params)
            overlayView = view
            true
        } catch (e: RuntimeException) { // e.g. BadTokenException
            Log.e(TAG, "Could not add overlay", e)
            false
        }
    }

    private fun removeOverlayView() {
        val view = overlayView ?: return
        overlayView = null
        try {
            windowManager?.removeView(view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay was already detached", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        removeOverlayView()
        if (instanceRef?.get() === this) instanceRef = null
        session.onDestroyed()
        OverlayRepository.setServiceRunning(false)
        OverlayRepository.flush()
    }
}

/** SharedPreferences-backed [SessionStore]. */
private class PrefsSessionStore(private val prefs: SharedPreferences) : SessionStore {
    override var sessionOpen: Boolean
        get() = prefs.getBoolean(KEY_SESSION_OPEN, false)
        set(value) {
            prefs.edit(commit = true) { putBoolean(KEY_SESSION_OPEN, value) }
        }

    override var filterEnabled: Boolean
        get() = prefs.getBoolean(KEY_FILTER_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_FILTER_ENABLED, value) }
        }

    private companion object {
        const val KEY_SESSION_OPEN = "session_open"
        const val KEY_FILTER_ENABLED = "filter_enabled"
    }
}
