package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class AlarmExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val timeStr = command.target
        if (timeStr.isNullOrBlank()) {
            return ActionResult(false, "No time specified for the alarm.")
        }

        return try {
            val parts = timeStr.split(":")
            if (parts.size != 2) {
                return ActionResult(false, "Invalid time format. Expected HH:mm.")
            }

            val hour = parts[0].trim().toInt()
            val minute = parts[1].trim().toInt()

            if (hour !in 0..23 || minute !in 0..59) {
                return ActionResult(false, "Invalid time values. Hour must be 0-23, minute 0-59.")
            }

            val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                context.startActivity(alarmIntent)
            } catch (e: SecurityException) {
                Log.w("AlarmExecutor", "Silent alarm set blocked by security. Falling back to Clock UI...", e)
                val uiIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(uiIntent)
            }
            
            ActionResult(true, "Setting alarm for ${String.format("%02d:%02d", hour, minute)}")
        } catch (e: NumberFormatException) {
            Log.e("AlarmExecutor", "Error parsing alarm time", e)
            ActionResult(false, "Invalid time format: ${e.message}")
        } catch (e: Exception) {
            Log.e("AlarmExecutor", "Error setting alarm", e)
            ActionResult(false, "Failed to set alarm: ${e.message}")
        }
    }
}
