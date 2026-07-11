package com.kairo.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kairo.assistant.MainActivity
import com.kairo.assistant.R

/**
 * Lightweight foreground service that continuously listens for the wake word "Kairo".
 * When detected, it launches the MainActivity overlay so the user can issue voice commands.
 */
class KairoWakeWordService : Service() {

    companion object {
        private const val TAG = "KairoWakeWord"
        private const val CHANNEL_ID = "kairo_wake_word_channel"
        private const val NOTIFICATION_ID = 2001
        private const val WAKE_WORD = "kairo"
        private const val RESTART_DELAY_MS = 500L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "Wake word service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startWakeWordListening()
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopWakeWordListening()
        super.onDestroy()
        Log.d(TAG, "Wake word service destroyed")
    }

    private fun startWakeWordListening() {
        if (isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(wakeWordListener)
            
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }

            speechRecognizer?.startListening(recognizerIntent)
            isListening = true
            Log.d(TAG, "Wake word listening started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word listening", e)
            scheduleRestart()
        }
    }

    private fun stopWakeWordListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer", e)
        }
        speechRecognizer = null
    }

    private fun scheduleRestart() {
        isListening = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null

        // Restart listening after a short delay
        android.os.Handler(mainLooper).postDelayed({
            if (isServiceRunning()) {
                startWakeWordListening()
            }
        }, RESTART_DELAY_MS)
    }

    private fun isServiceRunning(): Boolean {
        // Check if the service preference is still enabled
        val prefs = getSharedPreferences("kairo_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("wake_word_enabled", false)
    }

    private fun checkForWakeWord(text: String): Boolean {
        return text.lowercase().contains(WAKE_WORD)
    }

    private fun onWakeWordDetected() {
        Log.d(TAG, "🎤 Wake word 'Kairo' detected! Launching app...")
        
        // Stop listening before launching (mic will be used by the app)
        stopWakeWordListening()

        // Launch the main activity
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("from_wake_word", true)
        }
        startActivity(launchIntent)

        // Restart wake word listening after a delay (app will take over mic, resume after it's done)
        android.os.Handler(mainLooper).postDelayed({
            if (isServiceRunning()) {
                startWakeWordListening()
            }
        }, 10000) // Wait 10 seconds before re-listening (give app time to use mic)
    }

    private val wakeWordListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for wake word...")
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech, will restart...")
        }

        override fun onError(error: Int) {
            val errorName = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "TIMEOUT"
                SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
                SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
                SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
                else -> "CODE_$error"
            }
            Log.d(TAG, "Wake word listener error: $errorName — restarting...")
            scheduleRestart()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "Wake word results: $matches")
            
            if (matches != null) {
                for (match in matches) {
                    if (checkForWakeWord(match)) {
                        onWakeWordDetected()
                        return
                    }
                }
            }
            // No wake word found, restart listening
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches != null) {
                for (match in matches) {
                    if (checkForWakeWord(match)) {
                        onWakeWordDetected()
                        return
                    }
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kairo Wake Word",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Listening for 'Kairo' wake word"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kairo is listening")
            .setContentText("Say \"Kairo\" to activate")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
