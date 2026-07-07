package com.kairo.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoError
import com.kairo.assistant.ui.theme.KairoOnSurfaceVariant
import com.kairo.assistant.ui.theme.KairoPrimary
import com.kairo.assistant.ui.theme.KairoSuccess
import com.kairo.assistant.ui.theme.KairoPurple

/**
 * Represents the assistant's current operational status.
 */
enum class AssistantStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    DISAMBIGUATING,
    DISAMBIGUATING_SIM,
    ERROR
}

@Composable
fun StatusIndicator(
    status: AssistantStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val statusColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> KairoOnSurfaceVariant
            AssistantStatus.LISTENING -> KairoPrimary
            AssistantStatus.PROCESSING -> KairoAccent
            AssistantStatus.SPEAKING -> KairoSuccess
            AssistantStatus.DISAMBIGUATING -> KairoPurple
            AssistantStatus.DISAMBIGUATING_SIM -> KairoPurple
            AssistantStatus.ERROR -> KairoError
        },
        animationSpec = tween(300),
        label = "status_color"
    )

    val statusText = when (status) {
        AssistantStatus.IDLE -> "Tap the mic to start"
        AssistantStatus.LISTENING -> "Listening…"
        AssistantStatus.PROCESSING -> "Processing…"
        AssistantStatus.SPEAKING -> "Speaking…"
        AssistantStatus.DISAMBIGUATING -> "Multiple matches found"
        AssistantStatus.DISAMBIGUATING_SIM -> "Choose SIM card"
        AssistantStatus.ERROR -> "Something went wrong"
    }

    val shouldPulse = status == AssistantStatus.LISTENING || status == AssistantStatus.PROCESSING || status == AssistantStatus.DISAMBIGUATING || status == AssistantStatus.DISAMBIGUATING_SIM

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(
                color = statusColor.copy(
                    alpha = if (shouldPulse) pulseAlpha else 1f
                ),
                radius = size.width / 2f
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            color = statusColor
        )
    }
}
