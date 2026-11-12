package com.simulens.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Curated Student Palette
// Background: #0B1220 | Ink: #E6EEFF | Primary: #60A5FA | Accent: #FBBF24 | Tint: #16233B
// ============================================================================

// Main Palette Tokens
val InkBackground = Color(0xFF0B1220)        // Deep navy background (#0b1220)
val InkTextPrimary = Color(0xFFE6EEFF)       // Soft ice ink text (#e6eeff)
val IceBlue = Color(0xFF60A5FA)              // Primary sky blue (#60a5fa)
val InkAmber = Color(0xFFFBBF24)             // Accent warm amber (#fbbf24)
val InkTint = Color(0xFF16233B)              // Tint surface (#16233b)

// Playful, lighter translucent blue card surface
val InkSurface = Color(0xFF152642)           // Lighter blue card surface
val InkSurfaceLow = Color(0xFF101B2F)        // Surface low (navbar)
val InkSurfaceHigh = Color(0xFF1C3154)       // Surface high (chips/interactive containers)
val InkSurfaceHighest = Color(0xFF223A63)    // Surface highest elevation
val InkBorder = Color(0x5960A5FA)            // Subtle glowing translucent blue border (35% opacity #60a5fa)

// Accent Variations
val InkAmberLight = Color(0xFFFDE047)
val InkAmberDark = Color(0xFFD97706)
val InkOnAmber = Color(0xFF0B1220)

val IceBlueLight = Color(0xFF93C5FD)
val IceBlueBanner = Color(0xFF3B82F6)
val IceBlueContainer = Color(0xFF172A4A)
val OnIceBlue = Color(0xFF0B1220)

// Text Tokens
val InkTextSecondary = Color(0xFF94A3B8)     // Cool slate secondary text
val InkTextMuted = Color(0xFF64748B)         // Muted text

// Status & Risk Levels
val RiskLow = Color(0xFF22C55E)              // Emerald Green
val RiskLowBg = Color(0xFF0F291E)
val RiskModerate = Color(0xFFFBBF24)         // Warm Amber
val RiskModerateBg = Color(0xFF2E240D)
val RiskHigh = Color(0xFFEF4444)             // Alert Coral
val RiskHighBg = Color(0xFF331418)

val ErrorDark = Color(0xFFEF4444)
val ErrorContainerDark = Color(0xFF331418)

// ============================================================================
// Backward compatibility aliases for existing references
// ============================================================================
val PrimaryIndigo = InkAmber
val PrimaryIndigoLight = InkAmberLight
val PrimaryIndigoDark = InkAmber
val PrimaryIndigoContainerLight = Color(0xFFFEF3C7)
val OnPrimaryIndigoContainerLight = InkOnAmber
val PrimaryIndigoContainerDark = InkSurfaceHigh
val OnPrimaryIndigoContainerDark = InkAmber

val SecondaryTeal = IceBlue
val SecondaryTealLight = IceBlueLight
val SecondaryTealDark = IceBlue
val SecondaryTealContainerLight = Color(0xFFE0F2FE)
val OnSecondaryTealContainerLight = Color(0xFF0369A1)
val SecondaryTealContainerDark = IceBlueContainer
val OnSecondaryTealContainerDark = IceBlueLight

val TertiaryCoral = IceBlueBanner
val TertiaryCoralLight = IceBlueLight
val TertiaryCoralDark = IceBlueBanner
val TertiaryCoralContainerLight = Color(0xFFEFF6FF)
val OnTertiaryCoralContainerLight = Color(0xFF1E3A8A)
val TertiaryCoralContainerDark = Color(0xFF172554)
val OnTertiaryCoralContainerDark = Color(0xFFDBEAFE)

val LightBackground = Color(0xFFF8FAFC)
val LightOnBackground = Color(0xFF0F172A)
val LightSurfaceLow = Color(0xFFF1F5F9)
val LightSurfaceContainer = Color(0xFFE2E8F0)
val LightSurfaceHigh = Color(0xFFCBD5E1)
val LightSurfaceVariant = Color(0xFFE2E8F0)
val LightOnSurfaceVariant = Color(0xFF475569)
val LightOutline = Color(0xFFCBD5E1)
val LightOutlineVariant = Color(0xFFE2E8F0)

val DarkBackground = InkBackground
val DarkOnBackground = InkTextPrimary
val DarkSurfaceLow = InkSurfaceLow
val DarkSurfaceContainer = InkSurface
val DarkSurfaceHigh = InkSurfaceHigh
val DarkSurfaceVariant = InkSurfaceHigh
val DarkOnSurfaceVariant = InkTextSecondary
val DarkOutline = InkBorder
val DarkOutlineVariant = InkBorder

val ErrorLight = Color(0xFFDC2626)
val ErrorContainerLight = Color(0xFFFEE2E2)
