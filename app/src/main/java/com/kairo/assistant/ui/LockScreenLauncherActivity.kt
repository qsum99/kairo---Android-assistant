package com.kairo.assistant.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kairo.assistant.MainActivity
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoDarkBg

/**
 * Activity rendered ON TOP OF THE LOCK SCREEN.
 * Displays a round glowing cyan launcher button at the bottom of the lock screen.
 * Tapping it launches Kairo assistant directly over the lock screen.
 */
class LockScreenLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        setContent {
            LockScreenLauncherUI(
                onLaunchKairo = {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                },
                onDismiss = {
                    finish()
                }
            )
        }
    }
}

@Composable
fun LockScreenUI(
    onLaunchKairo: () -> Unit,
    onDismiss: () -> Unit
) {
    LockScreenLauncherUI(onLaunchKairo = onLaunchKairo, onDismiss = onDismiss)
}

@Composable
fun LockScreenLauncherUI(
    onLaunchKairo: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { onDismiss() } // Tap background to dismiss overlay if needed
    ) {
        // Round Glowing Cyan Launch Button at bottom of Lock Screen
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(KairoDarkBg.copy(alpha = 0.95f))
                .border(BorderStroke(2.dp, KairoAccent), CircleShape)
                .clickable { onLaunchKairo() }
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Activate Kairo on Lock Screen",
                tint = KairoAccent,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
