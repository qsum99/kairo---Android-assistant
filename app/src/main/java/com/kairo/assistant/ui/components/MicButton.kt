package com.kairo.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoAccentGlow
import com.kairo.assistant.ui.theme.KairoDarkBg
import com.kairo.assistant.ui.theme.KairoGradientEnd
import com.kairo.assistant.ui.theme.KairoGradientStart
import com.kairo.assistant.ui.theme.KairoOnSurface
import com.kairo.assistant.ui.theme.KairoPrimary
import com.kairo.assistant.ui.theme.KairoSurface
import com.kairo.assistant.ui.theme.KairoSurfaceVariant

@Composable
fun MicButton(
    isListening: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")

    // Pulse scale for listening state
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Glow alpha for listening state
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Rotation for processing state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val currentScale = when {
        isListening -> pulseScale
        else -> 1f
    }

    val buttonColor by animateColorAsState(
        targetValue = when {
            isListening -> KairoPrimary
            isProcessing -> KairoAccent
            else -> KairoSurfaceVariant
        },
        animationSpec = tween(300),
        label = "button_color"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isListening || isProcessing -> KairoOnSurface
            else -> KairoOnSurface.copy(alpha = 0.7f)
        },
        animationSpec = tween(300),
        label = "icon_color"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Outer glow rings when listening
        if (isListening) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize(1f)
                    .scale(currentScale)
            ) {
                // Outer glow ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            KairoPrimary.copy(alpha = glowAlpha * 0.4f),
                            KairoAccentGlow.copy(alpha = glowAlpha * 0.2f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width / 2f
                    )
                )

                // Inner ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            KairoGradientStart.copy(alpha = glowAlpha),
                            KairoGradientEnd.copy(alpha = glowAlpha),
                            KairoGradientStart.copy(alpha = glowAlpha)
                        )
                    ),
                    radius = (size.width / 2f) * 0.85f,
                    style = Stroke(width = size.width * 0.02f)
                )
            }
        }

        // Processing ring
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(0.85f),
                color = KairoAccent,
                strokeWidth = 3.dp,
                trackColor = KairoSurfaceVariant
            )
        }

        // Main button circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize(0.75f)
                .scale(if (isListening) currentScale else 1f)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            buttonColor,
                            buttonColor.copy(alpha = 0.8f)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Start listening",
                tint = iconColor,
                modifier = Modifier.fillMaxSize(0.4f)
            )
        }
    }
}
