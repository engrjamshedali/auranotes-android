package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AuraAccessibilityConfig(
    val reducedMotion: Boolean = false,
    val reducedTransparency: Boolean = false,
    val highContrast: Boolean = false,
    val isDark: Boolean = false,
    val accentPrimary: Color = AccentLiquidBlue,
    val accentSecondary: Color = AccentLiquidBlueGlow
)

@Immutable
data class AuraNeumorphicPalette(
    val background: Color = NeuLightBackground,
    val surface: Color = NeuLightSurface,
    val surfaceElevated: Color = NeuLightSurfaceElevated,
    val surfaceRecessed: Color = NeuLightSurfaceRecessed,
    val lightShadow: Color = NeuLightHighlight,
    val darkShadow: Color = NeuLightShadow,
    val borderSubtle: Color = NeuLightBorder,
    val success: Color = NeuSuccess,
    val warning: Color = NeuWarning,
    val error: Color = NeuError,
    val isDark: Boolean = false
)

val LocalAuraAccessibility = staticCompositionLocalOf { AuraAccessibilityConfig() }
val LocalAuraNeumorphicPalette = staticCompositionLocalOf { AuraNeumorphicPalette() }

fun getAccentColors(accentIndex: Int): Pair<Color, Color> {
    return when (accentIndex) {
        0 -> AccentLiquidBlue to AccentLiquidBlueGlow
        1 -> AccentViolet to AccentVioletGlow
        2 -> AccentEmerald to AccentEmeraldGlow
        3 -> AccentRose to AccentRoseGlow
        4 -> AccentAmber to AccentAmberGlow
        else -> AccentLiquidBlue to AccentLiquidBlueGlow
    }
}

@Composable
fun AuraNotesTheme(
    darkTheme: Boolean = false,
    accentIndex: Int = 0,
    reducedMotion: Boolean = false,
    reducedTransparency: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val (primaryColor, tertiaryColor) = getAccentColors(accentIndex)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = if (highContrast) 0.36f else 0.18f),
            onPrimaryContainer = if (highContrast) Color.White else tertiaryColor,
            secondary = tertiaryColor,
            onSecondary = Color.Black,
            tertiary = AccentVioletGlow,
            background = if (highContrast) Color(0xFF0E1118) else NeuDarkBackground,
            onBackground = TextPrimaryDark,
            surface = if (highContrast) Color(0xFF151A25) else NeuDarkSurface,
            onSurface = TextPrimaryDark,
            surfaceVariant = if (highContrast) Color(0xFF1F2636) else NeuDarkSurfaceRecessed,
            onSurfaceVariant = if (highContrast) Color(0xFFE0EAFA) else TextSecondaryDark,
            outline = if (highContrast) Color(0xFF6B7CA0) else NeuDarkBorder,
            error = NeuError,
            onError = Color.White,
            errorContainer = NeuError.copy(alpha = 0.16f),
            onErrorContainer = Color(0xFFFCA5A5)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = if (highContrast) 0.22f else 0.13f),
            onPrimaryContainer = primaryColor,
            secondary = AccentViolet,
            onSecondary = Color.White,
            tertiary = tertiaryColor,
            background = if (highContrast) Color(0xFFF7FAFF) else NeuLightBackground,
            onBackground = TextPrimaryLight,
            surface = if (highContrast) Color(0xFFFFFFFF) else NeuLightSurface,
            onSurface = TextPrimaryLight,
            surfaceVariant = if (highContrast) Color(0xFFE2E9F5) else NeuLightSurfaceRecessed,
            onSurfaceVariant = if (highContrast) Color(0xFF1E293B) else TextSecondaryLight,
            outline = if (highContrast) Color(0xFF64748B) else NeuLightBorder,
            error = NeuError,
            onError = Color.White,
            errorContainer = NeuError.copy(alpha = 0.12f),
            onErrorContainer = Color(0xFFB91C1C)
        )
    }

    val neuPalette = if (darkTheme) {
        AuraNeumorphicPalette(
            background = if (highContrast) Color(0xFF0E1118) else NeuDarkBackground,
            surface = if (highContrast) Color(0xFF151A25) else NeuDarkSurface,
            surfaceElevated = if (highContrast) Color(0xFF1E2433) else NeuDarkSurfaceElevated,
            surfaceRecessed = if (highContrast) Color(0xFF0C0F16) else NeuDarkSurfaceRecessed,
            lightShadow = if (highContrast) Color(0xFF3E4A66) else NeuDarkHighlight,
            darkShadow = if (highContrast) Color(0xFF06080D) else NeuDarkShadow,
            borderSubtle = if (highContrast) Color(0xFF6B7CA0) else NeuDarkBorder,
            isDark = true
        )
    } else {
        AuraNeumorphicPalette(
            background = if (highContrast) Color(0xFFF7FAFF) else NeuLightBackground,
            surface = if (highContrast) Color(0xFFFFFFFF) else NeuLightSurface,
            surfaceElevated = NeuLightSurfaceElevated,
            surfaceRecessed = if (highContrast) Color(0xFFE2E9F5) else NeuLightSurfaceRecessed,
            lightShadow = NeuLightHighlight,
            darkShadow = if (highContrast) Color(0xFF94A3B8) else NeuLightShadow,
            borderSubtle = if (highContrast) Color(0xFF64748B) else NeuLightBorder,
            isDark = false
        )
    }

    val accessibilityConfig = AuraAccessibilityConfig(
        reducedMotion = reducedMotion,
        reducedTransparency = reducedTransparency,
        highContrast = highContrast,
        isDark = darkTheme,
        accentPrimary = primaryColor,
        accentSecondary = tertiaryColor
    )

    CompositionLocalProvider(
        LocalAuraAccessibility provides accessibilityConfig,
        LocalAuraNeumorphicPalette provides neuPalette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
