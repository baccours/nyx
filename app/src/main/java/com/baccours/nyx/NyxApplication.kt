package com.baccours.nyx

import android.app.Application
import com.baccours.nyx.util.ThemeManager

class NyxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.initTheme(this)
    }
}
