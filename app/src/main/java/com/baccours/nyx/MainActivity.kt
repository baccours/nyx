package com.baccours.nyx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.accessibility.AccessibilityManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.baccours.nyx.components.ControlSliderView
import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.databinding.ActivityMainBinding
import com.baccours.nyx.service.NyxService
import com.baccours.nyx.ui.MainViewModel
import com.baccours.nyx.ui.UiState
import com.baccours.nyx.util.SliderMapping
import com.baccours.nyx.util.ThemeManager
import com.baccours.nyx.util.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    /** One slider row: how it reads from, and writes to, [OverlaySettings]. */
    private class SliderSpec(
        val view: ControlSliderView,
        val progressOf: (OverlaySettings) -> Int,
        val withProgress: (OverlaySettings, Int) -> OverlaySettings,
        val format: (Int) -> String
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var sliders: List<SliderSpec>

    private val viewModel: MainViewModel by viewModels {
        // The ViewModel outlives this Activity, so the lambda must only capture the app context.
        val appContext = applicationContext
        MainViewModel.factory(appContext.appContainer) {
            isAccessibilityServiceEnabled(appContext, NyxService::class.java)
        }
    }

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
        setUpPermissionCard()
        // Render synchronously so the first frame is already correct (no flash).
        render(viewModel.uiState.value)
        observeState()
    }

    override fun onRestart() {
        super.onRestart()
        // Back from another screen (typically system settings): the service may have been
        // enabled or disabled meanwhile. Not needed on a cold start, where onCreate just asked.
        viewModel.refresh()
    }

    override fun onStop() {
        super.onStop()
        // Flush pending slider updates to prevent data loss.
        viewModel.flush()
    }

    // --- Insets -----------------------------------------------------------------------------

    private fun applyWindowInsets() {
        // The content column is capped at content_max_width; on wider windows the surplus becomes
        // side gutters. screenWidthDp follows the window size, including in multi-window mode.
        val maxWidthPx = resources.getDimensionPixelSize(R.dimen.content_max_width)
        val windowWidthPx = (resources.configuration.screenWidthDp * resources.displayMetrics.density).toInt()
        val gutter = ((windowWidthPx - maxWidthPx) / 2).coerceAtLeast(0)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // Extend app bar under status bar to avoid color seams.
            binding.appBar.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.scroll.updatePadding(left = bars.left + gutter, right = bars.right + gutter, bottom = bars.bottom)
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

    // --- Filter toggle, permission card & sliders -------------------------------------------

    private fun setUpToggle() {
        binding.swipeToggle.onCheckedChangeListener = { checked ->
            if (viewModel.isServiceRunning) {
                // Service is running; toggle filter on or off.
                viewModel.setFilterEnabled(checked)
            } else {
                // Service disabled; open accessibility settings and reset toggle.
                startSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                binding.swipeToggle.isChecked = false
            }
        }
        // Allow stopping the service when filter is disabled.
        binding.disableServiceButton.setOnClickListener { viewModel.requestDisableService() }
    }

    private fun setUpPermissionCard() {
        binding.openAccessibilitySettingsButton.setOnClickListener {
            startSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        // "Restricted settings" only exists on Android 13+.
        binding.restrictedSettingsHelp.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        binding.openAppInfoButton.setOnClickListener {
            startSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    private fun startSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity to handle ${intent.action}", e)
        }
    }

    private fun setUpSliders() {
        val percent = { progress: Int -> getString(R.string.value_percent, progress) }
        sliders = listOf(
            SliderSpec(
                binding.dimSlider,
                progressOf = { SliderMapping.fractionToProgress(it.dimIntensity) },
                withProgress = { s, p -> s.copy(dimIntensity = SliderMapping.progressToFraction(p)) },
                format = percent
            ),
            SliderSpec(
                binding.blueSlider,
                progressOf = { SliderMapping.fractionToProgress(it.blueLightIntensity) },
                withProgress = { s, p -> s.copy(blueLightIntensity = SliderMapping.progressToFraction(p)) },
                format = percent
            ),
            SliderSpec(
                binding.tempSlider,
                progressOf = { SliderMapping.kelvinToProgress(it.colorTemperature) },
                withProgress = { s, p -> s.copy(colorTemperature = SliderMapping.progressToKelvin(p)) },
                format = { p -> getString(R.string.value_kelvin, SliderMapping.progressToKelvin(p).roundToInt()) }
            ),
            SliderSpec(
                binding.blackSlider,
                progressOf = { SliderMapping.fractionToProgress(it.blackLevel) },
                withProgress = { s, p -> s.copy(blackLevel = SliderMapping.progressToFraction(p)) },
                format = percent
            )
        )
        for (spec in sliders) {
            spec.view.setOnProgressChangeListener { progress, fromUser ->
                spec.view.valueText = spec.format(progress)
                if (fromUser) viewModel.updateSettings { spec.withProgress(it, progress) }
            }
        }
    }

    // --- Rendering --------------------------------------------------------------------------

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: UiState) {
        val filterOn = state is UiState.FilterOn

        binding.statusText.text = getString(if (filterOn) R.string.filter_on else R.string.filter_off)
        binding.statusCard.isSelected = filterOn
        binding.statusText.isSelected = filterOn
        binding.swipeToggle.isChecked = filterOn

        binding.permissionCard.isVisible = state is UiState.NeedsPermission
        binding.serviceIdleCard.isVisible = state is UiState.ServiceIdle
        binding.dashboard.isVisible = filterOn

        if (state is UiState.FilterOn) renderSettings(state.settings)
    }

    private fun renderSettings(settings: OverlaySettings) {
        for (spec in sliders) {
            val progress = spec.progressOf(settings)
            spec.view.progress = progress
            spec.view.valueText = spec.format(progress)
        }
        // Dim blue light dependent options when blue light intensity is 0.
        val blue = SliderMapping.fractionToProgress(settings.blueLightIntensity)
        binding.blueLightOptions.alpha = if (blue == 0) 0.5f else 1f
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    // FEEDBACK_ALL_MASK: don't silently break if the feedback type in the service XML ever changes.
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
        val info = it.resolveInfo.serviceInfo
        info.packageName == context.packageName && info.name == service.name
    }
}
