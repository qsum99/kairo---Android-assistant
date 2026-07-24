package com.kairo.assistant

import android.app.Application
import android.content.Context
import android.provider.Settings
import com.kairo.assistant.service.LockScreenLauncherService

/**
 * Kairo Application class.
 * Entry point for app-wide initialization.
 */
class KairoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
        val allowOnLockScreen = prefs.getBoolean("allow_on_lock_screen", false)
        if (allowOnLockScreen && Settings.canDrawOverlays(this)) {
            LockScreenLauncherService.start(this)
        }
    }
}
