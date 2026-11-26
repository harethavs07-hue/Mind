package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ShadowRoomColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = ObsidianVoid,
    primaryContainer = DeepAmethyst,
    onPrimaryContainer = WhisperSilver,
    secondary = RoyalViolet,
    onSecondary = WhisperSilver,
    secondaryContainer = ShadowSurfaceElevated,
    onSecondaryContainer = SoftLavender,
    tertiary = PortalCyan,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = WhisperSilver,
    surface = RoomDepth,
    onSurface = WhisperSilver,
    surfaceVariant = ShadowSurface,
    onSurfaceVariant = MutedSlate,
    outline = SubtleBorder,
    error = CrimsonAlert,
    onError = WhisperSilver
)

private val SubconsciousShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun SubconsciousTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ShadowRoomColorScheme,
        typography = SubconsciousTypography,
        shapes = SubconsciousShapes,
        content = content
    )
}
