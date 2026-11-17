package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GrGamerDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = Color(0xFF003647),
    onPrimaryContainer = NeonCyanGlow,
    secondary = NeonBlue,
    onSecondary = TextPrimary,
    secondaryContainer = Color(0xFF0E2554),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = CyberEmerald,
    onTertiary = ObsidianVoid,
    tertiaryContainer = Color(0xFF003B26),
    onTertiaryContainer = CyberEmerald,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianElevated,
    surfaceContainerHigh = ObsidianBright,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF16233B),
    error = CyberCrimson,
    onError = TextPrimary,
    errorContainer = Color(0xFF4B0C20),
    onErrorContainer = Color(0xFFFFB3C4)
)

val GrGamerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun GrGamerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GrGamerDarkColorScheme,
        typography = GrGamerTypography,
        shapes = GrGamerShapes,
        content = content
    )
}
