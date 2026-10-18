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
import android.widget.Toast
import com.baccours.nyx.R
import com.baccours.nyx.data.FilterEngine
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.overlay.OverlayColorCalculator
import com.baccours.nyx.overlay.SystemFilterPlanner
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hosts the filter: a full-screen overlay window and, optionally, the system's Night Light.
 *
 * Holds no settings state of its own: it renders whatever [OverlayRepository] publishes and
 * reports its lifecycle back through [OverlayRepository.setServiceRunning]. Whatever it borrowed
 * from the system (Night Light) is handed back in [onDestroy], so switching the filter off or
 * disabling the service never leaves a system color filter behind.
 */
@SuppressLint("AccessibilityPolicy")
class NyxService : AccessibilityService() {

    /** What should be on screen, already reduced to the values that matter (so we can dedupe). */
    private sealed interface RenderState {
        /** Overlay engine: one translucent layer does everything. */
        data class Overlay(val argb: Int) : RenderState

        /** System engine: Night Light for warmth; the overlay only dims ([dimArgb] == 0: no overlay). */
        data class NightLight(val nightLightKelvin: Int?, val dimArgb: Int) : RenderState
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val systemFilter by lazy { SystemColorFilter(applicationContext) }
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

        renderJob?.cancel()
        renderJob = serviceScope.launch {
            // Reduce to the final render state first: many slider positions (e.g. Black Level while
            // the filter is at 0%) give the same result, and every overlay repaint is a full-screen
            // redraw while every Night Light change is a round trip to the system.
            OverlayRepository.settings
                .map(::toRenderState)
                .distinctUntilChanged()
                .collect { render(it) }
        }
        // (An overlay that could not be added already switched the service off.)
        if (!overlayFailed) OverlayRepository.setServiceRunning(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun toRenderState(settings: OverlaySettings): RenderState {
        val useSystem = settings.engine == FilterEngine.SYSTEM &&
                SystemColorFilter.availability(applicationContext) == SystemColorFilter.Availability.AVAILABLE
        if (!useSystem) return RenderState.Overlay(OverlayColorCalculator.overlayColor(settings))

        val plan = SystemFilterPlanner.plan(settings, SystemColorFilter.capabilities())
        // Pure black layer: an exact multiply, so it dims without lifting black.
        val dimArgb = OverlayColorCalculator.overlayColor(
            OverlaySettings(dimIntensity = plan.dimIntensity, blueLightIntensity = 0f, blackLevel = 0f)
        )
        return RenderState.NightLight(plan.nightLightKelvin, dimArgb)
    }

    private suspend fun render(state: RenderState) {
        when (state) {
            is RenderState.Overlay -> {
                // Back from the system engine: hand Night Light back to the user.
                if (systemFilter.isHoldingSystemState) {
                    withContext(Dispatchers.IO) { systemFilter.applyNightLight(null) }
                }
                showOverlay(state.argb)
            }

            is RenderState.NightLight -> {
                // Settings writes are binder calls: keep them off the main thread.
                val applied = withContext(Dispatchers.IO) { systemFilter.applyNightLight(state.nightLightKelvin) }
                if (!applied) {
                    Log.w(TAG, "System filter unavailable, falling back to the overlay engine")
                    Toast.makeText(applicationContext, R.string.system_mode_failed, Toast.LENGTH_LONG).show()
                    // The settings change re-renders this as an Overlay state.
                    OverlayRepository.update { it.copy(engine = FilterEngine.OVERLAY) }
                    return
                }
                if (state.dimArgb == 0) removeOverlayView() else showOverlay(state.dimArgb)
            }
        }
    }

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
        // Give Night Light back exactly as the user had it (also cleans up after a crashed run).
        systemFilter.close()
        if (instanceRef?.get() === this) instanceRef = null
        OverlayRepository.setServiceRunning(false)
        // Persist any change still waiting in the debounce window.
        OverlayRepository.flush()
    }
}
