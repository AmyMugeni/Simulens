package com.simulens.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================================
// Option 1: Ice and Ink (Dark) Signature Theme
// Enforced across all devices and Android versions (light mode overridden)
// ============================================================================

private val DarkColors = darkColorScheme(
    primary = IceBlue,                 // #60A5FA
    onPrimary = InkBackground,         // #0B1220
    primaryContainer = InkSurfaceHigh,
    onPrimaryContainer = IceBlueLight,
    secondary = InkAmber,              // #FBBF24
    onSecondary = InkOnAmber,          // #0B1220
    secondaryContainer = IceBlueContainer,
    onSecondaryContainer = IceBlueLight,
    tertiary = InkAmber,
    onTertiary = InkOnAmber,
    background = InkBackground,        // #0B1220
    onBackground = InkTextPrimary,     // #E6EEFF
    surface = InkSurface,              // Playful lighter blue card surface #152642
    onSurface = InkTextPrimary,        // #E6EEFF
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = InkTextSecondary,
    surfaceContainerLowest = InkBackground,
    surfaceContainerLow = InkSurfaceLow,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceHigh,
    surfaceContainerHighest = InkSurfaceHighest,
    outline = InkBorder,               // Glowing translucent blue border #60A5FA
    outlineVariant = InkBorder,
    error = ErrorDark,
    errorContainer = ErrorContainerDark
)

@Composable
fun SimulensTheme(
    // Permanently locked to false and true to enforce the Ice and Ink youth brand across all devices
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Ensure system bars match the midnight ink aesthetic
                window.statusBarColor = InkBackground.toArgb()
                window.navigationBarColor = InkSurfaceLow.toArgb()

                // false = light/white icons for battery, clock, and wifi on top of dark ink background
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content
    )
}
