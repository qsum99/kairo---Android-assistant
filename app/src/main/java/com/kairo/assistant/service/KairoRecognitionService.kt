package com.kairo.assistant.service

import android.content.Intent
import android.speech.RecognitionService

/**
 * Standard RecognitionService placeholder required by Android's Voice Interaction framework.
 * Satisfies the requirement that a voice assistant must declare a recognition service.
 */
class KairoRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        // Recognition is handled dynamically using SpeechToTextManager
    }

    override fun onCancel(listener: Callback) {
        // Recognition cancellation logic
    }

    override fun onStopListening(listener: Callback) {
        // Recognition stop logic
    }
}
