package com.kairo.assistant.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val KairoDarkColorScheme = darkColorScheme(
    primary = KairoPrimary,
    onPrimary = KairoOnSurface,
    primaryContainer = KairoPrimaryVariant,
    secondary = KairoAccent,
    onSecondary = KairoOnSurface,
    tertiary = KairoPurple,
    background = KairoDarkBg,
    onBackground = KairoOnSurface,
    surface = KairoSurface,
    onSurface = KairoOnSurface,
    surfaceVariant = KairoSurfaceVariant,
    onSurfaceVariant = KairoOnSurfaceVariant,
    error = KairoError,
    onError = KairoOnSurface,
    outline = KairoOnSurfaceVariant
)

@Composable
fun KairoTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = KairoDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val transparentColor = androidx.compose.ui.graphics.Color.Transparent.toArgb()
            window.statusBarColor = transparentColor
            window.navigationBarColor = transparentColor
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KairoTypography,
        content = content
    )
}
