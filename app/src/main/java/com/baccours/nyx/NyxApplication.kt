package com.baccours.nyx

import android.app.Application
import android.content.Context
import com.baccours.nyx.data.OverlayRepository
import com.baccours.nyx.data.PrefsSettingsStore
import com.baccours.nyx.data.ServiceStatus
import com.baccours.nyx.util.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies shared by the activity and the accessibility service. */
class AppContainer(context: Context) {
    // Creating the handle starts SharedPreferences' background parse right away...
    private val settingsStore = PrefsSettingsStore.create(context)

    val status = ServiceStatus()

    // ...while the blocking read in the repository's constructor waits until first use, by which
    // time the parse has normally finished (and a process that never needs it never pays).
    val repository by lazy {
        OverlayRepository(
            store = settingsStore,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }
}

class NyxApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Container first: it kicks off the settings file parse, which then overlaps with the
        // theme read below. That read stays synchronous on purpose: the night mode must be applied
        // before the first activity inflates, or the wrong theme flashes.
        container = AppContainer(this)
        ThemeManager.init(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as NyxApplication).container
