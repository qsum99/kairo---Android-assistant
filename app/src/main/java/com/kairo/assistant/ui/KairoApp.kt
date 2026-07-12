package com.kairo.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kairo.assistant.ui.screens.HomeScreen
import com.kairo.assistant.ui.screens.PermissionScreen
import com.kairo.assistant.ui.screens.SettingsScreen
import com.kairo.assistant.viewmodel.KairoViewModel

@Composable
fun KairoApp(onExit: () -> Unit = {}) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val kairoViewModel: KairoViewModel = viewModel()

    val uiState by kairoViewModel.uiState.collectAsState()
    LaunchedEffect(uiState.shouldExit) {
        if (uiState.shouldExit) {
            onExit()
            kairoViewModel.resetExitState()
        }
    }

    // Monitor backstack destination transitions to manage speech listener lifecycle
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        if (currentRoute == "home") {
            kairoViewModel.startListeningAutomatic()
        }
    }

    // Check if essential permissions are already granted
    val hasRequiredPermissions = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val startDestination = if (hasRequiredPermissions) "home" else "permissions"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("permissions") {
            PermissionScreen(
                onAllGranted = {
                    kairoViewModel.onPermissionsGranted()
                    navController.navigate("home") {
                        popUpTo("permissions") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = kairoViewModel,
                onSettingsClick = {
                    kairoViewModel.stopListening() // Release mic when opening settings
                    navController.navigate("settings")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onLlmFallbackToggled = { enabled ->
                    kairoViewModel.setLlmFallbackEnabled(enabled)
                },
                viewModel = kairoViewModel
            )
        }
    }
}
