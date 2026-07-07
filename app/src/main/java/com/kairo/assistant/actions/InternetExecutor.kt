package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class InternetExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val action = command.target ?: "toggle"
        return try {
            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Open the floating Internet connectivity panel on Android 10+
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                // Fallback for older devices to Wireless/Cellular settings
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            val actionLabel = when (action) {
                "on" -> "on"
                "off" -> "off"
                else -> "and toggling"
            }
            ActionResult(true, "Opening connectivity settings to turn mobile data $actionLabel.")
        } catch (e: Exception) {
            Log.e("InternetExecutor", "Error opening internet settings", e)
            ActionResult(false, "Failed to open internet settings: ${e.message}")
        }
    }
}
