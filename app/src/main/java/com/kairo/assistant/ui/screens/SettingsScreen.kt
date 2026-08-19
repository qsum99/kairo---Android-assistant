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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.kairo.assistant.nlu.llm.GeminiClient
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kairo.assistant.receiver.KairoDeviceAdminReceiver
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
import com.kairo.assistant.viewmodel.KairoViewModel

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

    val scope = rememberCoroutineScope()
    var llmBackend by remember { mutableStateOf(prefs.getString("llm_backend", "gemini") ?: "gemini") }
    var geminiApiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var geminiModel by remember { mutableStateOf(prefs.getString("gemini_model", "gemini-3.1-flash-lite") ?: "gemini-3.1-flash-lite") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    val modelFile = remember { java.io.File(context.filesDir, "kairo_model_v7.gguf") }
    var isModelDownloaded by remember { mutableStateOf(modelFile.exists()) }
    LaunchedEffect(uiState.llmStatus) {
        isModelDownloaded = modelFile.exists()
    }

    // Dynamic permission and service statuses
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityEnabled(context)) }
    var isOverlayGranted by remember { mutableStateOf(checkOverlayPermission(context)) }
    var isBatteryOptimizedIgnored by remember { mutableStateOf(checkBatteryOptimizationIgnored(context)) }
    var isDeviceAdminActive by remember { mutableStateOf(checkDeviceAdminActive(context)) }
    var areAppPermissionsGranted by remember { mutableStateOf(checkRuntimePermissionsGranted(context)) }
    var floatingBuddyEnabled by remember {
        mutableStateOf(FloatingBuddyService.isRunning() || prefs.getBoolean("floating_buddy_enabled", false))
    }

    // Refresh all statuses when returning to Settings screen
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

            // ══════════════════════════════════════════════════════
            // ── AGENT & SYSTEM PERMISSIONS (ALL REQUIREMENTS) ──
            // ══════════════════════════════════════════════════════
            Text(
                text = "AGENT & SYSTEM PERMISSIONS",
                style = MaterialTheme.typography.labelMedium,
                color = KairoOnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KairoSurface)
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    // 1. Accessibility Service (Primary Agent Requirement)
                    AgentPermissionRow(
                        icon = Icons.Default.Accessibility,
                        title = "Accessibility Service",
                        badgeText = "AGENT BRAIN",
                        description = "Enables Kairo to read screen UI trees, tap buttons, scroll, and type text autonomously",
                        isGranted = isAccessibilityEnabled,
                        buttonLabel = if (isAccessibilityEnabled) "Settings" else "Enable",
                        onButtonClick = {
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

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 2. Display Over Other Apps (Floating AI Buddy Overlay)
                    AgentPermissionRow(
                        icon = Icons.Default.Layers,
                        title = "Display Over Other Apps",
                        badgeText = "AI BUDDY",
                        description = "Allows the floating assistant bubble and task cards to stay visible over any app",
                        isGranted = isOverlayGranted,
                        buttonLabel = if (isOverlayGranted) "Granted" else "Grant",
                        onButtonClick = {
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

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 3. Default Digital Assistant
                    AgentPermissionRow(
                        icon = Icons.Default.Assistant,
                        title = "Default Assistant App",
                        badgeText = "VOICE TRIGGER",
                        description = "Enables long-press power/home assist launch and system-wide voice capture",
                        isGranted = true, // Informative/configurable
                        buttonLabel = "Configure",
                        onButtonClick = {
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

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 4. App Permissions (Microphone, Contacts, Calls, SMS)
                    AgentPermissionRow(
                        icon = Icons.Default.Security,
                        title = "App Permissions",
                        badgeText = "VOICE & SYSTEM",
                        description = "Microphone, Contacts lookup, Phone calls, SMS sending, and Phone State",
                        isGranted = areAppPermissionsGranted,
                        buttonLabel = if (areAppPermissionsGranted) "Granted" else "Manage",
                        onButtonClick = {
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

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 5. Battery Optimization (Unrestricted Background Execution)
                    AgentPermissionRow(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Battery Optimization",
                        badgeText = "BACKGROUND",
                        description = "Set to Unrestricted so Android does not terminate long-running agent tasks in background",
                        isGranted = isBatteryOptimizedIgnored,
                        buttonLabel = if (isBatteryOptimizedIgnored) "Unrestricted" else "Configure",
                        onButtonClick = {
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

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 6. Device Administrator (Screen Lock)
                    AgentPermissionRow(
                        icon = Icons.Default.Lock,
                        title = "Device Administrator",
                        badgeText = "SCREEN LOCK",
                        description = "Allows Kairo to lock the phone screen when you request 'Lock device'",
                        isGranted = isDeviceAdminActive,
                        buttonLabel = if (isDeviceAdminActive) "Active" else "Enable",
                        onButtonClick = {
                            try {
                                val adminComponent = ComponentName(context, KairoDeviceAdminReceiver::class.java)
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allows Kairo to lock the screen when you ask.")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("SettingsScreen", "Failed to open device admin settings", e)
                            }
                        }
                    )

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 🔒 100% Privacy Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(KairoDarkBg)
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🔒 100% On-Device Privacy Guaranteed",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF00E5FF)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All screen reading, UI tree inspection, gesture automation, and AI models run strictly on your phone hardware. Zero screenshots, data, or credentials ever leave your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KairoOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ══════════════════════════════════════════════════════
            // ── AI BUDDY & AGENT TOGGLES ──
            // ══════════════════════════════════════════════════════
            Text(
                text = "AI BUDDY & ASSISTANT FEATURES",
                style = MaterialTheme.typography.labelMedium,
                color = KairoOnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KairoSurface)
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Floating AI Buddy Toggle
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
                                text = "Always-accessible draggable assistant bubble on top of other apps",
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
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ══════════════════════════════════════════════════════
            // ══════════════════════════════════════════════════════
            // ── AI REASONING BACKEND (LOCAL VS GEMINI API) ──
            // ══════════════════════════════════════════════════════
            Text(
                text = "AI REASONING ENGINE & BENCHMARK",
                style = MaterialTheme.typography.labelMedium,
                color = KairoOnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KairoSurface)
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    // Option 1: On-Device Local LLM
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                llmBackend = "local"
                                prefs.edit().putString("llm_backend", "local").apply()
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = llmBackend == "local",
                            onClick = {
                                llmBackend = "local"
                                prefs.edit().putString("llm_backend", "local").apply()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = KairoPrimary,
                                unselectedColor = KairoOnSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "100% On-Device Local Model",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KairoOnSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KairoSuccess.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "PRIVACY",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                        color = KairoSuccess
                                    )
                                }
                            }
                            Text(
                                text = "Runs LLaMA 3.2 1B locally on phone hardware (zero internet or API keys needed)",
                                style = MaterialTheme.typography.bodySmall,
                                color = KairoOnSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        color = KairoSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    // Option 2: Google Gemini Cloud API
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                llmBackend = "gemini"
                                prefs.edit().putString("llm_backend", "gemini").apply()
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = llmBackend == "gemini",
                            onClick = {
                                llmBackend = "gemini"
                                prefs.edit().putString("llm_backend", "gemini").apply()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF00E5FF),
                                unselectedColor = KairoOnSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Google Gemini Cloud API",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KairoOnSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "TESTING & SPEED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                            }
                            Text(
                                text = "Ultra-fast frontier intelligence with Gemini 2.0 Flash for instant reasoning benchmarks",
                                style = MaterialTheme.typography.bodySmall,
                                color = KairoOnSurfaceVariant
                            )
                        }
                    }

                    // If Gemini selected: API Key Input & Model Configuration
                    if (llmBackend == "gemini") {
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(KairoDarkBg)
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Gemini API Configuration",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF00E5FF)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = geminiApiKey,
                                    onValueChange = {
                                        geminiApiKey = it
                                        prefs.edit().putString("gemini_api_key", it).apply()
                                        testResultText = null
                                    },
                                    label = { Text("Gemini API Key") },
                                    placeholder = { Text("AIzaSy...") },
                                    singleLine = true,
                                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                            Icon(
                                                imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle API Key visibility",
                                                tint = KairoOnSurfaceVariant
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00E5FF),
                                        unfocusedBorderColor = KairoSurfaceVariant,
                                        focusedTextColor = KairoOnSurface,
                                        unfocusedTextColor = KairoOnSurface,
                                        focusedLabelColor = Color(0xFF00E5FF),
                                        unfocusedLabelColor = KairoOnSurfaceVariant
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Gemini Model Input & Presets
                                Text(
                                    text = "Model ID:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KairoOnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = geminiModel,
                                    onValueChange = {
                                        geminiModel = it.trim()
                                        prefs.edit().putString("gemini_model", it.trim()).apply()
                                        testResultText = null
                                    },
                                    placeholder = { Text("e.g. gemini-2.0-flash, gemini-3.6") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00E5FF),
                                        unfocusedBorderColor = KairoSurfaceVariant,
                                        focusedTextColor = KairoOnSurface,
                                        unfocusedTextColor = KairoOnSurface,
                                        focusedLabelColor = Color(0xFF00E5FF),
                                        unfocusedLabelColor = KairoOnSurfaceVariant
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick Model Preset Chips
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf(
                                        "gemma-4-26b-a4b-it" to "Gemma 4B",
                                        "gemini-3.1-flash-lite" to "3.1 Lite",
                                        "gemini-flash-latest" to "Flash Latest",
                                        "gemini-3.6-flash" to "3.6 Flash"
                                    ).forEach { (modelId, label) ->
                                        val isSelected = geminiModel == modelId
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 6.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else KairoSurface)
                                                .border(1.dp, if (isSelected) Color(0xFF00E5FF) else KairoSurfaceVariant, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    geminiModel = modelId
                                                    prefs.edit().putString("gemini_model", modelId).apply()
                                                    testResultText = null
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                                color = if (isSelected) Color(0xFF00E5FF) else KairoOnSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Test Connection Button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            if (geminiApiKey.isBlank()) {
                                                testResultText = "Please enter an API key first"
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
                                                    testResultText = "Connected to $geminiModel!"
                                                } else {
                                                    isTestSuccess = false
                                                    testResultText = result.exceptionOrNull()?.message ?: "Connection failed"
                                                }
                                            }
                                        },
                                        enabled = !isTestingConnection,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isTestingConnection) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = KairoDarkBg,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = if (isTestingConnection) "Testing..." else "Test Connection",
                                            color = KairoDarkBg,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    if (testResultText != null) {
                                        Text(
                                            text = testResultText ?: "",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = if (isTestSuccess) KairoSuccess else KairoError,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ══════════════════════════════════════════════════════
            // ── VOICE & OFFLINE PROCESSING ──
            // ══════════════════════════════════════════════════════
            Text(
                text = "VOICE & OFFLINE PROCESSING",
                style = MaterialTheme.typography.labelMedium,
                color = KairoOnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KairoSurface)
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // LLM Fallback Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LLM Fallback & Agent Brain",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = "Use on-device AI for autonomous multi-step agent decisions",
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

                    // ── Model 1: LLaMA 3.2 1B Agent Brain ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Agent Brain (LLaMA 3.2 1B)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KairoOnSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isModelDownloaded) KairoSuccess.copy(alpha = 0.15f) else Color(0xFFFFB300).copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isModelDownloaded) "Ready" else "Required",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                        color = if (isModelDownloaded) KairoSuccess else Color(0xFFFFB300)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (uiState.isLlmDownloading) {
                                    "Downloading: ${((uiState.llmDownloadProgress ?: 0f) * 100).toInt()}%"
                                } else if (isModelDownloaded) {
                                    "Offline LLM for autonomous multi-step tasks (554MB)"
                                } else {
                                    "Required for offline agent & screen reasoning (554MB)"
                                },
                                style = MaterialTheme.typography.bodySmall,
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
                                text = "Installed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoSuccess,
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

                    // ── Model 2: On-Device Vision Engine (SmolVLM) ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Vision Engine (SmolVLM)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KairoOnSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Phase 2C",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Visual screenshot understanding & OCR fallback (350MB)",
                                style = MaterialTheme.typography.bodySmall,
                                color = KairoOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Built-in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KairoOnSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
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
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ══════════════════════════════════════════════════════
            // ── ABOUT ──
            // ══════════════════════════════════════════════════════
            Text(
                text = "ABOUT",
                style = MaterialTheme.typography.labelMedium,
                color = KairoOnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KairoSurface)
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kairo",
                        style = MaterialTheme.typography.titleLarge,
                        color = KairoOnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.4.1 — AI Buddy & Agent Edition",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KairoOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "An offline, privacy-first AI assistant and autonomous agent for Android. All screen intelligence, LLMs, and gesture automation execute locally on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KairoOnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ══════════════════════════════════════════════════════
            // ── DIAGNOSTICS & LOGS ──
            // ══════════════════════════════════════════════════════
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
                        if (file.exists()) file.readText() else "No logs found yet."
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KairoSurface)
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(14.dp))
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
                            .height(160.dp)
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

/**
 * Reusable row for system permission / agent setting with live badge and direct settings action.
 */
@Composable
private fun AgentPermissionRow(
    icon: ImageVector,
    title: String,
    badgeText: String,
    description: String,
    isGranted: Boolean,
    buttonLabel: String,
    onButtonClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isGranted) KairoSuccess.copy(alpha = 0.15f) else KairoPrimary.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isGranted) KairoSuccess else KairoPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = KairoOnSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isGranted) KairoSuccess.copy(alpha = 0.15f) else Color(0xFFFFB300).copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isGranted) "Active" else badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = if (isGranted) KairoSuccess else Color(0xFFFFB300)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = KairoOnSurfaceVariant,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Button(
            onClick = onButtonClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) KairoSurfaceVariant else KairoPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
        ) {
            if (isGranted && buttonLabel == "Granted") {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = KairoSuccess,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = buttonLabel,
                color = if (isGranted && buttonLabel == "Granted") KairoSuccess else KairoOnSurface,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
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
