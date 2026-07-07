package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class SettingsExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "Opening settings.")
        } catch (e: Exception) {
            Log.e("SettingsExecutor", "Error opening settings", e)
            ActionResult(false, "Failed to open settings: ${e.message}")
        }
    }
}
