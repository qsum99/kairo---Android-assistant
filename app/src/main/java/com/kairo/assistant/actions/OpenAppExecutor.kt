package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class OpenAppExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
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
