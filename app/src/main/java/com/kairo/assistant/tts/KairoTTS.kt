package com.kairo.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class KairoTTS(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false
    private var onUtteranceDoneListener: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("KairoTTS", "Language not supported")
                } else {
                    // Try to query and select one of Google's premium offline neural voices (en-us-x- or en-gb-x-)
                    try {
                        val availableVoices = tts?.voices
                        if (!availableVoices.isNullOrEmpty()) {
                            val preferredVoice = availableVoices.find { voice ->
                                (voice.name.contains("en-us-x-") || voice.name.contains("en-gb-x-")) &&
                                voice.name.contains("local")
                            } ?: availableVoices.find { voice ->
                                voice.locale.language == Locale.ENGLISH.language && !voice.isNetworkConnectionRequired
                            }
                            
                            preferredVoice?.let {
                                tts?.voice = it
                                Log.d("KairoTTS", "Selected premium neural voice: ${it.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("KairoTTS", "Failed to select premium voice profile", e)
                    }

                    tts?.setPitch(1.0f) // Keep standard pitch to prevent distortion
                    tts?.setSpeechRate(1.0f) // Keep standard speed
                    isReady = true
                    Log.d("KairoTTS", "TTS initialized successfully with system defaults and neural voice check")
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        Log.d("KairoTTS", "Speech done for: $utteranceId")
                        onUtteranceDoneListener?.invoke()
                        onUtteranceDoneListener = null
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e("KairoTTS", "Speech error")
                        onUtteranceDoneListener?.invoke()
                        onUtteranceDoneListener = null
                    }
                })
            } else {
                Log.e("KairoTTS", "TTS initialization failed with status: $status")
            }
        }
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
        val voiceFeedbackEnabled = prefs.getBoolean("voice_feedback_enabled", true)
        if (!voiceFeedbackEnabled) {
            Log.d("KairoTTS", "Voice feedback is muted. Invoking callback asynchronously on Main thread.")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onDone()
            }
            return
        }

        if (isReady) {
            onUtteranceDoneListener = onDone
            val utteranceId = UUID.randomUUID().toString()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            Log.d("KairoTTS", "TTS not ready yet. Retrying in background...")
            val startTime = System.currentTimeMillis()
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            
            val checkRunnable = object : Runnable {
                override fun run() {
                    if (isReady) {
                        Log.d("KairoTTS", "TTS became ready. Speaking response.")
                        onUtteranceDoneListener = onDone
                        val utteranceId = UUID.randomUUID().toString()
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                    } else if (System.currentTimeMillis() - startTime < 3000) {
                        handler.postDelayed(this, 150)
                    } else {
                        Log.w("KairoTTS", "TTS failed to become ready after 3 seconds. Skipping speech.")
                        onDone()
                    }
                }
            }
            handler.post(checkRunnable)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
