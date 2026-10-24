package com.baccours.nyx.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * The full-screen, touch-transparent overlay window: attach, recolor, detach. Main thread only.
 *
 * Needs the [service] itself: `TYPE_ACCESSIBILITY_OVERLAY` windows are tied to its connection.
 */
internal class OverlayWindow(private val service: AccessibilityService) {

    private class Attached(val view: View, val windowManager: WindowManager)

    private var attached: Attached? = null

    /** Shows the overlay in [argb], attaching it if needed. Fails with the attach exception. */
    fun show(argb: Int): Result<Unit> {
        attached?.let {
            it.view.setBackgroundColor(argb)
            return Result.success(Unit)
        }
        // Fetched here, not at construction: the service's WindowManager is bound to its window
        // token on first use, so that must happen after the service is connected.
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val newView = View(service).apply { setBackgroundColor(argb) }
        return try {
            windowManager.addView(newView, layoutParams())
            attached = Attached(newView, windowManager)
            Result.success(Unit)
        } catch (e: RuntimeException) { // e.g. BadTokenException
            Log.e(TAG, "Could not add overlay", e)
            Result.failure(e)
        }
    }

    /** Detaches the overlay; a no-op when it isn't attached. */
    fun hide() {
        val current = attached ?: return
        attached = null
        try {
            current.windowManager.removeView(current.view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay was already detached", e)
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
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

    private companion object {
        const val TAG = "OverlayWindow"
    }
}
