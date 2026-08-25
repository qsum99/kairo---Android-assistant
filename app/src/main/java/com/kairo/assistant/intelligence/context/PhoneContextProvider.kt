package com.kairo.assistant.intelligence.context

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "PhoneContextProvider"

/**
 * Gathers device-wide intelligence for context-aware AI responses.
 *
 * Provides battery level, connectivity status, time context,
 * and device info to enrich LLM prompts.
 */
object PhoneContextProvider {

    /**
     * Collect a full device context snapshot.
     */
    fun getContext(context: Context): PhoneContext {
        return PhoneContext(
            batteryLevel = getBatteryLevel(context),
            isCharging = isCharging(context),
            wifiSsid = getWifiSsid(context),
            isConnectedToInternet = isConnectedToInternet(context),
            connectionType = getConnectionType(context),
            currentTime = getCurrentTime(),
            timeOfDay = getTimeOfDay(),
            deviceName = getDeviceName(),
            androidVersion = Build.VERSION.RELEASE
        )
    }

    /**
     * Format the context as a human-readable string for LLM prompts.
     */
    fun getContextString(context: Context): String {
        val ctx = getContext(context)
        return buildString {
            appendLine("DEVICE CONTEXT:")
            appendLine("- Time: ${ctx.currentTime} (${ctx.timeOfDay})")
            appendLine("- Battery: ${ctx.batteryLevel}%${if (ctx.isCharging) " (charging)" else ""}")
            appendLine("- Internet: ${if (ctx.isConnectedToInternet) "Connected (${ctx.connectionType})" else "Disconnected"}")
            if (ctx.wifiSsid != null) {
                appendLine("- WiFi: ${ctx.wifiSsid}")
            }
            appendLine("- Device: ${ctx.deviceName} (Android ${ctx.androidVersion})")
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get battery level", e)
            -1
        }
    }

    private fun isCharging(context: Context): Boolean {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifiSsid(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid?.removeSurrounding("\"")
            if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") null else ssid
        } catch (e: Exception) {
            null
        }
    }

    private fun isConnectedToInternet(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun getConnectionType(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return "None"
            val capabilities = cm.getNetworkCapabilities(network) ?: return "None"
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("hh:mm a, EEE dd MMM yyyy", Locale.getDefault()).format(Date())
    }

    private fun getTimeOfDay(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
    }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }
}

/**
 * Snapshot of the phone's current state.
 */
data class PhoneContext(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val wifiSsid: String?,
    val isConnectedToInternet: Boolean,
    val connectionType: String,
    val currentTime: String,
    val timeOfDay: String,
    val deviceName: String,
    val androidVersion: String
)