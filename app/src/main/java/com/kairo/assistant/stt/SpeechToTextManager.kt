package com.kairo.assistant.stt

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class SpeechToTextManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isCurrentlyListening = false

    private var originalSystemVolume: Int = -1
    private var originalNotificationVolume: Int = -1

    private fun muteSystemSounds() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
            Log.d("SpeechToTextManager", "Muted system streams to silence start/stop voice ding sounds")
        } catch (e: Exception) {
            Log.w("SpeechToTextManager", "Failed to mute system streams", e)
        }
    }

    private fun unmuteSystemSounds() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
            Log.d("SpeechToTextManager", "Unmuted system streams")
        } catch (e: Exception) {
            Log.w("SpeechToTextManager", "Failed to restore system streams volume", e)
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Reuse the recognizer instance if it exists, otherwise create it
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w("SpeechToTextManager", "Error cancelling active recognition", e)
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechToTextManager", "Ready for speech")
                unmuteSystemSounds() // Unmute since the start-beep has already executed silently
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechToTextManager", "Speech started")
                unmuteSystemSounds()
            }

            override fun onRmsChanged(rmsdB: Float) {
                // No-op
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // No-op
            }

            override fun onEndOfSpeech() {
                Log.d("SpeechToTextManager", "Speech ended")
                isCurrentlyListening = false
                muteSystemSounds() // Mute system streams to silence the ending voice ding sound
            }

            override fun onError(error: Int) {
                unmuteSystemSounds() // Ensure system streams are unmuted on failure
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    13 -> "Voice model unavailable. Please update Google Speech Services or connect to the internet to download it."
                    11 -> "Server disconnected. Retrying connection..."
                    else -> "Unknown error (code: $error)"
                }
                Log.e("SpeechToTextManager", "Recognition error: $errorMessage")
                isCurrentlyListening = false
                
                // If it is a critical client/server binding issue, destroy the instance to force recreate next time
                if (error == 11 || error == SpeechRecognizer.ERROR_CLIENT) {
                    destroy()
                }

                onError(errorMessage)
            }

            override fun onResults(results: Bundle?) {
                unmuteSystemSounds() // Unmute system streams after receiving results
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onPartialResult(matches[0])
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // No-op
            }
        })

        try {
            muteSystemSounds() // Mute before starting the recognizer
            speechRecognizer?.startListening(intent)
            isCurrentlyListening = true
        } catch (e: Exception) {
            unmuteSystemSounds() // Safety unmute on fail
            Log.e("SpeechToTextManager", "Failed to start listening", e)
            isCurrentlyListening = false
            onError("Failed to start voice listener: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w("SpeechToTextManager", "Error stopping/cancelling recognition", e)
        }
        isCurrentlyListening = false
    }

    fun isListening(): Boolean = isCurrentlyListening

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechToTextManager", "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
    }
}
