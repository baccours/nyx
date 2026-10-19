package com.baccours.nyx.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.overlay.OverlayColorCalculator
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Hosts the filter: a full-screen overlay window.
 *
 * Holds no settings state of its own: it renders whatever [OverlayRepository] publishes and
 * reports its lifecycle back through [OverlayRepository.setServiceRunning]. The filter can be
 * switched off while the service stays enabled: the overlay is then removed from the screen.
 */
@SuppressLint("AccessibilityPolicy")
class NyxService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session by lazy {
        ServiceSession(PrefsSessionStore(applicationContext.getSharedPreferences("nyx_service_session", Context.MODE_PRIVATE)))
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

        // A fresh start (Settings or shortcut) always turns the filter on; a restart keeps the
        // user's last choice. See ServiceSession.
        OverlayRepository.setFilterEnabled(session.onConnected())

        renderJob?.cancel()
        renderJob = serviceScope.launch {
            // Remember the user's on/off choice (read back by ServiceSession after a restart).
            launch { OverlayRepository.filterEnabled.collect { session.onFilterChanged(it) } }

            // Reduce to the final ARGB color first (null = filter off): many slider positions (e.g.
            // Black Level while the filter is at 0%) give the same color, and every repaint is a
            // full-screen redraw. With the filter off no layer is on screen at all, so there is
            // no per-frame compositing cost.
            combine(OverlayRepository.settings, OverlayRepository.filterEnabled) { settings, enabled ->
                if (enabled) OverlayColorCalculator.overlayColor(settings) else null
            }
                .distinctUntilChanged()
                .collect { argb -> if (argb == null) removeOverlayView() else showOverlay(argb) }
        }
        // (An overlay that could not be added already switched the service off.)
        if (!overlayFailed) OverlayRepository.setServiceRunning(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun showOverlay(argb: Int) {
        val view = overlayView
        if (view != null) {
            view.setBackgroundColor(argb)
        } else if (!addOverlayView(argb)) {
            // Without an overlay the service is useless; switch it off so the UI stays truthful.
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
                    // A window added from a Service is software-rendered unless it asks otherwise,
                    // so every slider tick would fill ~10 MB of pixels on the CPU. On the GPU the
                    // repaint of a single solid color is trivial.
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            // ALWAYS also covers cutouts on the long edges (landscape); SHORT_EDGES would not.
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
            overlayView = view // only remembered once it is really attached
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
        session.onDestroyed() // orderly shutdown: the next connect is a fresh start
        OverlayRepository.setServiceRunning(false)
        // Persist any change still waiting in the debounce window.
        OverlayRepository.flush()
    }
}

/** SharedPreferences-backed [SessionStore]. The session flag is committed so it survives a kill. */
private class PrefsSessionStore(private val prefs: SharedPreferences) : SessionStore {
    override var sessionOpen: Boolean
        get() = prefs.getBoolean(KEY_SESSION_OPEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SESSION_OPEN, value).commit()
        }

    override var filterEnabled: Boolean
        get() = prefs.getBoolean(KEY_FILTER_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_FILTER_ENABLED, value).apply()
        }

    private companion object {
        const val KEY_SESSION_OPEN = "session_open"
        const val KEY_FILTER_ENABLED = "filter_enabled"
    }
}
