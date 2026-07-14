package com.kairo.assistant.actions

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.util.Log
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class MediaExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult(false, "Audio service not available.")

        return try {
            val keycode = when (command.intent) {
                IntentType.MEDIA_PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
                IntentType.MEDIA_PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
                else -> return ActionResult(false, "Unsupported media command.")
            }

            // Dispatch key down event
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keycode)
            audioManager.dispatchMediaKeyEvent(eventDown)

            // Dispatch key up event
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keycode)
            audioManager.dispatchMediaKeyEvent(eventUp)

            val actionLabel = if (command.intent == IntentType.MEDIA_PLAY) "Playing" else "Pausing"
            ActionResult(true, "$actionLabel music.")
        } catch (e: Exception) {
            Log.e("MediaExecutor", "Failed to dispatch media key event", e)
            ActionResult(false, "Failed to control music playback.")
        }
    }
}
