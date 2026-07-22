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

    init {
        prewarm()
    }

    fun prewarm() {
        if (speechRecognizer == null) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            } catch (e: Exception) {
                Log.w("SpeechToTextManager", "Error pre-warming SpeechRecognizer", e)
            }
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Ensure recognizer instance exists
        if (speechRecognizer == null) {
            prewarm()
        } else if (isCurrentlyListening) {
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
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechToTextManager", "Speech started")
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
            }

            override fun onError(error: Int) {
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
            speechRecognizer?.startListening(intent)
            isCurrentlyListening = true
        } catch (e: Exception) {
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
