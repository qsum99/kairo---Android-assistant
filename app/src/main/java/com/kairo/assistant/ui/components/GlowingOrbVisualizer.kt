package com.kairo.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoAccentGlow
import com.kairo.assistant.ui.theme.KairoPrimary
import kotlin.math.sin

@Composable
fun GlowingOrbVisualizer(
    isListening: Boolean,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_animations")

    // Slow rotation for idle/listening, fast for processing
    val rotationSpeed = if (isProcessing) 1500 else 6000
    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_1"
    )

    val rotation2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween((rotationSpeed * 1.3f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_2"
    )

    // Breathing pulse size scale
    val pulseDuration = if (isListening || isSpeaking) 1000 else 3000
    val pulseMax = if (isListening || isSpeaking) 1.15f else 1.02f
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = pulseMax,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Glowing blur alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width * 0.32f
            
            // Draw background soft radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        KairoPrimary.copy(alpha = glowAlpha * 0.25f),
                        KairoAccentGlow.copy(alpha = glowAlpha * 0.1f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.8f
                )
            )

            // Draw outer neon rings (with rotation and wobble)
            val ringCount = 3
            for (i in 0 until ringCount) {
                val rot = if (i % 2 == 0) rotation1 else rotation2
                val phaseOffset = i * (Math.PI / 3f)
                
                // Add soft wave wobble to the radius when actively listening/speaking
                val wobble = if (isListening || isSpeaking) {
                    val time = System.currentTimeMillis() / 250.0
                    (sin(time + phaseOffset).toFloat() * 4.dp.toPx())
                } else 0f

                val radiusX = baseRadius + wobble
                val radiusY = baseRadius * 0.85f + wobble

                val strokeWidth = (3.dp + (i * 0.5f).dp).toPx()
                val alpha = if (i == 0) 1.0f else 0.7f - (i * 0.15f)

                // Alternate sweep gradient paths
                val sweepBrush = Brush.sweepGradient(
                    colors = listOf(
                        KairoAccent.copy(alpha = alpha),
                        KairoPrimary.copy(alpha = alpha * 0.5f),
                        KairoAccentGlow.copy(alpha = alpha),
                        KairoAccent.copy(alpha = alpha)
                    ),
                    center = center
                )

                // Rotate oval context around the canvas center pivot
                rotate(degrees = rot + (i * 35f), pivot = center) {
                    drawOval(
                        brush = sweepBrush,
                        topLeft = Offset(center.x - radiusX, center.y - radiusY),
                        size = Size(radiusX * 2f, radiusY * 2f),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
        }
    }
}
