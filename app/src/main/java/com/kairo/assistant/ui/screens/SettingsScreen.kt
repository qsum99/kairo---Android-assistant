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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoDarkBg
import com.kairo.assistant.ui.theme.KairoOnSurface
import com.kairo.assistant.ui.theme.KairoOnSurfaceVariant
import com.kairo.assistant.ui.theme.KairoPrimary
import com.kairo.assistant.ui.theme.KairoSurface
import com.kairo.assistant.ui.theme.KairoSurfaceVariant
import com.kairo.assistant.service.WakeWordServiceHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLlmFallbackToggled: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE) }
    
    val isLowRam = remember {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        memoryInfo.totalMem < 4831838208L // 4.5 GB
    }
    
    var llmFallbackEnabled by remember { mutableStateOf(prefs.getBoolean("llm_fallback_enabled", !isLowRam)) }
    var wakeWordEnabled by remember { mutableStateOf(prefs.getBoolean("wake_word_enabled", false)) }
    var allowOnLockScreen by remember { mutableStateOf(prefs.getBoolean("allow_on_lock_screen", false)) }
    
    var showLogDialog by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            wakeWordEnabled = true
            prefs.edit().putBoolean("wake_word_enabled", true).apply()
            WakeWordServiceHelper.start(context)
        }
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

                    // Always-on Wake Word (Kairo)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Always-on Wake Word",
                                style = MaterialTheme.typography.titleMedium,
                                color = KairoOnSurface
                            )
                            Text(
                                text = "Wake up assistant by saying 'Kairo'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KairoOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!hasPermission) {
                                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            wakeWordEnabled = true
                                            prefs.edit().putBoolean("wake_word_enabled", true).apply()
                                            WakeWordServiceHelper.start(context)
                                        }
                                    } else {
                                        wakeWordEnabled = true
                                        prefs.edit().putBoolean("wake_word_enabled", true).apply()
                                        WakeWordServiceHelper.start(context)
                                    }
                                } else {
                                    wakeWordEnabled = false
                                    prefs.edit().putBoolean("wake_word_enabled", false).apply()
                                    WakeWordServiceHelper.stop(context)
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
                                text = "Allow assistant to launch over the lock screen",
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

            Spacer(modifier = Modifier.height(16.dp))

            // Debug Logs Button
            Button(
                onClick = {
                    logText = getLogcatLogs(context)
                    showLogDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = KairoSurface),
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, KairoSurfaceVariant, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("View Debug logs", color = KairoOnSurface)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLogDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("System Debug logs", color = KairoOnSurface) },
            text = {
                Column {
                    Box(
                        modifier = Modifier.height(300.dp)
                            .verticalScroll(rememberScrollState())
                            .background(KairoDarkBg)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = logText,
                            style = MaterialTheme.typography.bodySmall,
                            color = KairoOnSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLogDialog = false }) {
                    Text("Close")
                }
            },
            containerColor = KairoSurface
        )
    }
}

fun getLogcatLogs(context: Context): String {
    return try {
        val process = Runtime.getRuntime().exec("logcat -d -v time")
        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
        val lines = mutableListOf<String>()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            if (line!!.contains("Kairo") || line!!.contains("Llama") || line!!.contains("Fatal") || line!!.contains("SIG") || line!!.contains("backtrace") || line!!.contains("DEBUG") || line!!.contains("libc")) {
                lines.add(line!!)
            }
        }
        val lastLines = if (lines.size > 200) lines.drop(lines.size - 200) else lines
        if (lastLines.isEmpty()) "No crash/system logs found." else lastLines.joinToString("\n")
    } catch (e: Exception) {
        "Failed to read logs: ${e.message}"
    }
}
