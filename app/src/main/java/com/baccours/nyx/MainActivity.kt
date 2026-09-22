package com.baccours.nyx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.baccours.nyx.service.NyxService
import com.baccours.nyx.components.SwipeToggleView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusCard: View
    private lateinit var statusText: TextView
    private lateinit var swipeToggle: SwipeToggleView
    private lateinit var permissionCard: View
    private lateinit var dashboard: View
    private lateinit var runningIndicator: View

    private lateinit var dimSlider: ControlSliderViews
    private lateinit var blueSlider: ControlSliderViews
    private lateinit var tempSlider: ControlSliderViews

    private var isAccessibilityEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.title = getString(R.string.app_name)

        bindViews()
        setUpSliders()
        setUpToggle()
        observeServiceState()
        pollAccessibilityStatus()
    }

    private fun bindViews() {
        statusCard = findViewById(R.id.statusCard)
        statusText = findViewById(R.id.statusText)
        swipeToggle = findViewById(R.id.swipeToggle)
        permissionCard = findViewById(R.id.permissionCard)
        dashboard = findViewById(R.id.dashboard)
        runningIndicator = findViewById(R.id.runningIndicator)

        dimSlider = ControlSliderViews.inflateInto(
            findViewById(R.id.dimContainer),
            label = getString(R.string.label_dimming_intensity),
            iconRes = R.drawable.ic_brightness,
            accentColor = ContextCompat.getColor(this, R.color.dimming_accent)
        )
        blueSlider = ControlSliderViews.inflateInto(
            findViewById(R.id.blueContainer),
            label = getString(R.string.label_blue_light_filter),
            iconRes = R.drawable.ic_sun,
            accentColor = ContextCompat.getColor(this, R.color.blue_light_accent)
        )
        tempSlider = ControlSliderViews.inflateInto(
            findViewById(R.id.tempContainer),
            label = getString(R.string.label_color_temperature),
            iconRes = R.drawable.ic_thermostat,
            accentColor = ContextCompat.getColor(this, R.color.temperature_accent)
        )
    }

    private fun setUpToggle() {
        swipeToggle.onCheckedChangeListener = { newState ->
            if (!newState) {
                NyxService.isServiceRunning.value = false
                NyxService.stopService()
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    private fun setUpSliders() {
        // Dimming intensity: SeekBar is 0..100, service value is 0f..1f.
        dimSlider.slider.setOnSeekBarChangeListener(onProgressChanged { progress, fromUser ->
            val value = progress / 100f
            dimSlider.valueText.text = "$progress%"
            if (fromUser) NyxService.dimIntensity.value = value
        })
        // Blue light filter: same 0..100 -> 0f..1f mapping.
        blueSlider.slider.setOnSeekBarChangeListener(onProgressChanged { progress, fromUser ->
            val value = progress / 100f
            blueSlider.valueText.text = "$progress%"
            if (fromUser) NyxService.blueLightIntensity.value = value
        })
        // Color temperature: SeekBar 0..100 mapped to 1000K..7000K, like the original.
        tempSlider.slider.setOnSeekBarChangeListener(onProgressChanged { progress, fromUser ->
            val kelvin = 1000f + (progress / 100f) * 6000f
            tempSlider.valueText.text = "${kelvin.toInt()}K"
            if (fromUser) NyxService.colorTemperature.value = kelvin
        })
    }

    private fun onProgressChanged(onChange: (progress: Int, fromUser: Boolean) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) =
                onChange(progress, fromUser)
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        }

    /** Mirrors the Composable's collectAsState() calls: reflects service state into the UI. */
    private fun observeServiceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    NyxService.isServiceRunning.collect { running -> updateRunningUi(running) }
                }
                launch {
                    NyxService.dimIntensity.collect { value ->
                        val progress = (value * 100).toInt().coerceIn(0, 100)
                        if (dimSlider.slider.progress != progress) dimSlider.slider.progress = progress
                        dimSlider.valueText.text = "$progress%"
                    }
                }
                launch {
                    NyxService.blueLightIntensity.collect { value ->
                        val progress = (value * 100).toInt().coerceIn(0, 100)
                        if (blueSlider.slider.progress != progress) blueSlider.slider.progress = progress
                        blueSlider.valueText.text = "$progress%"
                    }
                }
                launch {
                    NyxService.colorTemperature.collect { kelvin ->
                        val progress = (((kelvin - 1000f) / 6000f) * 100).toInt().coerceIn(0, 100)
                        if (tempSlider.slider.progress != progress) tempSlider.slider.progress = progress
                        tempSlider.valueText.text = "${kelvin.toInt()}K"
                    }
                }
            }
        }
    }

    /** Mirrors the LaunchedEffect that polled `isAccessibilityServiceEnabled` every second. */
    private fun pollAccessibilityStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    setAccessibilityEnabled(isAccessibilityServiceEnabled(this@MainActivity, NyxService::class.java))
                    delay(1000)
                }
            }
        }
    }

    private fun setAccessibilityEnabled(enabled: Boolean) {
        if (isAccessibilityEnabled == enabled) return
        isAccessibilityEnabled = enabled
        permissionCard.isVisible = !enabled
        dashboard.isVisible = enabled
        setSlidersEnabled(enabled && NyxService.isServiceRunning.value)
    }

    private fun updateRunningUi(isRunning: Boolean) {
        statusText.text = if (isRunning) getString(R.string.filter_on) else getString(R.string.filter_off)

        val statusColor: Int
        val containerColor: Int
        if (isRunning) {
            statusColor = ContextCompat.getColor(this, R.color.color_on_primary)
            containerColor = ContextCompat.getColor(this, R.color.color_primary)
        } else {
            statusColor = ContextCompat.getColor(this, R.color.color_on_tertiary_container)
            containerColor = ContextCompat.getColor(this, R.color.color_tertiary_container)
        }
        statusText.setTextColor(statusColor)
        // statusCard's background is the bg_card.xml shape drawable (GradientDrawable),
        // so we recolor it in place rather than needing a MaterialCardView.
        (statusCard.background.mutate() as? GradientDrawable)?.setColor(containerColor)

        swipeToggle.isChecked = isRunning
        runningIndicator.isVisible = isRunning
        setSlidersEnabled(isRunning && isAccessibilityEnabled)
    }

    private fun setSlidersEnabled(enabled: Boolean) {
        val dimmedText = ColorUtils.setAlphaComponent(
            ContextCompat.getColor(this, R.color.color_on_surface_variant), (0.5f * 255).toInt()
        )
        for (s in listOf(dimSlider, blueSlider, tempSlider)) {
            s.slider.isEnabled = enabled
            s.icon.setColorFilter(
                if (enabled) s.accentColor else ContextCompat.getColor(this, R.color.color_on_surface_variant)
            )
            s.label.setTextColor(
                if (enabled) ContextCompat.getColor(this, R.color.color_on_surface) else dimmedText
            )
            s.valueText.setTextColor(if (enabled) s.accentColor else dimmedText)
        }
    }
}

