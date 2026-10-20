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
    val status = ServiceStatus()
    val repository = OverlayRepository(
        store = PrefsSettingsStore.create(context),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )
}

class NyxApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ThemeManager.init(this)
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as NyxApplication).container
