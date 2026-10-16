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
    primary = PrimaryIndigo,
    onPrimary = Color.White,
    primaryContainer = PrimaryIndigoContainerLight,
    onPrimaryContainer = OnPrimaryIndigoContainerLight,
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = SecondaryTealContainerLight,
    onSecondaryContainer = OnSecondaryTealContainerLight,
    tertiary = TertiaryCoral,
    onTertiary = Color.White,
    tertiaryContainer = TertiaryCoralContainerLight,
    onTertiaryContainer = OnTertiaryCoralContainerLight,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightBackground,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorLight,
    errorContainer = ErrorContainerLight
)

private val DarkColors = darkColorScheme(
    primary = PrimaryIndigoDark,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = PrimaryIndigoContainerDark,
    onPrimaryContainer = OnPrimaryIndigoContainerDark,
    secondary = SecondaryTealDark,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = SecondaryTealContainerDark,
    onSecondaryContainer = OnSecondaryTealContainerDark,
    tertiary = TertiaryCoralDark,
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = TertiaryCoralContainerDark,
    onTertiaryContainer = OnTertiaryCoralContainerDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkBackground,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF090D16),
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorDark,
    errorContainer = ErrorContainerDark
)

@Composable
fun CognilensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set to false by default to ensure our brand blend colors are reflected cleanly
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
