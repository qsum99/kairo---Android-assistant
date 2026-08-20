package com.kairo.assistant.ui.screens

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kairo.assistant.receiver.KairoDeviceAdminReceiver
import com.kairo.assistant.nlu.llm.GeminiClient
import com.kairo.assistant.screen.KairoAccessibilityService
import com.kairo.assistant.service.FloatingBuddyService
import com.kairo.assistant.ui.theme.KairoDarkBg
import com.kairo.assistant.ui.theme.KairoError
import com.kairo.assistant.ui.theme.KairoPrimary
import com.kairo.assistant.ui.theme.KairoSuccess
import com.kairo.assistant.viewmodel.KairoViewModel
import kotlinx.coroutines.launch

// ── Color Tokens for High-Contrast Clean Settings ──
private val CardBg = Color(0xFF0F1523)
private val CardBorder = Color(0xFF1C273C)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF94A3B8)
private val AccentCyan = Color(0xFF00D2FF)
private val AccentGreen = Color(0xFF00E676)
private val AccentAmber = Color(0xFFF59E0B)

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
    val scope = rememberCoroutineScope()

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

    // Dual AI Reasoning State
    var llmBackend by remember { mutableStateOf(prefs.getString("llm_backend", "gemini") ?: "gemini") }
    var geminiApiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var geminiModel by remember { mutableStateOf(prefs.getString("gemini_model", "gemini-3.1-flash-lite") ?: "gemini-3.1-flash-lite") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }

    // Local model status
    val modelFile = remember { java.io.File(context.filesDir, "kairo_model_v7.gguf") }
    var isModelDownloaded by remember { mutableStateOf(modelFile.exists()) }
    LaunchedEffect(uiState.llmStatus) {
        isModelDownloaded = modelFile.exists()
    }

    // Dynamic Permission Statuses
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityEnabled(context)) }
    var isOverlayGranted by remember { mutableStateOf(checkOverlayPermission(context)) }
    var isBatteryOptimizedIgnored by remember { mutableStateOf(checkBatteryOptimizationIgnored(context)) }
    var isDeviceAdminActive by remember { mutableStateOf(checkDeviceAdminActive(context)) }
    var areAppPermissionsGranted by remember { mutableStateOf(checkRuntimePermissionsGranted(context)) }
    var floatingBuddyEnabled by remember {
        mutableStateOf(FloatingBuddyService.isRunning() || prefs.getBoolean("floating_buddy_enabled", false))
    }

    var isDiagnosticsExpanded by remember { mutableStateOf(false) }
    var diagnosticLogs by remember {
        mutableStateOf(
            run {
                val file = java.io.File(context.filesDir, "wakeword_logs.txt")
                if (file.exists()) file.readText() else "No logs recorded yet."
            }
        )
    }

    // Refresh statuses on screen entry
    LaunchedEffect(Unit) {
        isAccessibilityEnabled = checkAccessibilityEnabled(context)
        isOverlayGranted = checkOverlayPermission(context)
        isBatteryOptimizedIgnored = checkBatteryOptimizationIgnored(context)
        isDeviceAdminActive = checkDeviceAdminActive(context)
        areAppPermissionsGranted = checkRuntimePermissionsGranted(context)
        floatingBuddyEnabled = FloatingBuddyService.isRunning() || prefs.getBoolean("floating_buddy_enabled", false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = TextWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            // ══════════════════════════════════════════════════════
            // ── HERO STATUS CARD ──
            // ══════════════════════════════════════════════════════
            val allEssentialGranted = isAccessibilityEnabled && isOverlayGranted && areAppPermissionsGranted
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = if (allEssentialGranted) {
                                listOf(Color(0xFF042F2E), Color(0xFF0F172A))
                            } else {
                                listOf(Color(0xFF332005), Color(0xFF0F172A))
                            }
                        )
                    )
                    .border(
                        1.dp,
                        if (allEssentialGranted) AccentGreen.copy(alpha = 0.4f) else AccentAmber.copy(alpha = 0.4f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (allEssentialGranted) AccentGreen.copy(alpha = 0.15f) else AccentAmber.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = if (allEssentialGranted) Icons.Default.SmartToy else Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (allEssentialGranted) AccentGreen else AccentAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (allEssentialGranted) "AI Agent Ready" else "Setup Required",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (allEssentialGranted) AccentGreen.copy(alpha = 0.2f) else AccentAmber.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (allEssentialGranted) "ACTIVE" else "ACTION NEEDED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                    color = if (allEssentialGranted) AccentGreen else AccentAmber
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (allEssentialGranted) {
                                if (llmBackend == "gemini") "Autonomous agent reasoning with Cloud AI (${geminiModel.replace("gemini-", "")})" else "Autonomous agent running 100% offline with LLaMA 1B"
                            } else {
                                "Grant Accessibility and Overlay permissions to enable autonomous phone control"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ══════════════════════════════════════════════════════
            // ── 1. AI REASONING BACKEND ──
            // ══════════════════════════════════════════════════════
            SectionHeader(title = "AI REASONING ENGINE", icon = Icons.Default.SmartToy)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    // Segmented Switcher Tab: Cloud vs On-Device
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF090D16))
                            .padding(4.dp)
                    ) {
                        // Cloud API Option Tab
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (llmBackend == "gemini") AccentCyan.copy(alpha = 0.18f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (llmBackend == "gemini") AccentCyan.copy(alpha = 0.5f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    llmBackend = "gemini"
                                    prefs.edit().putString("llm_backend", "gemini").apply()
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = if (llmBackend == "gemini") AccentCyan else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cloud API",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (llmBackend == "gemini") AccentCyan else TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // On-Device Option Tab
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (llmBackend == "local") AccentGreen.copy(alpha = 0.18f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (llmBackend == "local") AccentGreen.copy(alpha = 0.5f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    llmBackend = "local"
                                    prefs.edit().putString("llm_backend", "local").apply()
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (llmBackend == "local") AccentGreen else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "100% Offline",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (llmBackend == "local") AccentGreen else TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── CLOUD API VIEW ──
                    if (llmBackend == "gemini") {
                        Text(
                            text = "Ultra-fast frontier intelligence with Google AI models for instant phone automation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // API Key Input
                        OutlinedTextField(
                            value = geminiApiKey,
                            onValueChange = {
                                geminiApiKey = it
                                prefs.edit().putString("gemini_api_key", it).apply()
                                testResultText = null
                            },
                            label = { Text("Google AI Studio API Key", color = TextMuted) },
                            placeholder = { Text("AQ.Ab8RN... or AIzaSy...", color = TextMuted.copy(alpha = 0.5f)) },
                            singleLine = true,
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle API Key visibility",
                                        tint = TextMuted
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = Color(0xFF090D16),
                                unfocusedContainerColor = Color(0xFF090D16)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Model Chips
                        Text(
                            text = "Active Model Preset:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                "gemini-3.1-flash-lite" to "3.1 Lite (Fastest)",
                                "gemma-4-26b-a4b-it" to "Gemma 4B",
                                "gemini-flash-latest" to "Flash Latest",
                                "gemini-3.6-flash" to "3.6 Flash"
                            ).forEach { (modelId, label) ->
                                val isSelected = geminiModel == modelId
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentCyan.copy(alpha = 0.2f) else Color(0xFF090D16))
                                        .border(1.dp, if (isSelected) AccentCyan else CardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            geminiModel = modelId
                                            prefs.edit().putString("gemini_model", modelId).apply()
                                            testResultText = null
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = if (isSelected) AccentCyan else TextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Test Connection Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (geminiApiKey.isBlank()) {
                                        testResultText = "Enter an API key first"
                                        isTestSuccess = false
                                        return@Button
                                    }
                                    isTestingConnection = true
                                    testResultText = null
                                    scope.launch {
                                        val result = GeminiClient.testApiKey(geminiApiKey, geminiModel)
                                        isTestingConnection = false
                                        if (result.isSuccess) {
                                            isTestSuccess = true
                                            testResultText = "✓ Connected to $geminiModel!"
                                        } else {
                                            isTestSuccess = false
                                            testResultText = result.exceptionOrNull()?.message ?: "Connection failed"
                                        }
                                    }
                                },
                                enabled = !isTestingConnection,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (isTestingConnection) "Testing..." else "Test Connection",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            if (testResultText != null) {
                                Text(
                                    text = testResultText ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = if (isTestSuccess) AccentGreen else KairoError,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        // ── 100% OFFLINE VIEW ──
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "LLaMA 3.2 1B Brain",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextWhite
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isModelDownloaded) AccentGreen.copy(alpha = 0.2f) else AccentAmber.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isModelDownloaded) "Ready (554MB)" else "Not Installed",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                            color = if (isModelDownloaded) AccentGreen else AccentAmber
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Executes completely on your phone's processor. Zero data leaves your device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    lineHeight = 16.sp
                                )

                                if (uiState.isLlmDownloading) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { uiState.llmDownloadProgress ?: 0f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = AccentCyan,
                                        trackColor = CardBorder
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            if (uiState.isLlmDownloading) {
                                CircularProgressIndicator(
                                    progress = { uiState.llmDownloadProgress ?: 0f },
                                    modifier = Modifier.size(28.dp),
                                    color = AccentCyan,
                                    strokeWidth = 2.5.dp
                                )
                            } else if (!isModelDownloaded) {
                                Button(
                                    onClick = { viewModel.downloadLlmModel() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ══════════════════════════════════════════════════════
            // ── 2. AGENT & SYSTEM PERMISSIONS ──
            // ══════════════════════════════════════════════════════
            SectionHeader(title = "AGENT PERMISSIONS", icon = Icons.Default.Security)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    // 1. Accessibility Service
                    CleanPermissionItem(
                        icon = Icons.Default.Accessibility,
                        title = "Accessibility Service",
                        subtitle = "Required for reading screen UI trees, tapping buttons, and executing agent actions",
                        isGranted = isAccessibilityEnabled,
                        actionLabel = if (isAccessibilityEnabled) "Settings" else "Enable",
                        onAction = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("SettingsScreen", "Failed to open accessibility settings", e)
                            }
                        }
                    )

                    DividerLine()

                    // 2. Display Over Other Apps
                    CleanPermissionItem(
                        icon = Icons.Default.Layers,
                        title = "Display Over Other Apps",
                        subtitle = "Allows the floating assistant bubble and task cards to remain visible over other apps",
                        isGranted = isOverlayGranted,
                        actionLabel = if (isOverlayGranted) "Active" else "Grant",
                        onAction = {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }
                        }
                    )

                    DividerLine()

                    // 3. Battery Optimization
                    CleanPermissionItem(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Battery Unrestricted",
                        subtitle = "Prevents Android battery saver from interrupting multi-step agent actions in background",
                        isGranted = isBatteryOptimizedIgnored,
                        actionLabel = if (isBatteryOptimizedIgnored) "Unrestricted" else "Configure",
                        onAction = {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    Log.e("SettingsScreen", "Failed to open battery settings", e2)
                                }
                            }
                        }
                    )

                    DividerLine()

                    // 4. App Permissions
                    CleanPermissionItem(
                        icon = Icons.Default.Security,
                        title = "System Permissions",
                        subtitle = "Microphone, Contacts lookup, Phone calling, and SMS messaging",
                        isGranted = areAppPermissionsGranted,
                        actionLabel = if (areAppPermissionsGranted) "Granted" else "Manage",
                        onAction = {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("SettingsScreen", "Failed to open app permissions", e)
                            }
                        }
                    )

                    DividerLine()

                    // 5. Default Assistant App
                    CleanPermissionItem(
                        icon = Icons.Default.Assistant,
                        title = "Default Digital Assistant",
                        subtitle = "Enables long-press power / home button voice activation across your entire phone",
                        isGranted = true,
                        actionLabel = "Configure",
                        onAction = {
                            try {
                                val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    Log.e("SettingsScreen", "Failed to open default apps settings", e2)
                                }
                            }
                        }
                    )

                    DividerLine()

                    // 6. Device Administrator
                    CleanPermissionItem(
                        icon = Icons.Default.Lock,
                        title = "Device Administrator",
                        subtitle = "Allows Kairo to lock the phone screen when you request 'Lock screen'",
                        isGranted = isDeviceAdminActive,
                        actionLabel = if (isDeviceAdminActive) "Active" else "Enable",
                        onAction = {
                            try {
                                val adminComponent = ComponentName(context, KairoDeviceAdminReceiver::class.java)
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allows Kairo to lock the screen when requested.")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("SettingsScreen", "Failed to open device admin settings", e)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ══════════════════════════════════════════════════════
            // ── 3. VOICE & BUDDY PREFERENCES ──
            // ══════════════════════════════════════════════════════
            SectionHeader(title = "PREFERENCES & VOICE", icon = Icons.Default.Mic)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    // Floating AI Buddy Toggle
                    CleanToggleItem(
                        title = "Floating AI Buddy",
                        subtitle = "Show an always-accessible floating bubble on top of other apps",
                        isChecked = floatingBuddyEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
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
                        }
                    )

                    DividerLine()

                    // Voice Feedback (TTS)
                    CleanToggleItem(
                        title = "Voice Feedback",
                        subtitle = "Speak responses aloud using Text-to-Speech",
                        isChecked = voiceFeedbackEnabled,
                        onCheckedChange = { isChecked ->
                            voiceFeedbackEnabled = isChecked
                            prefs.edit().putBoolean("voice_feedback_enabled", isChecked).apply()
                        }
                    )

                    DividerLine()

                    // Mute Microphone
                    CleanToggleItem(
                        title = "Mute Microphone",
                        subtitle = "Disable microphone voice recording input",
                        isChecked = micMuted,
                        onCheckedChange = { isChecked ->
                            micMuted = isChecked
                            prefs.edit().putBoolean("mic_muted", isChecked).apply()
                        }
                    )

                    DividerLine()

                    // Allow on Lock Screen
                    CleanToggleItem(
                        title = "Allow on Lock Screen",
                        subtitle = "Allow assistant to run seamlessly over the phone lock screen",
                        isChecked = allowOnLockScreen,
                        onCheckedChange = { isChecked ->
                            allowOnLockScreen = isChecked
                            prefs.edit().putBoolean("allow_on_lock_screen", isChecked).apply()
                        }
                    )

                    DividerLine()

                    // Default Calling SIM
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default Calling SIM",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextWhite
                            )
                            Text(
                                text = "Preferred SIM card for placing voice calls",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { showSimDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF162032)),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(
                                text = when (defaultSimSetting) {
                                    "sim1" -> "SIM 1"
                                    "sim2" -> "SIM 2"
                                    else -> "Always Ask"
                                },
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (showSimDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showSimDialog = false },
                            title = {
                                Text(
                                    text = "Select Default SIM",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            containerColor = CardBg,
                            textContentColor = TextWhite,
                            confirmButton = {},
                            dismissButton = {
                                Button(
                                    onClick = { showSimDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardBorder)
                                ) {
                                    Text("Cancel", color = TextWhite)
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
                                                .padding(vertical = 10.dp)
                                        ) {
                                            RadioButton(
                                                selected = defaultSimSetting == value,
                                                onClick = {
                                                    defaultSimSetting = value
                                                    prefs.edit().putString("default_calling_sim", value).apply()
                                                    showSimDialog = false
                                                },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = AccentCyan,
                                                    unselectedColor = TextMuted
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextWhite
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ══════════════════════════════════════════════════════
            // ── 4. ABOUT & DIAGNOSTICS ──
            // ══════════════════════════════════════════════════════
            SectionHeader(title = "ABOUT & DIAGNOSTICS", icon = Icons.Default.Info)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kairo Assistant",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Version 1.4.2 — Autonomous Agent Edition",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentCyan.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "v1.4.2",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = AccentCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Collapsible Diagnostics Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF090D16))
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .clickable { isDiagnosticsExpanded = !isDiagnosticsExpanded }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Diagnostic Logs",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextWhite,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isDiagnosticsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }

                    AnimatedVisibility(
                        visible = isDiagnosticsExpanded,
                        enter = fadeIn(tween(150)) + expandVertically(),
                        exit = fadeOut(tween(150)) + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        val file = java.io.File(context.filesDir, "wakeword_logs.txt")
                                        diagnosticLogs = if (file.exists()) file.readText() else "No logs found yet."
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF162032)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text("Refresh", color = TextWhite, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val file = java.io.File(context.filesDir, "wakeword_logs.txt")
                                        if (file.exists()) file.delete()
                                        diagnosticLogs = "Logs cleared."
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF162032)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text("Clear", color = TextMuted, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF060910))
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        text = diagnosticLogs,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

// ── Reusable Section Header ──
@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            ),
            color = AccentCyan
        )
    }
}

// ── Clean Permission Item ──
@Composable
private fun CleanPermissionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isGranted) AccentGreen.copy(alpha = 0.15f) else Color(0xFF162032))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isGranted) AccentGreen else AccentCyan,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = TextMuted,
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isGranted) Color(0xFF162032) else AccentCyan)
                .border(
                    1.dp,
                    if (isGranted) CardBorder else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .clickable { onAction() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isGranted && (actionLabel == "Active" || actionLabel == "Granted" || actionLabel == "Unrestricted")) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = actionLabel,
                    color = if (isGranted) (if (actionLabel == "Active" || actionLabel == "Granted" || actionLabel == "Unrestricted") AccentGreen else TextWhite) else Color.Black,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

// ── Clean Toggle Item ──
@Composable
private fun CleanToggleItem(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color(0xFF162032)
            )
        )
    }
}

// ── Thin Subtle Divider ──
@Composable
private fun DividerLine() {
    HorizontalDivider(
        color = CardBorder,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

// ── Permission Helper Functions ──

private fun checkAccessibilityEnabled(context: Context): Boolean {
    if (KairoAccessibilityService.isRunning) return true
    val expectedServiceName = "${context.packageName}/${KairoAccessibilityService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(expectedServiceName) || enabledServices.contains("KairoAccessibilityService")
}

private fun checkOverlayPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
}

private fun checkBatteryOptimizationIgnored(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    return true
}

private fun checkDeviceAdminActive(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    val adminComponent = ComponentName(context, KairoDeviceAdminReceiver::class.java)
    return dpm?.isAdminActive(adminComponent) == true
}

private fun checkRuntimePermissionsGranted(context: Context): Boolean {
    val permissions = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE
    )
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
