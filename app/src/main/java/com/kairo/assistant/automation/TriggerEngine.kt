package com.kairo.assistant.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.kairo.assistant.data.local.AutomationRecipeEntity
import com.kairo.assistant.data.local.KairoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

private const val TAG = "TriggerEngine"

/**
 * Lightweight service that monitors automation triggers and fires actions.
 *
 * Started from KairoApplication, it collects enabled recipes from Room
 * and dispatches them when triggers fire.
 *
 * Supported triggers:
 *   - time: AlarmManager exact/inexact alarms
 *   - wifi_connect / wifi_disconnect: ConnectivityManager.NetworkCallback
 *   - battery_low / battery_high: registered BroadcastReceivers
 */
object TriggerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Start the trigger engine. Call from KairoApplication.onCreate().
     * Safe to call multiple times — it's a no-op if already running.
     */
    fun start(context: Context) {
        if (isRunning) return
        isRunning = true
        Log.i(TAG, "Starting TriggerEngine")

        val db = KairoDatabase.getInstance(context)

        // Collect enabled recipes and re-arm triggers whenever the list changes
        scope.launch {
            db.automationRecipeDao().getEnabledRecipes().collectLatest { recipes ->
                Log.d(TAG, "Recipe list changed: ${recipes.size} enabled")
                armAllTriggers(context, recipes)
            }
        }

        // Register battery triggers
        registerBatteryReceiver(context)
    }

    // ── Trigger arming ──────────────────────────────────────────────────

    private fun armAllTriggers(context: Context, recipes: List<AutomationRecipeEntity>) {
        // Remove old WiFi callback
        removeWifiCallback(context)

        for (recipe in recipes) {
            when (recipe.triggerType) {
                "time" -> armTimeTrigger(context, recipe)
                "wifi_connect", "wifi_disconnect" -> { /* handled below in batch */ }
                "battery_low", "battery_high" -> { /* handled by battery receiver */ }
            }
        }

        // Arm WiFi triggers
        val wifiRecipes = recipes.filter { it.triggerType == "wifi_connect" || it.triggerType == "wifi_disconnect" }
        if (wifiRecipes.isNotEmpty()) {
            armWifiTriggers(context, wifiRecipes)
        }
    }

    private fun armTimeTrigger(context: Context, recipe: AutomationRecipeEntity) {
        try {
            val config = JSONObject(recipe.triggerConfig)
            val hour = config.optInt("hour", 0)
            val minute = config.optInt("minute", 0)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TriggerReceiver::class.java).apply {
                action = "com.kairo.TRIGGER_FIRED"
                putExtra("recipe_id", recipe.id)
                putExtra("trigger_type", recipe.triggerType)
                putExtra("action_type", recipe.actionType)
                putExtra("action_config", recipe.actionConfig)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                recipe.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                // If the time has already passed today, schedule for tomorrow
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // Use inexact alarm by default (no permission needed)
            // Exact alarms require SCHEDULE_EXACT_ALARM + runtime check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "Armed exact alarm for recipe #${recipe.id}: $hour:${minute.toString().padStart(2, '0')}")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "Armed inexact alarm for recipe #${recipe.id}: $hour:${minute.toString().padStart(2, '0')}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to arm time trigger for recipe #${recipe.id}", e)
        }
    }

    private fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    // ── WiFi triggers ───────────────────────────────────────────────────

    private fun armWifiTriggers(context: Context, recipes: List<AutomationRecipeEntity>) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "WiFi network available")
                // Check if any wifi_connect recipes should fire
                scope.launch {
                    for (recipe in recipes) {
                        if (recipe.triggerType == "wifi_connect") {
                            val config = JSONObject(recipe.triggerConfig)
                            val targetSsid = config.optString("ssid", "*")
                            if (targetSsid == "*" || targetSsid.isBlank()) {
                                // "Any WiFi" trigger — fire immediately
                                fireAction(context, recipe)
                            } else {
                                // Specific SSID — check if connected to it
                                // Note: getting SSID requires ACCESS_FINE_LOCATION on Android 10+
                                val currentSsid = getCurrentSsid(context)
                                if (currentSsid != null && currentSsid.equals(targetSsid, ignoreCase = true)) {
                                    fireAction(context, recipe)
                                }
                            }
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "WiFi network lost")
                scope.launch {
                    for (recipe in recipes) {
                        if (recipe.triggerType == "wifi_disconnect") {
                            fireAction(context, recipe)
                        }
                    }
                }
            }
        }

        connectivityManager.registerNetworkCallback(request, callback)
        wifiCallback = callback
        Log.d(TAG, "Registered WiFi callback for ${recipes.size} recipes")
    }

    private fun removeWifiCallback(context: Context) {
        wifiCallback?.let {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister WiFi callback", e)
            }
        }
        wifiCallback = null
    }

    @Suppress("DEPRECATION")
    private fun getCurrentSsid(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid?.removeSurrounding("\"")
            if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") null else ssid
        } catch (e: Exception) {
            null
        }
    }

    // ── Battery triggers ────────────────────────────────────────────────

    private fun registerBatteryReceiver(context: Context) {
        // BatteryReceiver is registered in AndroidManifest.xml
        // It calls TriggerEngine.onBatteryChanged()
        Log.d(TAG, "Battery receiver registered via manifest")
    }

    /**
     * Called by TriggerReceiver when battery level changes.
     */
    fun onBatteryChanged(context: Context, level: Int, isCharging: Boolean) {
        scope.launch {
            val db = KairoDatabase.getInstance(context)
            val recipes = db.automationRecipeDao().getEnabledRecipes().let { flow ->
                var list: List<AutomationRecipeEntity> = emptyList()
                flow.collect { list = it }
                list
            }

            for (recipe in recipes) {
                val config = JSONObject(recipe.triggerConfig)
                val threshold = config.optInt("level", 20)

                when {
                    recipe.triggerType == "battery_low" && level <= threshold && !isCharging -> {
                        fireAction(context, recipe)
                    }
                    recipe.triggerType == "battery_high" && level >= threshold && isCharging -> {
                        fireAction(context, recipe)
                    }
                }
            }
        }
    }

    // ── Action execution ────────────────────────────────────────────────

    private suspend fun fireAction(context: Context, recipe: AutomationRecipeEntity) {
        Log.i(TAG, "Firing recipe #${recipe.id}: ${recipe.name}")

        val db = KairoDatabase.getInstance(context)
        db.automationRecipeDao().markTriggered(recipe.id)

        try {
            val config = JSONObject(recipe.actionConfig)
            when (recipe.actionType) {
                "toggle_setting" -> {
                    val setting = config.optString("setting", "")
                    val state = config.optString("state", "on")
                    val turnOn = state == "on"
                    when (setting) {
                        "bluetooth" -> toggleBluetooth(context, turnOn)
                        "wifi" -> toggleWifi(context, turnOn)
                        "airplane" -> toggleAirplane(context, turnOn)
                        "hotspot" -> toggleHotspot(context, turnOn)
                    }
                }
                "open_app" -> {
                    val pkg = config.optString("package", "")
                    if (pkg.isNotBlank()) {
                        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }
                }
                "send_sms" -> {
                    val to = config.optString("to", "")
                    val message = config.optString("message", "")
                    // Use ActionDispatcher for SMS
                    val dispatchIntent = Intent("com.kairo.TRIGGER_SMS").apply {
                        putExtra("to", to)
                        putExtra("message", message)
                    }
                    context.sendBroadcast(dispatchIntent)
                }
                "toggle_media" -> {
                    val state = config.optString("state", "play")
                    val keyCode = if (state == "play") {
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                    } else {
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
                    }
                    val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    audioManager.dispatchMediaKeyEvent(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fire action for recipe #${recipe.id}", e)
        }
    }

    // ── System setting toggles ──────────────────────────────────────────

    private fun toggleBluetooth(context: Context, enable: Boolean) {
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (enable && !adapter.isEnabled) adapter.enable()
            else if (!enable && adapter.isEnabled) adapter.disable()
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth toggle failed", e)
        }
    }

    private fun toggleWifi(context: Context, enable: Boolean) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            if (enable && !wifiManager.isWifiEnabled) wifiManager.isWifiEnabled = true
            else if (!enable && wifiManager.isWifiEnabled) wifiManager.isWifiEnabled = false
        } catch (e: Exception) {
            Log.e(TAG, "WiFi toggle failed", e)
        }
    }

    private fun toggleAirplane(context: Context, enable: Boolean) {
        try {
            android.provider.Settings.Global.putInt(
                context.contentResolver,
                android.provider.Settings.Global.AIRPLANE_MODE_ON,
                if (enable) 1 else 0
            )
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                putExtra("state", enable)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Airplane mode toggle failed", e)
        }
    }

    private fun toggleHotspot(context: Context, enable: Boolean) {
        try {
            val manager = context.getSystemService(Context.WIFI_SERVICE)
            val method = manager?.javaClass?.getMethod("setWifiApEnabled", android.net.wifi.WifiConfiguration::class.java, Boolean::class.java)
            method?.invoke(manager, null, enable)
        } catch (e: Exception) {
            Log.e(TAG, "Hotspot toggle failed", e)
        }
    }
}

