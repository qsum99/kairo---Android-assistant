package com.kairo.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kairo.assistant.MainActivity
import java.util.Locale

class KairoWakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val CHANNEL_ID = "kairo_wake_word"
        private const val FOREGROUND_ID = 1001
        private const val WAKEUP_NOTIFICATION_ID = 1002

        var isAppActive = false
        private var instance: KairoWakeWordService? = null

        fun stopListeningForWakeWord() {
            instance?.stopListening()
        }

        fun startListeningForWakeWord() {
            instance?.startListening()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d("KairoWakeWordService", "Screen receiver action: $action")
            
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val isLocked = keyguardManager.isKeyguardLocked

            when (action) {
                Intent.ACTION_SCREEN_OFF -> {
                    stopListening()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (isLocked) {
                        stopListening()
                    } else {
                        startListening()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kairo Wake Word Service")
            .setContentText("Listening for 'Kairo'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(FOREGROUND_ID, notification)
        }

        startListening()

        return START_STICKY
    }

    private fun startListening() {
        if (isAppActive || isListening) return

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("KairoWakeWordService", "RECORD_AUDIO permission not granted")
            return
        }
        
        handler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                } else {
                    try {
                        speechRecognizer?.cancel()
                    } catch (e: Exception) {
                        Log.w("KairoWakeWordService", "Error cancelling recognizer", e)
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                }
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                    }
                    override fun onError(error: Int) {
                        isListening = false
                        Log.d("KairoWakeWordService", "Speech recognition error: $error")
                        if (error == SpeechRecognizer.ERROR_CLIENT || error == 11) {
                            stopListening()
                        }
                        // If app goes active, do not restart
                        if (!isAppActive) {
                            handler.postDelayed({ startListening() }, 500)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].lowercase()
                            Log.d("KairoWakeWordService", "Heard in background: $text")
                            if (text.contains("kairo") || text.contains("cairo") || text.contains("chiro") || text.contains("hero") || text.contains("hello kairo")) {
                                wakeupAssistant()
                            }
                        }
                        if (!isAppActive) {
                            handler.postDelayed({ startListening() }, 500)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].lowercase()
                            if (text.contains("kairo") || text.contains("cairo") || text.contains("chiro") || text.contains("hero") || text.contains("hello kairo")) {
                                wakeupAssistant()
                            }
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("KairoWakeWordService", "Failed to start listening", e)
                isListening = false
                if (!isAppActive) {
                    handler.postDelayed({ startListening() }, 1000)
                }
            }
        }
    }

    private fun stopListening() {
        handler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w("KairoWakeWordService", "Error stopping recognizer", e)
            }
            speechRecognizer = null
            isListening = false
        }
    }

    private fun wakeupAssistant() {
        // Stop listening temporarily to let app take over mic
        stopListening()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("start_listening_auto", true)
        }

        val prefs = getSharedPreferences("kairo_prefs", MODE_PRIVATE)
        val allowOnLockScreen = prefs.getBoolean("allow_on_lock_screen", false)

        if (allowOnLockScreen) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Kairo Voice Assistant")
                .setContentText("Kairo is activated")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(WAKEUP_NOTIFICATION_ID, notification)
        } else {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("KairoWakeWordService", "Failed to start activity directly", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kairo Wake Word Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs background wake word detection for 'Kairo'"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.w("KairoWakeWordService", "Error unregistering screen receiver", e)
        }
        stopListening()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
