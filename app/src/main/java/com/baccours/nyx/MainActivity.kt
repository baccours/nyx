package com.baccours.nyx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.baccours.nyx.data.FilterEngine
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.databinding.ActivityMainBinding
import com.baccours.nyx.service.NyxService
import com.baccours.nyx.service.SystemColorFilter
import com.baccours.nyx.util.SliderMapping
import com.baccours.nyx.util.ThemeManager
import com.baccours.nyx.util.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isAccessibilityEnabled = false

    /** True while the system engine is really in use (selected AND permitted); Black Level is moot then. */
    private var systemModeActive = false

    /** Guards the mode switch's listener while we set it programmatically. */
    private var updatingSystemSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must come first. Draws behind the system bars and picks light/dark bar icons from the
        // current (day/night) configuration, which replaces the old statusBarColor/is_light_bars.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        binding.toolbar.setTitleTextAppearance(this, R.style.TextAppearance_Nyx_ToolbarTitle)
        setSupportActionBar(binding.toolbar)

        setUpSliders()
        setUpToggle()
        setUpSystemMode()
        // Resolve synchronously so the very first frame (and every frame after recreation,
        // e.g. when switching theme) already shows the right card instead of a blank screen.
        refreshAccessibilityState()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        // The user may have enabled/disabled the service in system Settings while we were away.
        refreshAccessibilityState()
        // ...or granted the one-time adb permission for the system engine.
        renderSettings(OverlayRepository.settings.value)
    }

    override fun onStop() {
        super.onStop()
        // Don't lose a slider change still waiting in the debounce window if we get killed.
        OverlayRepository.flush()
    }

    // --- Insets -----------------------------------------------------------------------------

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // The colored app bar extends under the status bar, so there is no color seam.
            binding.appBar.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.scroll.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    // --- Theme button -----------------------------------------------------------------------

    /** What is actually rendered right now, independent of what the stored preference says. */
    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Icon and label both describe the action (what a tap will do), not the current state.
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
                // Always flips the real theme, so the activity is always recreated and the
                // menu icon refreshed (also when the stored mode was "follow system").
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
                // The service stays enabled: this only switches the filter (overlay) on or off.
                OverlayRepository.setFilterEnabled(checked)
            } else {
                // The service is not enabled yet; that can only be done in Settings. Don't leave
                // the thumb parked on "ON" while the status still says "OFF".
                openAccessibilitySettings()
                binding.swipeToggle.isChecked = false
            }
        }
        // Filter off but service still on: offer to turn the service itself off.
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

    // --- True black (system) mode -----------------------------------------------------------

    private fun setUpSystemMode() {
        binding.systemModeSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingSystemSwitch) return@setOnCheckedChangeListener
            if (!checked) {
                OverlayRepository.update { it.copy(engine = FilterEngine.OVERLAY) }
            } else if (SystemColorFilter.availability(this) == SystemColorFilter.Availability.AVAILABLE) {
                OverlayRepository.update { it.copy(engine = FilterEngine.SYSTEM) }
            } else {
                setSystemSwitch(false)
                showSystemModeSetupDialog()
            }
        }
    }

    private fun setSystemSwitch(checked: Boolean) {
        updatingSystemSwitch = true
        binding.systemModeSwitch.isChecked = checked
        updatingSystemSwitch = false
    }

    private fun showSystemModeSetupDialog() {
        val command = SystemColorFilter.grantCommand(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.system_mode_setup_title)
            .setMessage(getString(R.string.system_mode_setup_body, command))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.system_mode_copy) { _, _ -> copyToClipboard(command) }
            .show()
        dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("adb command", text))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.system_mode_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    OverlayRepository.serviceRunning.collect {
                        updateRunningUi()
                        // Service lifecycle is the signal that the permission state changed.
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

        // Color Temperature and Black Level only act through the Blue Light Filter: dim the nested
        // block (still adjustable, so values can be prepared) while the filter has no effect.
        binding.blueLightOptions.alpha = if (blue == 0) 0.5f else 1f

        val availability = SystemColorFilter.availability(this)
        systemModeActive = settings.engine == FilterEngine.SYSTEM &&
                availability == SystemColorFilter.Availability.AVAILABLE
        // Hidden entirely on devices without the Night Light color transform.
        binding.systemModeGroup.isVisible = availability != SystemColorFilter.Availability.UNSUPPORTED
        setSystemSwitch(systemModeActive)
        binding.systemModeSummary.setText(
            if (systemModeActive) R.string.system_mode_summary_on else R.string.system_mode_summary_off
        )
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

    /** The sliders only make sense (and are only shown) while the filter is actually on. */
    private fun updateDashboardVisibility() {
        binding.dashboard.isVisible = isAccessibilityEnabled && isFilterOn()
    }

    /** The filter is really applied: the service is running AND the user has not switched it off. */
    private fun isFilterOn() = OverlayRepository.serviceRunning.value && OverlayRepository.filterEnabled.value

    private fun updateRunningUi() {
        val serviceRunning = OverlayRepository.serviceRunning.value
        val filterOn = isFilterOn()

        binding.statusText.text = getString(if (filterOn) R.string.filter_on else R.string.filter_off)
        binding.statusCard.isSelected = filterOn
        binding.statusText.isSelected = filterOn

        binding.swipeToggle.isChecked = filterOn
        binding.runningIndicator.isVisible = filterOn
        // Service on, filter off: nothing is drawn, but the service is still running in the background.
        binding.serviceIdleCard.isVisible = serviceRunning && !filterOn
        updateDashboardVisibility()
        setSlidersEnabled(filterOn && isAccessibilityEnabled)
    }

    private fun setSlidersEnabled(enabled: Boolean) {
        binding.dimSlider.isEnabled = enabled
        binding.blueSlider.isEnabled = enabled
        binding.tempSlider.isEnabled = enabled
        // The system engine has no black-level trade-off: blacks are always exact.
        binding.blackSlider.isEnabled = enabled && !systemModeActive
    }
}

private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC).any {
        val info = it.resolveInfo.serviceInfo
        info.packageName == context.packageName && info.name == service.name
    }
}