// ── Receivers ──────────────────────────────────────────────────────────

/**
 * Receives alarm intents from TriggerEngine time-based triggers.
 */
class TriggerReceiver : BroadcastReceiver() {
    private val TAG = "TriggerReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.kairo.TRIGGER_FIRED") return

        val recipeId = intent.getLongExtra("recipe_id", -1)
        val actionType = intent.getStringExtra("action_type") ?: return
        val actionConfig = intent.getStringExtra("action_config") ?: return

        Log.i(TAG, "Alarm fired for recipe #$recipeId")

        CoroutineScope(Dispatchers.IO).launch {
            TriggerEngine.start(context) // ensure engine is running
            // Re-arm for tomorrow by re-reading the Flow
            val db = KairoDatabase.getInstance(context)
            db.automationRecipeDao().getAllRecipes().let { flow ->
                flow.collect { recipes ->
                    val recipe = recipes.find { it.id == recipeId }
                    if (recipe != null) {
                        // The Flow collection in TriggerEngine will re-arm this
                    }
                }
            }
        }
    }
}

/**
 * Receives battery change broadcasts and forwards to TriggerEngine.
 */
class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
            TriggerEngine.onBatteryChanged(context, level, isCharging)
        }
    }
}

/**
 * Re-arms time-based automation triggers after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed, re-arming time triggers")
            TriggerEngine.start(context)
        }
    }
}
