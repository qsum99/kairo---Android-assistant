package com.kairo.assistant.service

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Main VoiceInteractionService representing the assistant to the Android OS.
 * Allows Kairo to be selected as the Default Digital Assistant App in Android settings.
 *
 * When the OS activates this service (user selects Kairo as default assistant),
 * [onReady] is called and we auto-start the "Hey Kairo" wake word listener.
 */
class KairoAssistantService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.d("KairoAssistantService", "VoiceInteractionService ready — starting Hey Kairo listener")
        HeyKairoListenerService.startIfEnabled(this)
    }
}

