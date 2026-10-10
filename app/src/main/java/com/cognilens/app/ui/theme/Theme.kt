package com.cognilens.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = SteelBlue,
    onPrimary = Color.White,
    primaryContainer = SkyBlue,
    onPrimaryContainer = Navy,
    secondary = Coral,
    onSecondary = Navy,
    secondaryContainer = CoralContainer,
    background = LightBackground,
    onBackground = Navy,
    surface = LightBackground,
    onSurface = Navy,
    surfaceVariant = Ice,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Ice,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorLight,
    errorContainer = ErrorContainerLight
)

private val DarkColors = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Navy,
    primaryContainer = SteelBlue,
    onPrimaryContainer = Ice,
    secondary = Coral,
    onSecondary = Navy,
    background = DarkBackground,
    onBackground = Ice,
    surface = DarkBackground,
    onSurface = Ice,
    surfaceVariant = Navy,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = Navy,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = ErrorDark,
    errorContainer = ErrorContainerDark
)

@Composable
fun CognilensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set to false by default to ensure brand colors are reflected
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
