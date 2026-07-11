package com.kairo.assistant.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Service that instantiates and hosts the VoiceInteractionSession.
 */
class KairoAssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return KairoAssistantSession(this)
    }
}
