package com.kairo.assistant.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.assistant.intelligence.drafting.MessageDrafter
import com.kairo.assistant.nlu.llm.GeminiClient
import com.kairo.assistant.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DraftScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedPlatform by remember { mutableStateOf(MessageDrafter.Platform.WHATSAPP) }
    var selectedTone by remember { mutableStateOf(MessageDrafter.Tone.PROFESSIONAL) }
    var userIntent by remember { mutableStateOf("") }
    var generatedDraft by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
    val apiKey = prefs.getString("gemini_api_key", "") ?: ""
    val model = prefs.getString("gemini_model", GeminiClient.DEFAULT_MODEL) ?: GeminiClient.DEFAULT_MODEL

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KairoDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = KairoOnSurface
                    )
                }
                Text(
                    text = "Message Drafter",
                    style = MaterialTheme.typography.headlineSmall,
                    color = KairoOnSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = KairoAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Platform Selector
            Text(
                text = "Platform",
                color = KairoOnSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MessageDrafter.Platform.entries.forEach { platform ->
                    PlatformChip(
                        platform = platform,
                        isSelected = selectedPlatform == platform,
                        onClick = { selectedPlatform = platform },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tone Selector
            Text(
                text = "Tone",
                color = KairoOnSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MessageDrafter.Tone.entries.forEach { tone ->
                    ToneChip(
                        tone = tone,
                        isSelected = selectedTone == tone,
                        onClick = { selectedTone = tone },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // User Intent Input
            Text(
                text = "What do you want to say?",
                color = KairoOnSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KairoSurfaceVariant)
                    .border(1.dp, KairoAccent.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                if (userIntent.isEmpty()) {
                    Text(
                        text = "e.g., Tell my boss I'll be late due to traffic...",
                        color = KairoOnSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = userIntent,
                    onValueChange = { userIntent = it },
                    textStyle = TextStyle(
                        color = KairoOnSurface,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(KairoAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Generate Button
            Button(
                onClick = {
                    if (userIntent.isBlank()) {
                        errorMessage = "Please describe what you want to say"
                        return@Button
                    }
                    if (apiKey.isBlank()) {
                        errorMessage = "Please set your Gemini API key in Settings"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val draft = withContext(Dispatchers.IO) {
                                MessageDrafter.draft(
                                    platform = selectedPlatform,
                                    tone = selectedTone,
                                    userIntent = userIntent,
                                    apiKey = apiKey,
                                    model = model
                                )
                            }
                            generatedDraft = draft
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to generate draft"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading && userIntent.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KairoAccent,
                    disabledContainerColor = KairoSurfaceVariant
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = KairoDarkBg,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Generating...", color = KairoDarkBg, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = KairoDarkBg)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Draft", color = KairoDarkBg, fontWeight = FontWeight.Bold)
                }
            }

            // Error Message
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = KairoError,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // Generated Draft Output
            AnimatedVisibility(
                visible = generatedDraft.isNotBlank(),
                enter = fadeIn() + expandVertically()
            ) {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Generated Draft",
                            color = KairoAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Copy button
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Draft", generatedDraft))
                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, "Copy", tint = KairoOnSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                            // Share button
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, generatedDraft)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Share, "Share", tint = KairoOnSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        KairoSurface,
                                        KairoSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            )
                            .border(1.dp, KairoAccent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = generatedDraft,
                            color = KairoOnSurface,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlatformChip(
    platform: MessageDrafter.Platform,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (platform) {
        MessageDrafter.Platform.WHATSAPP -> Icons.Default.Chat
        MessageDrafter.Platform.EMAIL -> Icons.Default.Email
        MessageDrafter.Platform.SMS -> Icons.Default.Sms
        MessageDrafter.Platform.LINKEDIN -> Icons.Default.Work
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) KairoAccent.copy(alpha = 0.15f) else KairoSurfaceVariant)
            .border(
                1.dp,
                if (isSelected) KairoAccent else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = platform.displayName,
            tint = if (isSelected) KairoAccent else KairoOnSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = platform.displayName,
            fontSize = 10.sp,
            color = if (isSelected) KairoAccent else KairoOnSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun ToneChip(
    tone: MessageDrafter.Tone,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) KairoPurple.copy(alpha = 0.2f) else KairoSurfaceVariant)
            .border(
                1.dp,
                if (isSelected) KairoPurple else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 2.dp)
    ) {
        Text(
            text = tone.displayName,
            fontSize = 10.sp,
            color = if (isSelected) KairoPurple else KairoOnSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}