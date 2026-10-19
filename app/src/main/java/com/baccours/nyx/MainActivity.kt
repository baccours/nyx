package com.baccours.nyx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.accessibility.AccessibilityManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.databinding.ActivityMainBinding
import com.baccours.nyx.service.NyxService
import com.baccours.nyx.util.SliderMapping
import com.baccours.nyx.util.ThemeManager
import com.baccours.nyx.util.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isAccessibilityEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind system bars with current theme bar icons.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        binding.toolbar.setTitleTextAppearance(this, R.style.TextAppearance_Nyx_ToolbarTitle)
        setSupportActionBar(binding.toolbar)

        setUpSliders()
        setUpToggle()
        // Refresh state synchronously to ensure immediate UI rendering without flash.
        refreshAccessibilityState()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        // Check for accessibility setting changes made while inactive.
        refreshAccessibilityState()
    }

    override fun onStop() {
        super.onStop()
        // Flush pending slider updates to prevent data loss.
        OverlayRepository.flush()
    }

    // --- Insets -----------------------------------------------------------------------------

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // Extend app bar under status bar to avoid color seams.
            binding.appBar.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.scroll.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    // --- Theme button -----------------------------------------------------------------------

    /** Returns whether night mode is currently active. */
    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Action icon and label reflect the target state on tap.
        val night = isNightMode()
        menu.findItem(R.id.action_theme_toggle).apply {
            setIcon(if (night) R.drawable.ic_sun else R.drawable.ic_moon)
            title = getString(if (night) R.string.switch_to_light_theme else R.string.switch_to_dark_theme)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme_toggle -> {
                // Explicitly toggle theme and refresh UI.
                ThemeManager.set(this, if (isNightMode()) ThemeMode.LIGHT else ThemeMode.DARK)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- Filter toggle & sliders ------------------------------------------------------------

    private fun setUpToggle() {
        binding.swipeToggle.onCheckedChangeListener = { checked ->
            if (OverlayRepository.serviceRunning.value) {
                // Service is running; toggle filter on or off.
                OverlayRepository.setFilterEnabled(checked)
            } else {
                // Service disabled; open accessibility settings and reset toggle.
                openAccessibilitySettings()
                binding.swipeToggle.isChecked = false
            }
        }
        // Allow stopping the service when filter is disabled.
        binding.disableServiceButton.setOnClickListener { NyxService.stopService() }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.w("MainActivity", "No activity to handle accessibility settings", e)
        }
    }

    private fun setUpSliders() {
        binding.dimSlider.setOnProgressChangeListener { progress, fromUser ->
            binding.dimSlider.valueText = formatPercent(progress)
            if (fromUser) {
                OverlayRepository.update { it.copy(dimIntensity = SliderMapping.progressToFraction(progress)) }
            }
        }
        binding.blueSlider.setOnProgressChangeListener { progress, fromUser ->
            binding.blueSlider.valueText = formatPercent(progress)
            if (fromUser) {
                OverlayRepository.update { it.copy(blueLightIntensity = SliderMapping.progressToFraction(progress)) }
            }
        }
        binding.tempSlider.setOnProgressChangeListener { progress, fromUser ->
            val kelvin = SliderMapping.progressToKelvin(progress)
            binding.tempSlider.valueText = formatKelvin(kelvin)
            if (fromUser) {
                OverlayRepository.update { it.copy(colorTemperature = kelvin) }
            }
        }
        binding.blackSlider.setOnProgressChangeListener { progress, fromUser ->
            binding.blackSlider.valueText = formatPercent(progress)
            if (fromUser) {
                OverlayRepository.update { it.copy(blackLevel = SliderMapping.progressToFraction(progress)) }
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    OverlayRepository.serviceRunning.collect {
                        updateRunningUi()
                        // Sync accessibility state when service lifecycle changes.
                        refreshAccessibilityState()
                    }
                }
                launch { OverlayRepository.filterEnabled.collect { updateRunningUi() } }
                launch { OverlayRepository.settings.collect { renderSettings(it) } }
            }
        }
    }

    private fun renderSettings(settings: OverlaySettings) {
        val dim = SliderMapping.fractionToProgress(settings.dimIntensity)
        binding.dimSlider.progress = dim
        binding.dimSlider.valueText = formatPercent(dim)

        val blue = SliderMapping.fractionToProgress(settings.blueLightIntensity)
        binding.blueSlider.progress = blue
        binding.blueSlider.valueText = formatPercent(blue)

        binding.tempSlider.progress = SliderMapping.kelvinToProgress(settings.colorTemperature)
        binding.tempSlider.valueText = formatKelvin(settings.colorTemperature)

        val black = SliderMapping.fractionToProgress(settings.blackLevel)
        binding.blackSlider.progress = black
        binding.blackSlider.valueText = formatPercent(black)

        // Dim blue light dependent options when blue light intensity is 0.
        binding.blueLightOptions.alpha = if (blue == 0) 0.5f else 1f

        setSlidersEnabled(isAccessibilityEnabled && isFilterOn())
    }

    private fun formatPercent(progress: Int) = "$progress%"
    private fun formatKelvin(kelvin: Float) = "${kelvin.roundToInt()}K"

    // --- Visibility / enabled state ---------------------------------------------------------

    private fun refreshAccessibilityState() {
        val enabled = isAccessibilityServiceEnabled(this, NyxService::class.java)
        isAccessibilityEnabled = enabled
        binding.permissionCard.isVisible = !enabled
        updateDashboardVisibility()
        setSlidersEnabled(enabled && isFilterOn())
    }

    /** Show dashboard sliders only when filter is active. */
    private fun updateDashboardVisibility() {
        binding.dashboard.isVisible = isAccessibilityEnabled && isFilterOn()
    }

    /** Returns whether both service is running and filter is enabled. */
    private fun isFilterOn() = OverlayRepository.serviceRunning.value && OverlayRepository.filterEnabled.value

    private fun updateRunningUi() {
        val serviceRunning = OverlayRepository.serviceRunning.value
        val filterOn = isFilterOn()

        binding.statusText.text = getString(if (filterOn) R.string.filter_on else R.string.filter_off)
        binding.statusCard.isSelected = filterOn
        binding.statusText.isSelected = filterOn

        binding.swipeToggle.isChecked = filterOn
        binding.runningIndicator.isVisible = filterOn
        // Show idle state card when service is running with filter disabled.
        binding.serviceIdleCard.isVisible = serviceRunning && !filterOn
        updateDashboardVisibility()
        setSlidersEnabled(filterOn && isAccessibilityEnabled)
    }

    private fun setSlidersEnabled(enabled: Boolean) {
        binding.dimSlider.isEnabled = enabled
        binding.blueSlider.isEnabled = enabled
        binding.tempSlider.isEnabled = enabled
        binding.blackSlider.isEnabled = enabled
    }
}

private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC).any {
        val info = it.resolveInfo.serviceInfo
        info.packageName == context.packageName && info.name == service.name
    }
}
