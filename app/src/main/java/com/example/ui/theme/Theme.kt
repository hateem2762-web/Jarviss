package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = ObsidianBackground,
    primaryContainer = JarvisCyanDeep,
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = JarvisAmber,
    onSecondary = ObsidianBackground,
    secondaryContainer = JarvisAmberDeep,
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = JarvisEmerald,
    onTertiary = ObsidianBackground,
    tertiaryContainer = JarvisEmeraldDeep,
    onTertiaryContainer = Color(0xFFA7F3D0),
    error = JarvisCrimson,
    onError = Color.White,
    errorContainer = JarvisCrimsonDeep,
    onErrorContainer = Color(0xFFFECDD3),
    background = ObsidianBackground,
    onBackground = ObsidianOnSurface,
    surface = ObsidianSurface,
    onSurface = ObsidianOnSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianOnSurfaceMuted,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1E293B),
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = TacticalCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = TacticalAmberDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
    background = TacticalLightBackground,
    onBackground = TacticalLightOnSurface,
    surface = TacticalLightSurface,
    onSurface = TacticalLightOnSurface,
    surfaceVariant = TacticalLightSurfaceVariant,
    onSurfaceVariant = TacticalLightOnSurfaceMuted,
    outline = TacticalLightOutline,
    outlineVariant = Color(0xFFE2E8F0),
)

val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun JarvisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) JarvisDarkColorScheme else JarvisLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content,
    )
}
