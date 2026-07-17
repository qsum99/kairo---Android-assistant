package com.kairo.assistant.service

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Main VoiceInteractionService representing the assistant to the Android OS.
 * Allows Kairo to be selected as the Default Digital Assistant App in Android settings.
 */
class KairoAssistantService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.d("KairoAssistantService", "VoiceInteractionService ready")
    }
}
