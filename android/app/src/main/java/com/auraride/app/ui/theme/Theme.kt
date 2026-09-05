package com.auraride.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Material 3 has no "verified" slot, so the reserved safety colors ride on an
// extended-colors CompositionLocal. Read via AuraColors.current.verified.
data class AuraExtendedColors(
    val verified: Color,
    val verifiedContainer: Color,
    val warn: Color,
)

val AuraColors = staticCompositionLocalOf {
    AuraExtendedColors(verified = Verified, verifiedContainer = VerifiedContainer, warn = Warn)
}

private val LightScheme = lightColorScheme(
    primary = Plum,
    onPrimary = Color.White,
    primaryContainer = Chip,
    onPrimaryContainer = Plum,
    background = Surface,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Chip,
    onSurfaceVariant = Muted,
    outline = Line,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = PlumDark,
    onPrimary = Color(0xFF2A0C1B),
    primaryContainer = ChipDark,
    onPrimaryContainer = PlumDark,
    background = Color(0xFF171015),
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = ChipDark,
    onSurfaceVariant = MutedDark,
    outline = LineDark,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
fun AuraRideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extended = if (darkTheme) {
        AuraExtendedColors(VerifiedDark, VerifiedContainerDark, Warn)
    } else {
        AuraExtendedColors(Verified, VerifiedContainer, Warn)
    }
    CompositionLocalProvider(AuraColors provides extended) {
        MaterialTheme(colorScheme = scheme, typography = AuraTypography, content = content)
    }
}
