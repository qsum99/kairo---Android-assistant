package com.kairo.assistant.ui.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.assistant.agent.KairoAgent

/**
 * Individual agent bubble composable with state-driven glow, scale, and opacity animations.
 *
 * @param agent The agent to render
 * @param isActive Whether this agent is currently working
 * @param isDimmed Whether this agent should be dimmed (another agent is active)
 * @param size Base size of the bubble
 * @param onTap Callback when this bubble is tapped
 */
@Composable
fun AgentBubble(
    agent: KairoAgent,
    isActive: Boolean = false,
    isDimmed: Boolean = false,
    size: Dp = 40.dp,
    onTap: () -> Unit = {}
) {
    val agentColor = Color(agent.colorHex)

    // Pulse animation when active
    val infiniteTransition = rememberInfiniteTransition(label = "agent_${agent.name}")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isActive) 0.4f else 0.6f,
        targetValue = if (isActive) 1.0f else 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isActive) 600 else 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_${agent.name}"
    )

    // Scale animation: 40dp -> 52dp when active
    val targetScale = when {
        isActive -> size.value * 1.3f / size.value  // 52/40 = 1.3
        isDimmed -> 0.85f
        else -> 1.0f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale_${agent.name}"
    )

    // Opacity: dim to 30% when another agent is active
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isDimmed) 0.3f else 1.0f,
        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
        label = "alpha_${agent.name}"
    )

    val bgGradient = Brush.radialGradient(
        colors = listOf(
            agentColor.copy(alpha = 0.85f),
            agentColor.copy(alpha = 0.5f),
            Color(0xFF1A1A2E)
        )
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(animatedScale)
            .alpha(animatedAlpha)
            .shadow(
                elevation = if (isActive) 16.dp else 8.dp,
                shape = CircleShape,
                ambientColor = agentColor.copy(alpha = 0.3f),
                spotColor = agentColor.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(bgGradient)
            .border(
                width = if (isActive) 2.5.dp else 1.5.dp,
                color = agentColor.copy(alpha = glowAlpha),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures { onTap() }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = agent.emoji,
            fontSize = if (isActive) 20.sp else 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The main Kairo coordinator bubble (larger, center of radial).
 */
@Composable
fun CoordinatorBubble(
    activeAgent: KairoAgent?,
    isListening: Boolean = false,
    isProcessing: Boolean = false,
    size: Dp = 56.dp,
    onTap: () -> Unit = {}
) {
    val kairoColor = Color(KairoAgent.COORDINATOR.colorHex)

    val infiniteTransition = rememberInfiniteTransition(label = "coordinator")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when {
            isListening -> 1.15f
            isProcessing -> 1.1f
            else -> 1.06f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isListening -> 600
                    isProcessing -> 400
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coordinator_pulse"
    )

    val glowColor = when {
        isListening -> Color(0xFF4CAF50)   // Green listening
        isProcessing -> Color(0xFFFF9800)  // Orange processing
        activeAgent != null -> Color(activeAgent.colorHex)
        else -> kairoColor
    }

    val bgGradient = Brush.radialGradient(
        colors = listOf(
            glowColor.copy(alpha = 0.9f),
            glowColor.copy(alpha = 0.5f),
            Color(0xFF1A1A2E)
        )
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(pulseScale)
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = glowColor.copy(alpha = 0.4f),
                spotColor = glowColor.copy(alpha = 0.4f)
            )
            .clip(CircleShape)
            .background(bgGradient)
            .border(
                width = 2.dp,
                color = glowColor.copy(alpha = 0.7f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures { onTap() }
            },
        contentAlignment = Alignment.Center
    ) {
        // Show active agent's emoji badge, or wolf by default
        Text(
            text = activeAgent?.emoji ?: KairoAgent.COORDINATOR.emoji,
            fontSize = 26.sp,
            textAlign = TextAlign.Center
        )
    }
}