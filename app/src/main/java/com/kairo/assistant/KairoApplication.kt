package com.kairo.assistant

import android.app.Application
import com.kairo.assistant.automation.TriggerEngine

/**
 * Kairo Application class.
 * Entry point for app-wide initialization.
 */
class KairoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Start the automation trigger engine
        TriggerEngine.start(this)
    }
}
