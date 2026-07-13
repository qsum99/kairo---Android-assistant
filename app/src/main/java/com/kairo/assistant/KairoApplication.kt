package com.kairo.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.kairo.assistant.service.HeyKairoListenerService

/**
 * Kairo Application class.
 * Entry point for app-wide initialization.
 */
class KairoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("KairoApp", "Kairo Application initialized")
        createWakeWordNotificationChannel()

        // Auto-start the "Hey Kairo" listener if the user has it enabled
        HeyKairoListenerService.startIfEnabled(this)
    }

    private fun createWakeWordNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                HeyKairoListenerService.CHANNEL_ID,
                "Kairo Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Kairo is listening for the wake word"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
            Log.d("KairoApp", "Wake word notification channel created")
        }
    }
}
