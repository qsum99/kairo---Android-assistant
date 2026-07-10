package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class OpenAppExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val targetApp = command.target?.trim()?.lowercase() ?: ""
        if (targetApp == "alarm" || targetApp == "alarm clock" || targetApp == "clock") {
            try {
                val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return ActionResult(true, "Opening Alarm Clock")
            } catch (e: Exception) {
                Log.e("OpenAppExecutor", "Error launching ACTION_SHOW_ALARMS, falling back to package lookup", e)
            }
        }

        val packageName = command.extra
        if (packageName.isNullOrBlank()) {
            return ActionResult(false, "No app specified to open.")
        }

        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                return ActionResult(false, "App not found: $packageName")
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ActionResult(true, "Opening ${command.target ?: packageName}")
        } catch (e: Exception) {
            Log.e("OpenAppExecutor", "Error opening app", e)
            ActionResult(false, "Failed to open app: ${e.message}")
        }
    }
}
