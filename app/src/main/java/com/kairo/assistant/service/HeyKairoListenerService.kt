package com.kairo.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.kairo.assistant.MainActivity
import com.kairo.assistant.R
import com.kairo.assistant.wakeword.WakeWordDetector

/**
 * Foreground service that keeps the [WakeWordDetector] alive in the background.
 *
 * When "Hey Kairo" is detected, the service launches [MainActivity] with the
 * `assistant_trigger` flag and then temporarily pauses itself until the
 * activity finishes (to avoid microphone conflicts).
 *
 * The service shows a persistent notification as required by Android for
 * foreground services that use the microphone.
 */
class HeyKairoListenerService : Service() {

    companion object {
        private const val TAG = "HeyKairoListener"
        const val CHANNEL_ID = "hey_kairo_channel"
        private const val NOTIFICATION_ID = 9001
        private const val WAKE_LOCK_TAG = "kairo:wake_word_listener"

        /** SharedPreference key controlling whether the listener is enabled. */
        const val PREF_ENABLED = "hey_kairo_enabled"

        /**
         * Convenience helper to start the service if the user has it enabled.
         */
        fun startIfEnabled(context: Context) {
            val prefs = context.getSharedPreferences("kairo_prefs", MODE_PRIVATE)
            if (prefs.getBoolean(PREF_ENABLED, false)) {
                val intent = Intent(context, HeyKairoListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "Service start requested")
            }
        }

        /**
         * Stop the listener service.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, HeyKairoListenerService::class.java))
            Log.d(TAG, "Service stop requested")
        }
    }

    private var detector: WakeWordDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private fun logToFile(message: String, throwable: Throwable? = null) {
        try {
            val file = java.io.File(filesDir, "wakeword_logs.txt")
            val logText = "[${java.util.Date()}] [Service] $message\n" + 
                    (throwable?.stackTraceToString() ?: "") + "\n"
            file.appendText(logText)
            Log.d(TAG, "Log written: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    // ── Service lifecycle ───────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        logToFile("Service onCreate()")
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logToFile("Service onStartCommand()")
        startForegroundWithNotification()
        acquireWakeLock()
        startDetector()
        return START_STICKY // Restart automatically if killed
    }

    override fun onDestroy() {
        logToFile("Service onDestroy()")
        stopDetector()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Detector management ─────────────────────────────────────────────────

    private fun startDetector() {
        logToFile("startDetector() requested")
        if (detector?.isRunning() == true) {
            logToFile("Detector already running - ignoring")
            return
        }

        detector = WakeWordDetector(applicationContext).apply {
            setOnWakeWordDetectedListener { transcript ->
                logToFile("WakeWordDetector callback: wake word detected! Transcript: $transcript")
                onWakeWordDetected()
            }
        }

        // SpeechRecognizer requires main thread
        android.os.Handler(mainLooper).post {
            logToFile("Calling detector.start()")
            detector?.start()
        }
    }

    private fun stopDetector() {
        logToFile("stopDetector() requested")
        android.os.Handler(mainLooper).post {
            detector?.stop()
            detector = null
        }
    }

    private fun onWakeWordDetected() {
        logToFile("onWakeWordDetected() inside service called")
        // Stop listening to release the mic before launching the activity
        android.os.Handler(mainLooper).post {
            detector?.stop()
        }

        // Launch MainActivity
        try {
            logToFile("Preparing to launch MainActivity...")
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("assistant_trigger", true)
                putExtra("from_wake_word", true)
            }
            startActivity(launchIntent)
            logToFile("startActivity(MainActivity) succeeded")
        } catch (e: Exception) {
            logToFile("CRITICAL: Failed to launch MainActivity", e)
            // Restart detector since we failed to hand off
            android.os.Handler(mainLooper).post {
                detector?.start()
            }
        }
    }

    /**
     * Called by [MainActivity] when it goes to background/finishes so the
     * detector can resume listening.
     */
    fun resumeDetection() {
        android.os.Handler(mainLooper).post {
            if (detector == null) {
                startDetector()
            } else {
                detector?.start()
            }
        }
    }

    // ── Notification ────────────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kairo Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Kairo is listening for the wake word"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.apply {
            setContentTitle("Kairo is listening")
            setContentText("Say \"Kairo\" or \"Hey Kairo\" to activate")
            setSmallIcon(R.mipmap.ic_launcher)
            setContentIntent(pendingIntent)
            setOngoing(true)
        }.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ── Wake lock ───────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes max, re-acquired on each restart
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock", e)
        }
        wakeLock = null
    }
}
