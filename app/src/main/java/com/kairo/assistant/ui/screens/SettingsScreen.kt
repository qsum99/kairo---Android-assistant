package com.kairo.assistant.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import com.kairo.assistant.viewmodel.KairoViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import com.kairo.assistant.screen.KairoAccessibilityService
import com.kairo.assistant.service.FloatingBuddyService
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoDarkBg
import com.kairo.assistant.ui.theme.KairoError
import com.kairo.assistant.ui.theme.KairoOnSurface
import com.kairo.assistant.ui.theme.KairoOnSurfaceVariant
import com.kairo.assistant.ui.theme.KairoPrimary
import com.kairo.assistant.ui.theme.KairoSuccess
import com.kairo.assistant.ui.theme.KairoSurface
import com.kairo.assistant.ui.theme.KairoSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLlmFallbackToggled: (Boolean) -> Unit = {},
    viewModel: KairoViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE) }
    
    val isLowRam = remember {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        memoryInfo.totalMem < 4831838208L // 4.5 GB
    }
    
    var llmFallbackEnabled by remember { mutableStateOf(prefs.getBoolean("llm_fallback_enabled", !isLowRam)) }
    var voiceFeedbackEnabled by remember { mutableStateOf(prefs.getBoolean("voice_feedback_enabled", true)) }
    var micMuted by remember { mutableStateOf(prefs.getBoolean("mic_muted", false)) }
    var allowOnLockScreen by remember { mutableStateOf(prefs.getBoolean("allow_on_lock_screen", false)) }
    var showSimDialog by remember { mutableStateOf(false) }
    var defaultSimSetting by remember { mutableStateOf(prefs.getString("default_calling_sim", "always_ask") ?: "always_ask") }

    val modelFile = remember { java.io.File(context.filesDir, "kairo_model_v7.gguf") }
    var isModelDownloaded by remember { mutableStateOf(modelFile.exists()) }
    LaunchedEffect(uiState.llmStatus) {
        isModelDownloaded = modelFile.exists()
    }

    var floatingBuddyEnabled by remember {
        mutableStateOf(FloatingBuddyService.isRunning() || prefs.getBoolean("floating_buddy_enabled", false))
    }
    var isAccessibilityEnabled by remember {
        mutableStateOf(KairoAccessibilityService.isRunning)
    }

    // Refresh status when returning to screen
    LaunchedEffect(Unit) {
        isAccessibilityEnabled = KairoAccessibilityService.isRunning
        floatingBuddyEnabled = FloatingBuddyService.isRunning() || prefs.getBoolean("floating_buddy_enabled", false)
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        color = KairoOnSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = KairoOnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KairoDarkBg
                )
            )
        },
        containerColor = KairoDarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Voice Processing Section ──
            Text(
                text = "VOICE PROCESSING",
                style = MaterialTheme.typography.labelMedium,
                color = KairoOnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KairoSurface)
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // LLM Fallback Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LLM Fallback",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = "Use on-device AI for complex commands",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = llmFallbackEnabled,
                            onCheckedChange = { isChecked ->
                                llmFallbackEnabled = isChecked
                                prefs.edit().putBoolean("llm_fallback_enabled", isChecked).apply()
                                onLlmFallbackToggled(isChecked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = KairoPrimary,
                                checkedTrackColor = KairoPrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = KairoOnSurfaceVariant,
                                uncheckedTrackColor = KairoSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Fallback LLM Model Download Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fallback LLM Model",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = if (uiState.isLlmDownloading) {
                                    "Downloading: ${((uiState.llmDownloadProgress ?: 0f) * 100).toInt()}%"
                                } else if (isModelDownloaded) {
                                    "Model ready (484MB)"
                                } else {
                                    "Required for offline chat & queries (484MB)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                            if (uiState.isLlmDownloading) {
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { uiState.llmDownloadProgress ?: 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = KairoPrimary,
                                    trackColor = KairoSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        if (uiState.isLlmDownloading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                progress = { uiState.llmDownloadProgress ?: 0f },
                                modifier = Modifier.size(24.dp),
                                color = KairoPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else if (isModelDownloaded) {
                            Text(
                                text = "Downloaded",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Button(
                                onClick = { viewModel.downloadLlmModel() },
                                colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Download", color = KairoOnSurface)
                            }
                        }
                    }

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // STT Model info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Speech Recognition",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = "Using Android built-in recognizer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Voice Feedback
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voice Feedback",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = "Speak responses aloud using Text-to-Speech",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = voiceFeedbackEnabled,
                            onCheckedChange = { isChecked ->
                                voiceFeedbackEnabled = isChecked
                                prefs.edit().putBoolean("voice_feedback_enabled", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = KairoPrimary,
                                checkedTrackColor = KairoPrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = KairoOnSurfaceVariant,
                                uncheckedTrackColor = KairoSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Mute Microphone
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mute Microphone",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = "Disable microphone voice input recording",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = micMuted,
                            onCheckedChange = { isChecked ->
                                micMuted = isChecked
                                prefs.edit().putBoolean("mic_muted", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = KairoPrimary,
                                checkedTrackColor = KairoPrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = KairoOnSurfaceVariant,
                                uncheckedTrackColor = KairoSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Allow on Lock Screen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow on Lock Screen",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = "Allow assistant to run seamlessly over the lock screen without asking to unlock",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = allowOnLockScreen,
                            onCheckedChange = { isChecked ->
                                allowOnLockScreen = isChecked
                                prefs.edit().putBoolean("allow_on_lock_screen", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = KairoPrimary,
                                checkedTrackColor = KairoPrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = KairoOnSurfaceVariant,
                                uncheckedTrackColor = KairoSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Default Calling SIM
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default Calling SIM",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = "Preferred SIM for placing voice calls",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { showSimDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = when (defaultSimSetting) {
                                    "sim1" -> "SIM 1"
                                    "sim2" -> "SIM 2"
                                    else -> "Always Ask"
                                },
                                color = KairoOnSurface
                            )
                        }
                    }

                    if (showSimDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showSimDialog = false },
                            title = {
                                Text(
                                    text = "Select Default SIM",
                                    color = KairoOnSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            containerColor = KairoSurface,
                            textContentColor = KairoOnSurface,
                            confirmButton = {},
                            dismissButton = {
                                Button(
                                    onClick = { showSimDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = KairoSurfaceVariant)
                                ) {
                                    Text("Cancel", color = KairoOnSurface)
                                }
                            },
                            text = {
                                Column {
                                    listOf(
                                        Pair("always_ask", "Always Ask"),
                                        Pair("sim1", "SIM 1"),
                                        Pair("sim2", "SIM 2")
                                    ).forEach { (value, label) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    defaultSimSetting = value
                                                    prefs.edit().putString("default_calling_sim", value).apply()
                                                    showSimDialog = false
                                                }
                                                .padding(vertical = 12.dp, horizontal = 8.dp)
                                        ) {
                                            androidx.compose.material3.RadioButton(
                                                selected = defaultSimSetting == value,
                                                onClick = {
                                                    defaultSimSetting = value
                                                    prefs.edit().putString("default_calling_sim", value).apply()
                                                    showSimDialog = false
                                                },
                                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                                    selectedColor = KairoPrimary,
                                                    unselectedColor = KairoOnSurfaceVariant
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = KairoOnSurface
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Default Assistant Config
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default Assistant",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = "Required for always-on background mic access and lock screen support",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("SettingsScreen", "Failed to open voice settings", e)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Configure", color = KairoOnSurface)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── AI Buddy & Autonomous Agent Section ──
            Text(
                text = "AI BUDDY & AUTONOMOUS AGENT",
                style = MaterialTheme.typography.labelMedium,
                color = KairoOnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KairoSurface)
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Floating AI Buddy Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Floating AI Buddy",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = "Always-accessible draggable assistant bubble over other apps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = floatingBuddyEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                        // Request overlay permission
                                        try {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e("SettingsScreen", "Failed to launch overlay settings", e)
                                        }
                                    } else {
                                        floatingBuddyEnabled = true
                                        prefs.edit().putBoolean("floating_buddy_enabled", true).apply()
                                        FloatingBuddyService.start(context)
                                    }
                                } else {
                                    floatingBuddyEnabled = false
                                    prefs.edit().putBoolean("floating_buddy_enabled", false).apply()
                                    FloatingBuddyService.stop(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = KairoPrimary,
                                checkedTrackColor = KairoPrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = KairoOnSurfaceVariant,
                                uncheckedTrackColor = KairoSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Screen Accessibility Service (Agent Brain)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Screen Reader & Agent Control",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KairoOnSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isAccessibilityEnabled) KairoSuccess.copy(alpha = 0.15f) else Color(0xFFFFB300).copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isAccessibilityEnabled) "Active" else "Disabled",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isAccessibilityEnabled) KairoSuccess else Color(0xFFFFB300)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Required for AI to read screen content and automate multi-step tasks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("SettingsScreen", "Failed to open accessibility settings", e)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isAccessibilityEnabled) KairoSurfaceVariant else KairoPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isAccessibilityEnabled) "Settings" else "Enable",
                                color = KairoOnSurface
                            )
                        }
                    }

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Privacy Assurance Note
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(KairoDarkBg)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "🔒 100% On-Device Privacy",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00E5FF)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All screen reading, gesture execution, and AI decisions run strictly on your device using local models. Zero telemetry or screen data is ever sent to any cloud server.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KairoOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── About Section ──
            Text(
                text = "ABOUT",
                style = MaterialTheme.typography.labelMedium,
                color = KairoOnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KairoSurface)
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kairo",
                        style = MaterialTheme.typography.titleLarge,
                        color = KairoOnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KairoOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "An offline, privacy-first voice assistant for Android. " +
                            "All processing happens on your device — no data leaves your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KairoOnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Diagnostics Section ──
            Text(
                text = "DIAGNOSTICS & LOGS",
                style = MaterialTheme.typography.labelMedium,
                color = KairoOnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            var diagnosticLogs by remember {
                mutableStateOf(
                    run {
                        val file = java.io.File(context.filesDir, "wakeword_logs.txt")
                        if (file.exists()) file.readText() else "No logs found yet. Toggle Hey Kairo to generate logs."
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KairoSurface)
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val file = java.io.File(context.filesDir, "wakeword_logs.txt")
                                diagnosticLogs = if (file.exists()) file.readText() else "No logs found yet."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Refresh Logs", color = KairoOnSurface)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val file = java.io.File(context.filesDir, "wakeword_logs.txt")
                                if (file.exists()) {
                                    file.delete()
                                }
                                diagnosticLogs = "Logs cleared successfully."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KairoSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Logs", color = KairoOnSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(KairoDarkBg)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                text = diagnosticLogs,
                                style = MaterialTheme.typography.bodySmall,
                                color = KairoOnSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
