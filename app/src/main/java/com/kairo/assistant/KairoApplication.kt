package com.kairo.assistant

import android.app.Application
import android.util.Log

/**
 * Kairo Application class.
 * Entry point for app-wide initialization.
 */
class KairoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("KairoApp", "Kairo Application initialized")
    }
}
