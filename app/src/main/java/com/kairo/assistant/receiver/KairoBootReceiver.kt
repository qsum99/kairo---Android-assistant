package com.kairo.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kairo.assistant.service.KairoWakeWordService

class KairoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("KairoBootReceiver", "Received broadcast: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
            val wakeWordEnabled = prefs.getBoolean("wake_word_enabled", false)
            
            if (wakeWordEnabled) {
                val serviceIntent = Intent(context, KairoWakeWordService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d("KairoBootReceiver", "Started KairoWakeWordService successfully on boot/update")
                } catch (e: Exception) {
                    Log.e("KairoBootReceiver", "Failed to start KairoWakeWordService on boot/update", e)
                }
            } else {
                Log.d("KairoBootReceiver", "Wake word is disabled in preferences. Skipping service start.")
            }
        }
    }
}
