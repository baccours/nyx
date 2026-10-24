package com.baccours.nyx

import android.content.Context
import com.baccours.nyx.data.DefaultOverlaySettingsRepository
import com.baccours.nyx.data.AppPreferences
import com.baccours.nyx.data.OverlaySettingsPrefs
import com.baccours.nyx.data.OverlaySettingsRepository
import com.baccours.nyx.data.OverlayStatePrefs
import com.baccours.nyx.data.ThemePrefs
import com.baccours.nyx.state.OverlayController
import com.baccours.nyx.ui.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies shared by the activity and the accessibility service. */
class AppContainer(context: Context) {
    // Opening the file starts SharedPreferences' background parse right away...
    private val prefs = AppPreferences.open(context)

    val overlayController = OverlayController(OverlayStatePrefs(prefs))

    // ...while the blocking read in the repository's constructor waits until first use, by which
    // time the parse has normally finished (and a process that never needs it never pays).
    val repository: OverlaySettingsRepository by lazy {
        DefaultOverlaySettingsRepository(
            store = OverlaySettingsPrefs(prefs),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    // Read synchronously when the activity starts (see MainActivity.attachBaseContext).
    val themeManager by lazy { ThemeManager(ThemePrefs(prefs)) }
}
