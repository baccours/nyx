package com.baccours.nyx.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

@SuppressLint("AccessibilityPolicy")
class NyxService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var settingsManager: SettingsManager

    // Kelvin->RGB only depends on colorTemperature, so dragging the dim or blue-light
    // sliders (which don't change kelvin) can reuse this instead of redoing the ln/pow math.
    private var cachedKelvinKey = Int.MIN_VALUE
    private var cachedKelvinRgb = intArrayOf(255, 255, 255)

    companion object {
        val dimIntensity = MutableStateFlow(0.3f)
        val blueLightIntensity = MutableStateFlow(0.0f)
        val colorTemperature = MutableStateFlow(3400f)
        val isServiceRunning = MutableStateFlow(false)

        /** How long to wait after the last change before writing to disk. */
        private const val PERSIST_DEBOUNCE_MS = 300L

        private val stopCommand = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
        fun stopService() {
            stopCommand.tryEmit(Unit)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        serviceScope.launch {
            // Restore all three settings from a single DataStore read.
            val restored = settingsManager.restore()
            dimIntensity.value = restored.dimIntensity
            blueLightIntensity.value = restored.blueLightIntensity
            colorTemperature.value = restored.colorTemperature

            isServiceRunning.value = true

            val settings = combine(dimIntensity, blueLightIntensity, colorTemperature) { d, b, k ->
                OverlaySettings(d, b, k)
            }

            // Visual feedback: cheap (cached Kelvin math + a single setBackgroundColor),
            // so it can react to every real change immediately, even mid-drag.
            launch {
                settings.collect { updateOverlay(it) }
            }

            // Persistence: a real disk write, so it's debounced and collapsed into a single
            // batched write instead of firing on every slider tick.
            launch(Dispatchers.Default) {
                settings.debounce(PERSIST_DEBOUNCE_MS).collect { settingsManager.persist(it) }
            }
        }

        serviceScope.launch {
            stopCommand.collect {
                disableSelf()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        addOverlayView()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun addOverlayView() {
        val overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
        }

        overlayView = View(this)
        updateOverlay(OverlaySettings(dimIntensity.value, blueLightIntensity.value, colorTemperature.value))
        windowManager?.addView(overlayView, params)
    }

    private fun updateOverlay(settings: OverlaySettings) {
        val dim = settings.dimIntensity
        val tintIntensity = settings.blueLightIntensity
        val rgb = getKelvinRGB(settings.colorTemperature.toInt())

        // Calculate base color by scaling Kelvin RGB with tint intensity.
        // If tintIntensity is 0, this results in Black (0,0,0), which allows pure dimming.
        val r = rgb[0] * tintIntensity
        val g = rgb[1] * tintIntensity
        val b = rgb[2] * tintIntensity

        // Incorporate dimming by darkening the color channels
        val dimFactor = 1.0f - dim
        val finalRed = (r * dimFactor).toInt()
        val finalGreen = (g * dimFactor).toInt()
        val finalBlue = (b * dimFactor).toInt()

        // Alpha calculation: Combine base dimming with tint intensity
        val alphaDim = (dim * 255).toInt()
        val alphaTint = (tintIntensity * 180).toInt() // Max 180/255 for tint opacity
        val finalAlpha = max(alphaTint, alphaDim)

        overlayView?.setBackgroundColor(Color.argb(finalAlpha, finalRed, finalGreen, finalBlue))
    }

    /**
     * Approximates RGB values for a given Kelvin temperature.
     * Based on Tanner Helland's implementation of Mitchell Charity's formula.
     * Result is cached by integer Kelvin, since this is the only expensive part of an
     * overlay update (ln/pow) and is unchanged while only dim or blue-light intensity move.
     */
    private fun getKelvinRGB(kelvin: Int): IntArray {
        if (kelvin == cachedKelvinKey) return cachedKelvinRgb

        val temp = (kelvin / 100.0).coerceIn(10.0, 400.0)
        var r: Double
        var g: Double
        var b: Double

        // Calculate Red
        if (temp <= 66) {
            r = 255.0
        } else {
            r = temp - 60
            r = 329.698727446 * (r.pow(-0.1332047592))
        }

        // Calculate Green
        if (temp <= 66) {
            g = 99.4708025861 * ln(temp) - 161.1195681661
        } else {
            g = temp - 60
            g = 288.1221695283 * (g.pow(-0.0755148492))
        }

        // Calculate Blue
        if (temp >= 66) {
            b = 255.0
        } else if (temp <= 19) {
            b = 0.0
        } else {
            b = temp - 10
            b = 138.5177312231 * ln(b) - 305.0447927307
        }

        cachedKelvinKey = kelvin
        cachedKelvinRgb = intArrayOf(
            r.coerceIn(0.0, 255.0).toInt(),
            g.coerceIn(0.0, 255.0).toInt(),
            b.coerceIn(0.0, 255.0).toInt()
        )
        return cachedKelvinRgb
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
        }
        isServiceRunning.value = false

        // Flush the latest values immediately so a pending debounced write isn't lost,
        // then cancel the scope so no coroutines keep running against a dead service.
        val current = OverlaySettings(dimIntensity.value, blueLightIntensity.value, colorTemperature.value)
        serviceScope.launch(Dispatchers.Default) {
            settingsManager.persist(current)
        }.invokeOnCompletion {
            serviceScope.cancel()
        }
    }
}
