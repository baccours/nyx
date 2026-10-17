package com.baccours.nyx.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
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
import kotlinx.coroutines.launch

/**
 * Hosts the full-screen overlay that dims / tints the display.
 *
 * Holds no settings state of its own: it renders whatever [OverlayRepository] publishes and
 * reports its lifecycle back through [OverlayRepository.setServiceRunning].
 */
@SuppressLint("AccessibilityPolicy")
class NyxService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var renderJob: Job? = null

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

        // The system can report a connection more than once: never stack a second overlay.
        if (overlayView == null && !addOverlayView()) {
            // Without an overlay the service is useless; switch it off so the UI stays truthful.
            disableSelf()
            return
        }

        renderJob?.cancel()
        renderJob = serviceScope.launch {
            OverlayRepository.settings.collect { settings ->
                overlayView?.setBackgroundColor(OverlayColorCalculator.overlayColor(settings))
            }
        }
        OverlayRepository.setServiceRunning(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun addOverlayView(): Boolean {
        val wm = windowManager ?: return false

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
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

        val view = View(this).apply {
            setBackgroundColor(OverlayColorCalculator.overlayColor(OverlayRepository.settings.value))
        }
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
        OverlayRepository.setServiceRunning(false)
        // Persist any change still waiting in the debounce window.
        OverlayRepository.flush()
    }
}
