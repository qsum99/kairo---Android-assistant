package com.kairo.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

/**
 * Handles digital assistant sessions. When triggered (swipe up or power button hold),
 * it starts MainActivity directly with clear flags.
 */
class KairoAssistantSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreate() {
        super.onCreate()
        Log.d("KairoAssistantSession", "Voice interaction session created")
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d("KairoAssistantSession", "Voice interaction session shown — launching MainActivity")

        try {
            val intent = Intent(context, com.kairo.assistant.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("assistant_trigger", true)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("KairoAssistantSession", "Failed to launch MainActivity from voice session", e)
        } finally {
            // Dismiss the system session overlay instantly so MainActivity draws on top
            finish()
        }
    }
}
