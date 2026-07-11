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
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import com.kairo.assistant.ui.components.GlowingOrbVisualizer
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import kotlin.math.sin

@Composable
fun HomeScreen(
    viewModel: KairoViewModel,
    onSettingsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(context) {
        viewModel.updateActiveContext(context)
    }
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

    var suggestionIndex by remember { mutableStateOf(0) }
    val suggestions = remember {
        listOf(
            "I can search new contacts",
            "I can compose SMS text",
            "I can trigger offline LLM chat",
            "I can open calendar and settings"
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            suggestionIndex = (suggestionIndex + 1) % suggestions.size
        }
    }

    val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    val subtitle = if (uiState.transcript.isNotEmpty()) uiState.transcript else suggestions[suggestionIndex]
    var isKeyboardMode by remember { mutableStateOf(false) }
    var keyboardText by remember { mutableStateOf("") }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

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
        androidx.compose.animation.AnimatedVisibility(
            visible = isVisible,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 400, easing = androidx.compose.animation.core.EaseOutCubic)
            ) + androidx.compose.animation.fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ) + androidx.compose.animation.fadeOut(animationSpec = tween(durationMillis = 200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 480.dp) // Set maximum width limit
                .fillMaxWidth(0.92f) // Floating style (92% width)
                .padding(vertical = verticalSpace)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Assistant bottom overlay card
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(0.dp, Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {} // Catch clicks
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
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

                    // Header: Branding + Quick controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Microphone quick mute/unmute button
                        val prefs = remember { context.getSharedPreferences("kairo_prefs", android.content.Context.MODE_PRIVATE) }
                        var micMuted by remember {
                            mutableStateOf(prefs.getBoolean("mic_muted", false))
                        }
                        
                        IconButton(
                            onClick = {
                                val nextVal = !micMuted
                                micMuted = nextVal
                                prefs.edit().putBoolean("mic_muted", nextVal).apply()
                                // If muting mic while Kairo is actively recording, stop listening immediately
                                if (nextVal && uiState.status == AssistantStatus.LISTENING) {
                                    viewModel.stopListening()
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (micMuted) "Unmute Microphone" else "Mute Microphone",
                                tint = if (micMuted) KairoError.copy(alpha = 0.8f) else KairoAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Pulsing neon state indicator dot
                            val dotColor by animateColorAsState(
                                targetValue = when (uiState.status) {
                                    AssistantStatus.LISTENING -> Color(0xFF00E676)
                                    AssistantStatus.PROCESSING -> Color(0xFF00D2FF)
                                    AssistantStatus.SPEAKING -> Color(0xFF00F5D4)
                                    AssistantStatus.ERROR -> Color(0xFFEF4444)
                                    else -> KairoOnSurfaceVariant.copy(alpha = 0.5f)
                                },
                                animationSpec = tween(500),
                                label = "header_dot"
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = "KAIRO",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    brush = Brush.linearGradient(
                                        colors = listOf(KairoGradientStart, KairoGradientEnd)
                                    )
                                ),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Right: Voice Feedback quick mute/unmute button
                        var voiceFeedbackEnabled by remember {
                            mutableStateOf(prefs.getBoolean("voice_feedback_enabled", true))
                        }
                        
                        IconButton(
                            onClick = {
                                val nextVal = !voiceFeedbackEnabled
                                voiceFeedbackEnabled = nextVal
                                prefs.edit().putBoolean("voice_feedback_enabled", nextVal).apply()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (voiceFeedbackEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (voiceFeedbackEnabled) "Mute Voice" else "Unmute Voice",
                                tint = if (voiceFeedbackEnabled) KairoAccent else KairoError.copy(alpha = 0.8f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.response.isEmpty() && uiState.transcript.isEmpty()) {
                        // Cyber capsule layout for prompt suggestion
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(KairoSurfaceVariant.copy(alpha = 0.4f))
                                .border(BorderStroke(1.dp, KairoPrimary.copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "> ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KairoAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KairoPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "_",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KairoAccent.copy(alpha = cursorAlpha),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Gradient Header / Response Title
                        Text(
                            text = "What Can I Do for\nYou Today?",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.White, KairoPrimary, KairoAccent)
                                )
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    } else {
                        // Symmetrical thin spacing header when in conversation mode
                        Text(
                            text = "Conversation",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = KairoPrimary
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Only show the large center orb and keyboard fallback when NO active transcript/response is displayed
                    if (isKeyboardMode) {
                        // Keyboard input field (Always show when user wants to type!)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TextField(
                                value = keyboardText,
                                onValueChange = { keyboardText = it },
                                placeholder = { Text("Type query...", color = KairoOnSurfaceVariant) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(BorderStroke(1.dp, KairoPrimary.copy(alpha = 0.3f)), RoundedCornerShape(16.dp)),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = KairoSurfaceVariant,
                                    unfocusedContainerColor = KairoSurfaceVariant.copy(alpha = 0.5f),
                                    focusedTextColor = KairoOnSurface,
                                    unfocusedTextColor = KairoOnSurface.copy(alpha = 0.8f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (keyboardText.isNotBlank()) {
                                                viewModel.submitQuery(keyboardText)
                                                keyboardText = ""
                                                isKeyboardMode = false
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Send",
                                            tint = KairoPrimary
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (keyboardText.isNotBlank()) {
                                            viewModel.submitQuery(keyboardText)
                                            keyboardText = ""
                                            isKeyboardMode = false
                                        }
                                    }
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { 
                                    isKeyboardMode = false 
                                    viewModel.startListeningAutomatic() // Reactivate recording on cancel
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.dp, KairoOnSurfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(36.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 2.dp)
                            ) {
                                Text("Cancel", color = KairoOnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        // Regular mode: Orb visualizer when idle, or conversation bubbles when active
                        if (uiState.response.isEmpty() && uiState.transcript.isEmpty()) {
                            // Glowing center orb visualizer centerpiece
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        viewModel.onMicButtonClicked()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                GlowingOrbVisualizer(
                                    isListening = uiState.status == AssistantStatus.LISTENING,
                                    isProcessing = uiState.status == AssistantStatus.PROCESSING,
                                    isSpeaking = uiState.status == AssistantStatus.SPEAKING,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        } else {
                            // Active conversation mode - show scrollable chat bubbles Column
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
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
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            KairoPrimary.copy(alpha = 0.15f),
                                                            KairoSurfaceVariant.copy(alpha = 0.6f)
                                                        )
                                                    )
                                                )
                                                .border(BorderStroke(1.dp, KairoPrimary.copy(alpha = 0.3f)), RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp))
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
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            KairoSurfaceVariant.copy(alpha = 0.7f),
                                                            KairoSurface.copy(alpha = 0.4f)
                                                        )
                                                    )
                                                )
                                                .border(BorderStroke(1.dp, borderGlowColor.copy(alpha = 0.5f)), RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp))
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Centered Status Indicator
                    StatusIndicator(
                        status = uiState.status,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Symmetrical Waveform visualization
                    WaveformVisualizer(
                        isActive = uiState.status == AssistantStatus.LISTENING || uiState.status == AssistantStatus.SPEAKING,
                        isProcessing = uiState.status == AssistantStatus.PROCESSING,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sleek Bottom Navigation Bar (Qubora-inspired styling)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Keyboard launch trigger
                        IconButton(
                            onClick = { 
                                isKeyboardMode = true 
                                viewModel.stopListening() // Release mic when keyboard is active
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = KairoSurfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Keyboard Input",
                                tint = KairoOnSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Center active mic trigger
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(if (uiState.status == AssistantStatus.LISTENING) KairoAccent.copy(alpha = 0.2f) else KairoSurfaceVariant.copy(alpha = 0.4f))
                                .clickable { viewModel.onMicButtonClicked() }
                        ) {
                            GlowingOrbVisualizer(
                                isListening = uiState.status == AssistantStatus.LISTENING,
                                isProcessing = uiState.status == AssistantStatus.PROCESSING,
                                isSpeaking = uiState.status == AssistantStatus.SPEAKING,
                                modifier = Modifier.size(70.dp)
                            )
                        }

                        // Right Settings slider gear
                        IconButton(
                            onClick = onSettingsClick,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = KairoSurfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = KairoOnSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
