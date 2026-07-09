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
import android.media.AudioManager
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

    private var originalSystemVolume: Int = -1
    private var originalNotificationVolume: Int = -1

    private fun muteSystemSounds() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
            originalNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true)
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true)
            }
            Log.d("KairoWakeWordService", "Muted system streams to silence background speech recognizer beep")
        } catch (e: Exception) {
            Log.w("KairoWakeWordService", "Failed to mute background system streams", e)
        }
    }

    private fun unmuteSystemSounds() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false)
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false)
            }

            if (originalSystemVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                originalSystemVolume = -1
            }
            if (originalNotificationVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotificationVolume, 0)
                originalNotificationVolume = -1
            }
            Log.d("KairoWakeWordService", "Restored background system stream volumes")
        } catch (e: Exception) {
            Log.w("KairoWakeWordService", "Failed to restore background system streams", e)
        }
    }

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
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 20000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 20000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 20000L)
                }
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        unmuteSystemSounds() // Unmute since start beep has completed silently
                    }
                    override fun onBeginningOfSpeech() {
                        unmuteSystemSounds()
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                        muteSystemSounds() // Mute system streams to silence ending beep sound
                    }
                    override fun onError(error: Int) {
                        unmuteSystemSounds() // Ensure streams are unmuted
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
                        unmuteSystemSounds() // Restore streams volume
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
                
                muteSystemSounds() // Mute system streams before starting to listen
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                unmuteSystemSounds() // Safety unmute on fail
                Log.e("KairoWakeWordService", "Failed to start listening", e)
                isListening = false
                if (!isAppActive) {
                    handler.postDelayed({ startListening() }, 1000)
                }
            }
        }
    }

    private fun stopListening() {
        unmuteSystemSounds() // Restore volume
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

        // 1. Try to launch activity directly (bypasses notification if screen is unlocked or app is default assistant)
        try {
            startActivity(intent)
            Log.d("KairoWakeWordService", "Successfully launched assistant activity directly")
        } catch (e: Exception) {
            Log.w("KairoWakeWordService", "Direct startActivity failed (Android background launch limit). Relying on notification fallback.", e)
        }

        // 2. Fire high-priority notification with full-screen intent to ensure it triggers (on lockscreen or background fallback)
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
        try {
            notificationManager.notify(WAKEUP_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("KairoWakeWordService", "Failed to post wakeup notification", e)
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i("KairoWakeWordService", "App task removed. Scheduling wake word service restart...")
        
        // Re-start the service via alarm manager so it survives swiping the app out of recents
        val restartIntent = Intent(applicationContext, this.javaClass).apply {
            setPackage(packageName)
        }
        val pendingIntent = PendingIntent.getService(
            this,
            999,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        try {
            alarmManager.set(
                android.app.AlarmManager.RTC,
                System.currentTimeMillis() + 1000,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e("KairoWakeWordService", "Failed to schedule service restart on task removal", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unmuteSystemSounds() // Ensure volume is restored if stopped or destroyed
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
