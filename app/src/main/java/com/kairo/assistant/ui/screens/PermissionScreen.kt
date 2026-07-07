package com.kairo.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoDarkBg
import com.kairo.assistant.ui.theme.KairoGradientEnd
import com.kairo.assistant.ui.theme.KairoGradientStart
import com.kairo.assistant.ui.theme.KairoOnSurface
import com.kairo.assistant.ui.theme.KairoOnSurfaceVariant
import com.kairo.assistant.ui.theme.KairoPrimary
import com.kairo.assistant.ui.theme.KairoSurface
import com.kairo.assistant.ui.theme.KairoSurfaceVariant
import com.kairo.assistant.ui.theme.KairoSuccess

private val requiredPermissions = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.CALL_PHONE,
    Manifest.permission.SEND_SMS,
    Manifest.permission.READ_PHONE_STATE
)

@Composable
fun PermissionScreen(
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current
    var permissionStates by remember {
        mutableStateOf(
            requiredPermissions.associateWith { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionStates = requiredPermissions.associateWith { perm ->
            results[perm] == true ||
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Auto-navigate when all granted
    LaunchedEffect(permissionStates) {
        if (permissionStates.values.all { it }) {
            onAllGranted()
        }
    }

    val allGranted = permissionStates.values.all { it }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KairoDarkBg)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header
            Text(
                text = "Welcome to",
                style = MaterialTheme.typography.titleLarge,
                color = KairoOnSurfaceVariant
            )
            Text(
                text = "KAIRO",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    brush = Brush.linearGradient(
                        colors = listOf(KairoGradientStart, KairoGradientEnd)
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "To work as your voice assistant, Kairo needs a few permissions.",
                style = MaterialTheme.typography.bodyLarge,
                color = KairoOnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Permission items
            PermissionItem(
                icon = Icons.Default.Mic,
                name = "Microphone",
                description = "Hear your voice commands",
                isGranted = permissionStates[Manifest.permission.RECORD_AUDIO] == true
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionItem(
                icon = Icons.Default.Contacts,
                name = "Contacts",
                description = "Call or text people by name",
                isGranted = permissionStates[Manifest.permission.READ_CONTACTS] == true
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionItem(
                icon = Icons.Default.Call,
                name = "Phone",
                description = "Make calls hands-free",
                isGranted = permissionStates[Manifest.permission.CALL_PHONE] == true
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionItem(
                icon = Icons.Default.Sms,
                name = "SMS",
                description = "Send messages hands-free",
                isGranted = permissionStates[Manifest.permission.SEND_SMS] == true
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionItem(
                icon = Icons.Default.Call,
                name = "Phone Status",
                description = "Detect carrier and SIM configuration for dual-SIM support",
                isGranted = permissionStates[Manifest.permission.READ_PHONE_STATE] == true
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Grant button
            if (!allGranted) {
                Button(
                    onClick = {
                        val ungrantedPermissions = requiredPermissions.filter {
                            permissionStates[it] != true
                        }.toTypedArray()
                        if (ungrantedPermissions.isNotEmpty()) {
                            launcher.launch(ungrantedPermissions)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KairoPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "Grant Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        color = KairoOnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip button
            if (!allGranted) {
                Button(
                    onClick = { onAllGranted() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KairoSurfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Skip for now",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KairoOnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    name: String,
    description: String,
    isGranted: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KairoSurface)
            .border(
                width = 1.dp,
                color = if (isGranted) KairoSuccess.copy(alpha = 0.3f) else KairoSurfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) KairoSuccess.copy(alpha = 0.15f)
                    else KairoPrimary.copy(alpha = 0.15f)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = if (isGranted) KairoSuccess else KairoPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = KairoOnSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = KairoOnSurfaceVariant
            )
        }
        if (isGranted) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleLarge,
                color = KairoSuccess
            )
        }
    }
}
