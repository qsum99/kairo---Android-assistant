package com.kairo.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.assistant.agent.AgentProgress
import com.kairo.assistant.agent.AgentStatus
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoDarkBg
import com.kairo.assistant.ui.theme.KairoError
import com.kairo.assistant.ui.theme.KairoGradientEnd
import com.kairo.assistant.ui.theme.KairoGradientStart
import com.kairo.assistant.ui.theme.KairoOnSurface
import com.kairo.assistant.ui.theme.KairoOnSurfaceVariant
import com.kairo.assistant.ui.theme.KairoPrimary
import com.kairo.assistant.ui.theme.KairoSuccess
import com.kairo.assistant.ui.theme.KairoSurface
import com.kairo.assistant.ui.theme.KairoSurfaceVariant

/**
 * High-tech cybernetic progress card for autonomous agent task execution.
 * Shows live step counter, current action description, animated progress bar, and abort control.
 */
@Composable
fun AgentProgressCard(
    progress: AgentProgress,
    onCancelClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = progress.status != AgentStatus.IDLE

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + expandVertically(tween(350)),
        exit = fadeOut(tween(250)) + shrinkVertically(tween(300)),
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "agent_card")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )

        val accentColor by animateColorAsState(
            targetValue = when (progress.status) {
                AgentStatus.RUNNING -> Color(0xFF00E5FF)
                AgentStatus.COMPLETED -> KairoSuccess
                AgentStatus.FAILED -> KairoError
                AgentStatus.CANCELLED -> Color(0xFFFFB300)
                else -> KairoPrimary
            },
            animationSpec = tween(400),
            label = "accent_color"
        )

        val cardBgGradient = Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.12f),
                KairoSurface.copy(alpha = 0.95f),
                KairoDarkBg.copy(alpha = 0.98f)
            )
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (progress.status == AgentStatus.RUNNING) 12.dp else 4.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = accentColor.copy(alpha = 0.3f),
                    spotColor = accentColor.copy(alpha = 0.4f)
                )
                .background(cardBgGradient, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header: Robot badge + Status pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .scale(if (progress.status == AgentStatus.RUNNING) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.2f))
                                .border(1.dp, accentColor.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = when (progress.status) {
                                    AgentStatus.COMPLETED -> Icons.Default.CheckCircle
                                    AgentStatus.FAILED -> Icons.Default.ErrorOutline
                                    AgentStatus.CANCELLED -> Icons.Default.Cancel
                                    else -> Icons.Default.SmartToy
                                },
                                contentDescription = "Agent",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "AUTONOMOUS AGENT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp
                                ),
                                color = accentColor
                            )
                            Text(
                                text = "100% On-Device Automation",
                                style = MaterialTheme.typography.bodySmall,
                                color = KairoOnSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Status Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = when (progress.status) {
                                AgentStatus.RUNNING -> "RUNNING"
                                AgentStatus.COMPLETED -> "COMPLETED"
                                AgentStatus.FAILED -> "FAILED"
                                AgentStatus.CANCELLED -> "CANCELLED"
                                else -> "IDLE"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Task Name
                if (progress.taskDescription.isNotBlank()) {
                    Text(
                        text = progress.taskDescription,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = KairoOnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Current Step Action details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (progress.status == AgentStatus.RUNNING) {
                            "Step ${progress.currentStep} of ${progress.maxSteps}"
                        } else {
                            "Result"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (progress.currentAction.isNotBlank()) {
                        Text(
                            text = progress.currentAction,
                            style = MaterialTheme.typography.bodySmall,
                            color = KairoOnSurface.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress Bar (when running)
                if (progress.status == AgentStatus.RUNNING) {
                    val progressFraction = (progress.currentStep.toFloat() / progress.maxSteps.toFloat()).coerceIn(0.05f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accentColor,
                        trackColor = KairoSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Error message display
                if (progress.error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = KairoError,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (progress.status == AgentStatus.RUNNING) {
                        Button(
                            onClick = onCancelClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, KairoError.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = "Abort",
                                tint = KairoError,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Abort Agent",
                                color = KairoError,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    } else {
                        Button(
                            onClick = onDismissClick,
                            colors = ButtonDefaults.buttonColors(containerColor = KairoSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = "Dismiss",
                                color = KairoOnSurface,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
