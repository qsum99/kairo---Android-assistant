package com.kairo.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kairo.assistant.service.HeyKairoListenerService
import com.kairo.assistant.ui.KairoApp
import com.kairo.assistant.ui.theme.KairoTheme
import com.kairo.assistant.viewmodel.KairoViewModel


/**
 * Main Activity — the single entry point for the Kairo voice assistant.
 * Uses Jetpack Compose for the entire UI.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: KairoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.updateActiveContext(this)
        enableEdgeToEdge()
        configureLockScreenFlags()



        // Always start listening on launch (manual or assistant trigger)
        viewModel.startListeningAutomatic()

        setContent {
            KairoTheme {
                KairoApp(onExit = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureLockScreenFlags()
        viewModel.startListeningAutomatic()
    }



    override fun onResume() {
        super.onResume()
        configureLockScreenFlags()
        // Stop wake word listener to avoid microphone conflict while the assistant is active
        HeyKairoListenerService.stop(this)
        Log.d("MainActivity", "Paused Hey Kairo listener (mic in use)")
    }

    override fun onStop() {
        super.onStop()
        // Restart wake word listener when the activity goes to background
        HeyKairoListenerService.startIfEnabled(this)
        Log.d("MainActivity", "Resumed Hey Kairo listener (activity backgrounded)")
    }



    private fun configureLockScreenFlags() {
        val prefs = getSharedPreferences("kairo_prefs", MODE_PRIVATE)
        val allowOnLockScreen = prefs.getBoolean("allow_on_lock_screen", false)
        if (allowOnLockScreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(false)
                setTurnScreenOn(false)
            } else {
                @Suppress("DEPRECATION")
                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
    }
}
