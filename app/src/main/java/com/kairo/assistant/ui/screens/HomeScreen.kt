package com.kairo.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kairo.assistant.ui.components.AssistantStatus
import com.kairo.assistant.ui.components.MicButton
import com.kairo.assistant.ui.components.StatusIndicator
import com.kairo.assistant.ui.components.TranscriptCard
import com.kairo.assistant.ui.components.WaveformVisualizer
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoDarkBg
import com.kairo.assistant.ui.theme.KairoGradientEnd
import com.kairo.assistant.ui.theme.KairoGradientStart
import com.kairo.assistant.ui.theme.KairoOnSurfaceVariant
import com.kairo.assistant.ui.theme.KairoPrimary
import com.kairo.assistant.ui.theme.KairoSurface
import com.kairo.assistant.ui.theme.KairoSurfaceVariant
import com.kairo.assistant.ui.theme.KairoOnSurface
import com.kairo.assistant.ui.theme.KairoError
import com.kairo.assistant.viewmodel.KairoViewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.width

@Composable
fun HomeScreen(
    viewModel: KairoViewModel,
    onSettingsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val horizontalSpace = (configuration.screenWidthDp * 0.02f).dp
    val verticalSpace = (configuration.screenHeightDp * 0.02f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)) // Dim background scrim
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Click outside assistant card closes it
                (context as? Activity)?.finish()
            }
    ) {
        // Assistant bottom overlay card
        Card(
            shape = RoundedCornerShape(24.dp), // Fully rounded corners since it is floating!
            colors = CardDefaults.cardColors(containerColor = KairoSurface.copy(alpha = 0.6f)), // 40% translucent
            border = BorderStroke(1.dp, KairoSurfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = horizontalSpace, vertical = verticalSpace) // Exact 2% screen size space!
                .navigationBarsPadding()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {} // Catch clicks to prevent dismissing
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Aesthetics handle bar
                Box(
                    modifier = Modifier
                        .size(36.dp, 4.dp)
                        .clip(CircleShape)
                        .background(KairoOnSurfaceVariant.copy(alpha = 0.3f))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Header: Branding and Settings gear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "KAIRO",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                brush = Brush.linearGradient(
                                    colors = listOf(KairoGradientStart, KairoGradientEnd)
                                )
                            )
                        )
                        Text(
                            text = "Offline Assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = KairoOnSurfaceVariant
                        )
                        if (uiState.llmStatus.isNotEmpty() && uiState.llmStatus != "ready") {
                            Text(
                                text = "LLM: ${uiState.llmStatus}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.llmStatus == "loading") KairoAccent else KairoError
                            )
                        }
                    }

                    IconButton(
                        onClick = onSettingsClick,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = KairoSurfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = KairoOnSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Microphone control and status area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MicButton(
                        isListening = uiState.status == AssistantStatus.LISTENING,
                        isProcessing = uiState.status == AssistantStatus.PROCESSING,
                        modifier = Modifier.size(60.dp),
                        onClick = { viewModel.onMicButtonClicked() }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        StatusIndicator(status = uiState.status)
                        Spacer(modifier = Modifier.height(4.dp))
                        TranscriptCard(
                            userText = uiState.transcript,
                            responseText = uiState.response,
                            isVisible = uiState.transcript.isNotEmpty() || uiState.response.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Waveform visualization
                WaveformVisualizer(
                    isActive = uiState.status == AssistantStatus.LISTENING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )

                // Disambiguation Options Card
                if (uiState.disambiguationOptions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(KairoSurfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, KairoAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Which contact did you mean?",
                            style = MaterialTheme.typography.titleMedium,
                            color = KairoOnSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        uiState.disambiguationOptions.forEach { contact ->
                            Button(
                                onClick = { viewModel.onDisambiguationSelected(contact) },
                                colors = ButtonDefaults.buttonColors(containerColor = KairoSurfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = contact.first,
                                        color = KairoOnSurface,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = contact.second,
                                        color = KairoOnSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.onDisambiguationCancelled() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, KairoError.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Cancel", color = KairoError)
                        }
                    }
                }

                // SIM Disambiguation Options Card
                if (uiState.simOptions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(KairoSurfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, KairoAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Which SIM would you like to use?",
                            style = MaterialTheme.typography.titleMedium,
                            color = KairoOnSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        uiState.simOptions.forEach { sim ->
                            Button(
                                onClick = { viewModel.onSimSelected(sim) },
                                colors = ButtonDefaults.buttonColors(containerColor = KairoSurfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sim.first,
                                        color = KairoOnSurface,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.onSimCancelled() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, KairoError.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Cancel", color = KairoError)
                        }
                    }
                }
            }
        }
    }
}