/** Holds the views inflated from `view_control_slider.xml` for a single slider row. */
private class ControlSliderViews(
    val icon: ImageView,
    val label: TextView,
    val valueText: TextView,
    val slider: SeekBar,
    val accentColor: Int
) {
    companion object {
        fun inflateInto(container: ViewGroup, label: String, iconRes: Int, accentColor: Int): ControlSliderViews {
            LayoutInflater.from(container.context).inflate(R.layout.view_control_slider, container, true)
            val icon = container.findViewById<ImageView>(R.id.icon)
            val labelView = container.findViewById<TextView>(R.id.label)
            val valueText = container.findViewById<TextView>(R.id.value)
            val slider = container.findViewById<SeekBar>(R.id.slider)

            icon.setImageResource(iconRes)
            icon.setColorFilter(accentColor)
            labelView.text = label
            slider.progressTintList = ColorStateList.valueOf(accentColor)
            slider.thumbTintList = ColorStateList.valueOf(accentColor)

            return ControlSliderViews(icon, labelView, valueText, slider, accentColor)
        }
    }
}

/** Direct port of the original `isAccessibilityServiceEnabled` helper. */
private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
    for (enabledService in enabledServices) {
        val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
        if (enabledServiceInfo.packageName == context.packageName && enabledServiceInfo.name == service.name) {
            return true
        }
    }
    return false
}
