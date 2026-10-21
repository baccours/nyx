package com.baccours.nyx

import android.content.Context
import com.baccours.nyx.data.FilterController
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.PrefNames
import com.baccours.nyx.data.PrefsSettingsStore
import com.baccours.nyx.ui.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies shared by the activity and the accessibility service. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // Creating the handle starts SharedPreferences' background parse right away...
    private val settingsStore = PrefsSettingsStore.create(context)

    val filter = FilterController(settingsStore)

    // ...while the blocking read in the repository's constructor waits until first use, by which
    // time the parse has normally finished (and a process that never needs it never pays).
    val repository by lazy {
        OverlayRepository(
            store = settingsStore,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    // Read synchronously when the activity starts (see MainActivity.attachBaseContext).
    val themeManager by lazy {
        ThemeManager(appContext.getSharedPreferences(PrefNames.THEME, Context.MODE_PRIVATE))
    }
}
