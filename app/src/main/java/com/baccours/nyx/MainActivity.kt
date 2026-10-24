package com.baccours.nyx

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
import com.baccours.nyx.service.ServiceEnabledMonitor
import com.baccours.nyx.ui.MainViewModel
import com.baccours.nyx.ui.SliderType
import com.baccours.nyx.ui.ThemeMode
import com.baccours.nyx.ui.UiEvent
import com.baccours.nyx.ui.UiState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sliders: Map<SliderType, ControlSliderView>

    private val viewModel: MainViewModel by viewModels {
        // The ViewModel outlives this Activity, so the lambda must only capture the app context.
        val appContext = applicationContext
        val monitor = ServiceEnabledMonitor(appContext)
        MainViewModel.factory(appContext.appContainer, monitor::isEnabled, monitor.changes())
    }

    override fun attachBaseContext(newBase: Context) {
        // Before AppCompat reads the default night mode, so the right theme is used from the first
        // frame (applying it later would recreate the activity). Only the UI process pays this read.
        newBase.appContainer.themeManager.applyStored()
        super.attachBaseContext(newBase)
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
        renderSettings(viewModel.settings.value)
        observeState()
    }

    override fun onResume() {
        super.onResume()
        // Back from system settings: recheck if stuck waiting (the settings observer covers the rest).
        viewModel.onResume()
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
                appContainer.themeManager.set(if (isNightMode()) ThemeMode.LIGHT else ThemeMode.DARK)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- Overlay toggle, permission card & sliders -------------------------------------------

    private fun setUpToggle() {
        binding.swipeToggle.onCheckedChangeListener = viewModel::onToggle
        // Allow stopping the service when overlay is inactive.
        binding.disableServiceButton.setOnClickListener { viewModel.requestDisableService() }
    }

    private fun setUpPermissionCard() {
        binding.openAccessibilitySettingsButton.setOnClickListener { openAccessibilitySettings() }
        binding.notRespondingSettingsButton.setOnClickListener { openAccessibilitySettings() }
        // "Restricted settings" only exists on Android 13+.
        binding.restrictedSettingsHelp.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        binding.openAppInfoButton.setOnClickListener {
            startSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    private fun openAccessibilitySettings() = startSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun startSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity to handle ${intent.action}", e)
        }
    }

    private fun setUpSliders() {
        sliders = mapOf(
            SliderType.DIM_INTENSITY to binding.dimSlider,
            SliderType.BLUE_LIGHT to binding.blueSlider,
            SliderType.COLOR_TEMPERATURE to binding.tempSlider,
            SliderType.BLACK_LEVEL to binding.blackSlider
        )
        for ((type, view) in sliders) {
            view.setOnProgressChangeListener { progress, fromUser ->
                view.valueText = type.formatValue(this, progress)
                if (fromUser) viewModel.setSlider(type, progress)
            }
        }
    }

    // --- Rendering --------------------------------------------------------------------------

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.settings.collect { renderSettings(it) } }
                launch { viewModel.events.collect { handle(it) } }
            }
        }
    }

    private fun handle(event: UiEvent) {
        when (event) {
            UiEvent.OpenAccessibilitySettings -> {
                openAccessibilitySettings()
                binding.swipeToggle.isChecked = false // Nothing changed: snap back.
            }
        }
    }

    private fun render(state: UiState) {
        val overlayOn = state is UiState.OverlayOn

        binding.statusText.text = getString(if (overlayOn) R.string.overlay_on else R.string.overlay_off)
        binding.statusCard.isSelected = overlayOn
        binding.statusText.isSelected = overlayOn
        binding.swipeToggle.isChecked = overlayOn

        val needsPermission = state as? UiState.NeedsPermission
        binding.permissionCard.isVisible = needsPermission != null
        val error = needsPermission?.error
        binding.permissionError.isVisible = error != null
        if (error != null) binding.permissionError.text = getString(R.string.overlay_error, error)
        binding.notRespondingCard.isVisible = state is UiState.NotResponding
        binding.serviceIdleCard.isVisible = state is UiState.ServiceIdle
        binding.dashboard.isVisible = overlayOn
    }

    private fun renderSettings(settings: OverlaySettings) {
        for ((type, view) in sliders) {
            // The user's finger is the source of truth while dragging.
            if (view.isDragging) continue
            val progress = type.progressOf(settings)
            view.progress = progress
            view.valueText = type.formatValue(this, progress)
        }
        // Dim blue light dependent options when blue light intensity is 0.
        binding.blueLightOptions.alpha = if (SliderType.BLUE_LIGHT.progressOf(settings) == 0) 0.5f else 1f
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
