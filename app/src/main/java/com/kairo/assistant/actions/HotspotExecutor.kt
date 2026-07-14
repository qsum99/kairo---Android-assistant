package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class HotspotExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val action = command.target ?: "toggle"
        val enabled = (action == "on")

        // 1. Try legacy/reflection direct toggle first
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val setWifiApEnabledMethod = wifiManager.javaClass.getMethod(
                    "setWifiApEnabled",
                    android.net.wifi.WifiConfiguration::class.java,
                    Boolean::class.javaPrimitiveType
                )
                setWifiApEnabledMethod.invoke(wifiManager, null, enabled)
                return ActionResult(true, "Turning hotspot $action.")
            }
        } catch (e: Exception) {
            Log.d("HotspotExecutor", "Direct AP toggle failed: ${e.message}. Falling back to Tether settings.")
        }

        // 2. Fallback: launch tethering settings directly
        return try {
            val intent = Intent().apply {
                setAction("android.settings.TETHER_SETTINGS")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "Opening hotspot settings to turn hotspot $action.")
        } catch (e: Exception) {
            Log.e("HotspotExecutor", "Error opening hotspot settings", e)
            ActionResult(false, "Failed to open hotspot settings: ${e.message}")
        }
    }
}
