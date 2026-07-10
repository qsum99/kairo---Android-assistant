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
import androidx.compose.material.icons.filled.VolumeUp
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
import com.kairo.assistant.ui.theme.KairoPrimaryVariant
import com.kairo.assistant.ui.theme.KairoSuccess
import com.kairo.assistant.ui.theme.KairoPurple
import com.kairo.assistant.ui.theme.KairoSurface
import com.kairo.assistant.ui.theme.KairoSurfaceVariant
import com.kairo.assistant.ui.theme.KairoOnSurface
import com.kairo.assistant.ui.theme.KairoError
import com.kairo.assistant.viewmodel.KairoViewModel
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.heightIn
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

    // Dynamic, state-aware glowing card outline border animation
    val borderGlowColor by animateColorAsState(
        targetValue = when (uiState.status) {
            AssistantStatus.LISTENING -> KairoPrimary
            AssistantStatus.PROCESSING -> KairoAccent
            AssistantStatus.SPEAKING -> KairoSuccess
            AssistantStatus.DISAMBIGUATING, AssistantStatus.DISAMBIGUATING_SIM -> KairoPurple
            AssistantStatus.ERROR -> KairoError
            else -> KairoSurfaceVariant.copy(alpha = 0.6f)
        },
        animationSpec = tween(400),
        label = "border_glow_color"
    )

    val borderGlowWidth by animateDpAsState(
        targetValue = when (uiState.status) {
            AssistantStatus.IDLE -> 1.dp
            else -> 1.5.dp
        },
        animationSpec = tween(400),
        label = "border_glow_width"
    )

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
            shape = RoundedCornerShape(28.dp), // Premium smooth corners
            colors = CardDefaults.cardColors(containerColor = KairoSurface.copy(alpha = 0.85f)), // Glassmorphism backdrop opacity
            border = BorderStroke(borderGlowWidth, borderGlowColor), // Animated state border!
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = horizontalSpace, vertical = verticalSpace) // Exact 2% space
                .navigationBarsPadding()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {} // Catch clicks
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp) // Removed verticalScroll from card parent!
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
                                color = if (uiState.llmStatus == "loading" || uiState.isLlmDownloading) KairoAccent else KairoError
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

                // Download models banner
                if (uiState.llmStatus.isNotEmpty() && uiState.llmStatus != "ready") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KairoSurfaceVariant.copy(alpha = 0.4f))
                            .border(BorderStroke(1.dp, KairoSurfaceVariant), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "On-Device LLM Fallback",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = KairoOnSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (uiState.isLlmDownloading) {
                                        "Downloading: ${(uiState.llmDownloadProgress?.times(100))?.toInt() ?: 0}%"
                                    } else {
                                        "Required for offline chat & queries"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KairoOnSurfaceVariant
                                )
                            }
                            
                            if (uiState.isLlmDownloading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    progress = uiState.llmDownloadProgress ?: 0f,
                                    modifier = Modifier.size(24.dp),
                                    color = KairoPrimary,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Button(
                                    onClick = { viewModel.downloadLlmModel() },
                                    colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Download (484MB)", color = KairoOnSurface, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        
                        if (uiState.isLlmDownloading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = uiState.llmDownloadProgress ?: 0f,
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = KairoPrimary,
                                trackColor = KairoSurfaceVariant
                            )
                        }
                    }
                }

                // Conversational Chat Bubble list (Independently scrollable)
                if (uiState.transcript.isNotEmpty() || uiState.response.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp) // Responsive vertical height limit
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // User transcript bubble (aligned right)
                        if (uiState.transcript.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .clip(RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp))
                                        .background(KairoSurfaceVariant.copy(alpha = 0.8f))
                                        .border(BorderStroke(1.dp, KairoSurfaceVariant.copy(alpha = 0.9f)), RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "You",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = KairoPrimaryVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = uiState.transcript,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = KairoOnSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Kairo response bubble (aligned left)
                        if (uiState.response.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp))
                                        .background(KairoSurface.copy(alpha = 0.5f))
                                        .border(BorderStroke(1.dp, borderGlowColor.copy(alpha = 0.4f)), RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp))
                                        .clickable { viewModel.stopSpeaking() } // Tapping response bubble stops speaking
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Kairo",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = KairoAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (uiState.status == AssistantStatus.SPEAKING) {
                                                Icon(
                                                    imageVector = Icons.Default.VolumeUp,
                                                    contentDescription = "Mute Speaking",
                                                    tint = KairoAccent.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = uiState.response,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = KairoOnSurface.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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

                Spacer(modifier = Modifier.height(20.dp))

                // Centered Status Indicator
                StatusIndicator(
                    status = uiState.status,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Symmetrical Waveform visualization
                WaveformVisualizer(
                    isActive = uiState.status == AssistantStatus.LISTENING || uiState.status == AssistantStatus.SPEAKING,
                    isProcessing = uiState.status == AssistantStatus.PROCESSING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Centered Large Mic Orb
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    MicButton(
                        isListening = uiState.status == AssistantStatus.LISTENING,
                        isProcessing = uiState.status == AssistantStatus.PROCESSING,
                        modifier = Modifier.size(70.dp),
                        onClick = { viewModel.onMicButtonClicked() }
                    )
                }
            }
        }
    }
}
