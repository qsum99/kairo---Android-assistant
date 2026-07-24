package com.kairo.assistant.receiver

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairo.assistant.ui.LockScreenLauncherActivity

/**
 * BroadcastReceiver that listens for screen activation events (ACTION_SCREEN_ON).
 * When screen turns on while phone is locked and "Allow on Lock Screen" is enabled,
 * it launches LockScreenLauncherActivity to display the round launcher button on the lock screen.
 */
class LockScreenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Intent.ACTION_SCREEN_ON) {
            val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
            val allowOnLockScreen = prefs.getBoolean("allow_on_lock_screen", false)

            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isLocked = km?.isKeyguardLocked == true

            if (allowOnLockScreen && isLocked) {
                try {
                    val launchIntent = Intent(context, LockScreenLauncherActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(launchIntent)
                    Log.d("LockScreenReceiver", "Launched LockScreenLauncherActivity over lock screen")
                } catch (e: Exception) {
                    Log.e("LockScreenReceiver", "Failed to launch LockScreenLauncherActivity", e)
                }
            }
        }
    }
}
