package com.baccours.nyx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.baccours.nyx.databinding.ActivityMainBinding
import com.baccours.nyx.service.NyxService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isAccessibilityEnabled: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setTitleTextAppearance(this, R.style.TextAppearance_Nyx_ToolbarTitle)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        setUpSliders()
        setUpToggle()
        observeServiceState()
        pollAccessibilityStatus()
    }

    private fun setUpToggle() {
        binding.swipeToggle.onCheckedChangeListener = { newState ->
            if (!newState) {
                NyxService.isServiceRunning.value = false
                NyxService.stopService()
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    private fun setUpSliders() {
        // Dimming intensity: SeekBar is 0...100, service value is 0f...1f.
        binding.dimSlider.setOnProgressChangeListener { progress, fromUser ->
            val value = progress / 100f
            binding.dimSlider.valueText = "$progress%"
            if (fromUser) NyxService.dimIntensity.value = value
        }
        // Blue light filter: same 0...100 -> 0f...1f mapping.
        binding.blueSlider.setOnProgressChangeListener { progress, fromUser ->
            val value = progress / 100f
            binding.blueSlider.valueText = "$progress%"
            if (fromUser) NyxService.blueLightIntensity.value = value
        }
        // Color temperature: SeekBar 0...100 mapped to 1000K...7000K.
        binding.tempSlider.setOnProgressChangeListener { progress, fromUser ->
            val kelvin = 1000f + (progress / 100f) * 6000f
            binding.tempSlider.valueText = "${kelvin.toInt()}K"
            if (fromUser) NyxService.colorTemperature.value = kelvin
        }
    }

    private fun observeServiceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    NyxService.isServiceRunning.collect { running -> updateRunningUi(running) }
                }
                launch {
                    NyxService.dimIntensity.collect { value ->
                        val progress = (value * 100).toInt().coerceIn(0, 100)
                        binding.dimSlider.progress = progress
                        binding.dimSlider.valueText = "$progress%"
                    }
                }
                launch {
                    NyxService.blueLightIntensity.collect { value ->
                        val progress = (value * 100).toInt().coerceIn(0, 100)
                        binding.blueSlider.progress = progress
                        binding.blueSlider.valueText = "$progress%"
                    }
                }
                launch {
                    NyxService.colorTemperature.collect { kelvin ->
                        val progress = (((kelvin - 1000f) / 6000f) * 100).toInt().coerceIn(0, 100)
                        binding.tempSlider.progress = progress
                        binding.tempSlider.valueText = "${kelvin.toInt()}K"
                    }
                }
            }
        }
    }

    private fun pollAccessibilityStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    setAccessibilityEnabled(isAccessibilityServiceEnabled(this@MainActivity, NyxService::class.java))
                    delay(1000.milliseconds)
                }
            }
        }
    }

    private fun setAccessibilityEnabled(enabled: Boolean) {
        if (isAccessibilityEnabled == enabled) return
        isAccessibilityEnabled = enabled
        binding.permissionCard.isVisible = !enabled
        binding.dashboard.isVisible = enabled
        setSlidersEnabled(enabled && NyxService.isServiceRunning.value)
    }

    private fun updateRunningUi(isRunning: Boolean) {
        binding.statusText.text = if (isRunning) getString(R.string.filter_on) else getString(R.string.filter_off)
        binding.statusCard.isSelected = isRunning
        binding.statusText.isSelected = isRunning

        binding.swipeToggle.isChecked = isRunning
        binding.runningIndicator.isVisible = isRunning
        setSlidersEnabled(isRunning && isAccessibilityEnabled == true)
    }

    private fun setSlidersEnabled(enabled: Boolean) {
        binding.dimSlider.isEnabled = enabled
        binding.blueSlider.isEnabled = enabled
        binding.tempSlider.isEnabled = enabled
    }
}

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
