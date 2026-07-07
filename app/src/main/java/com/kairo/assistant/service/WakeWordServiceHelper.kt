package com.kairo.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Build

object WakeWordServiceHelper {
    fun start(context: Context) {
        val intent = Intent(context, KairoWakeWordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        val intent = Intent(context, KairoWakeWordService::class.java)
        context.stopService(intent)
    }
}
