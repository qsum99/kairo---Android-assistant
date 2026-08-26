package com.kairo.assistant.ui.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.assistant.agent.AgentCharters
import com.kairo.assistant.agent.KairoAgent
import com.kairo.assistant.service.BuddyState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Radial menu that shows the coordinator bubble and 4 satellite agent bubbles.
 *
 * Collapsed: only the coordinator wolf bubble is visible.
 * Expanded: 4 agent bubbles animate outward in a semi-circle arc.
 */
@Composable
fun AgentRadialMenu(
    isExpanded: Boolean,
    buddyState: BuddyState,
    activeAgent: KairoAgent?,
    lastQuery: String,
    lastResponse: String,
    onCoordinatorTap: () -> Unit,
    onAgentTap: (KairoAgent) -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { 80.dp.toPx() }

    // Expansion animation (0f = collapsed, 1f = expanded)
    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expand"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Radial area: coordinator + satellites
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // Satellite agent bubbles (only visible when expanding/expanded)
            if (expandProgress > 0.01f) {
                val satellites = KairoAgent.satellites
                // Arc from 220deg to 320deg (top-left to bottom-right semi-circle)
                val startAngle = 220.0
                val endAngle = 320.0
                val step = (endAngle - startAngle) / (satellites.size - 1).coerceAtLeast(1)

                satellites.forEachIndexed { index, agent ->
                    val angleDeg = startAngle + (step * index)
                    val angleRad = angleDeg * PI / 180.0
                    val offsetX = (cos(angleRad) * radiusPx * expandProgress).roundToInt()
                    val offsetY = (sin(angleRad) * radiusPx * expandProgress).roundToInt()

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX, offsetY) }
                            .alpha(expandProgress)
                    ) {
                        AgentBubble(
                            agent = agent,
                            isActive = activeAgent == agent,
                            isDimmed = activeAgent != null && activeAgent != agent,
                            size = 40.dp,
                            onTap = { onAgentTap(agent) }
                        )
                    }
                }
            }

            // Center coordinator bubble (always visible)
            CoordinatorBubble(
                activeAgent = activeAgent,
                isListening = buddyState == BuddyState.LISTENING,
                isProcessing = buddyState == BuddyState.PROCESSING,
                size = 56.dp,
                onTap = onCoordinatorTap
            )
        }

        // Active agent status message
        if (activeAgent != null && buddyState == BuddyState.PROCESSING) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = AgentCharters.getStartMessage(activeAgent),
                color = Color(activeAgent.colorHex),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Conversation card (when response is ready)
        if (isExpanded && (lastQuery.isNotBlank() || lastResponse.isNotBlank())) {
            Spacer(modifier = Modifier.height(8.dp))
            AgentConversationCard(
                agent = activeAgent ?: KairoAgent.COORDINATOR,
                query = lastQuery,
                response = lastResponse,
                state = buddyState,
                onClose = onClose
            )
        }
    }
}

/**
 * Conversation card with agent emoji + color header.
 */
@Composable
private fun AgentConversationCard(
    agent: KairoAgent,
    query: String,
    response: String,
    state: BuddyState,
    onClose: () -> Unit
) {
    val agentColor = Color(agent.colorHex)

    Box(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D1117))
            .border(1.dp, agentColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            // Agent header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AgentCharters.getHeaderLabel(
                        agent,
                        isWorking = state == BuddyState.PROCESSING
                    ),
                    color = agentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = agent.charter,
                    color = agentColor.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // User query
            if (query.isNotBlank()) {
                Text(
                    text = query,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Agent response
            if (response.isNotBlank()) {
                Text(
                    text = response,
                    color = Color(0xFFF1F5F9),
                    fontSize = 12.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
            }
        }
    }
}