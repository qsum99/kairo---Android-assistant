package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class BluetoothExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val action = command.target ?: "toggle"
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val actionLabel = when (action) {
                "on" -> "on"
                "off" -> "off"
                else -> "and toggling"
            }
            ActionResult(true, "Opening Bluetooth settings to turn Bluetooth $actionLabel.")
        } catch (e: Exception) {
            Log.e("BluetoothExecutor", "Error opening Bluetooth settings", e)
            ActionResult(false, "Failed to open Bluetooth settings: ${e.message}")
        }
    }
}
