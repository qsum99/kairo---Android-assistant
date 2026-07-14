package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class WifiExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val action = command.target ?: "toggle"
        val enabled = (action == "on")

        // 1. Try to toggle programmatically first (supported on < Android 10 or privileged apps)
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val targetState = if (action == "toggle") !wifiManager.isWifiEnabled else enabled
                @Suppress("DEPRECATION")
                val success = wifiManager.setWifiEnabled(targetState)
                if (success) {
                    val finalState = if (targetState) "on" else "off"
                    return ActionResult(true, "Wi-Fi is now turned $finalState.")
                }
            }
        } catch (e: Exception) {
            Log.d("WifiExecutor", "Direct Wi-Fi toggle failed: ${e.message}. Falling back to Settings Panel.")
        }

        // 2. Fallback to Settings Panel (Android 10+) or full Wi-Fi settings page
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val actionLabel = when (action) {
                "on" -> "on"
                "off" -> "off"
                else -> "and toggling"
            }
            ActionResult(true, "Opening Wi-Fi settings to turn Wi-Fi $actionLabel.")
        } catch (e: Exception) {
            Log.e("WifiExecutor", "Error opening Wi-Fi settings", e)
            ActionResult(false, "Failed to open Wi-Fi settings: ${e.message}")
        }
    }
}
