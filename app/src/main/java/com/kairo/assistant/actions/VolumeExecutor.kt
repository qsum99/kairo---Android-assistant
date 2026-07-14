package com.kairo.assistant.actions

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class VolumeExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult(false, "Audio service not available.")

        return try {
            if (command.intent == IntentType.SET_VOLUME) {
                val percentage = command.target?.toIntOrNull() ?: 50
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVolume = (percentage * maxVolume) / 100
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    targetVolume,
                    AudioManager.FLAG_SHOW_UI
                )
                ActionResult(true, "Volume set to $percentage%.")
            } else {
                val direction = when (command.intent) {
                    IntentType.VOLUME_UP -> AudioManager.ADJUST_RAISE
                    IntentType.VOLUME_DOWN -> AudioManager.ADJUST_LOWER
                    else -> return ActionResult(false, "Unsupported volume command.")
                }

                // Adjust stream volume for music stream (global media audio)
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    direction,
                    AudioManager.FLAG_SHOW_UI
                )

                val actionLabel = if (command.intent == IntentType.VOLUME_UP) "up" else "down"
                ActionResult(true, "Turning volume $actionLabel.")
            }
        } catch (e: Exception) {
            Log.e("VolumeExecutor", "Failed to adjust volume", e)
            ActionResult(false, "Failed to adjust volume.")
        }
    }
}
