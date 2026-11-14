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
    val isDark: Boolean = true,
    val accentPrimary: Color = AccentLiquidBlue,
    val accentSecondary: Color = AccentLiquidBlueGlow
)

val LocalAuraAccessibility = staticCompositionLocalOf { AuraAccessibilityConfig() }

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
    darkTheme: Boolean = true,
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
            primaryContainer = primaryColor.copy(alpha = if (highContrast) 0.40f else 0.24f),
            onPrimaryContainer = if (highContrast) Color.White else tertiaryColor,
            secondary = tertiaryColor,
            onSecondary = Color.Black,
            tertiary = tertiaryColor,
            background = if (highContrast) Color(0xFF000000) else AuraDarkBackground,
            onBackground = TextPrimaryDark,
            surface = if (highContrast) Color(0xFF0B101D) else AuraDarkSurface,
            onSurface = TextPrimaryDark,
            surfaceVariant = if (highContrast) Color(0xFF18243D) else AuraDarkSurfaceVariant,
            onSurfaceVariant = if (highContrast) Color(0xFFE0EAFA) else TextSecondaryDark,
            outline = if (highContrast) Color(0xFF6885BA) else AuraDarkBorder
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = if (highContrast) 0.24f else 0.14f),
            onPrimaryContainer = primaryColor,
            secondary = tertiaryColor,
            onSecondary = Color.White,
            tertiary = tertiaryColor,
            background = if (highContrast) Color(0xFFFFFFFF) else AuraLightBackground,
            onBackground = TextPrimaryLight,
            surface = AuraLightSurface,
            onSurface = TextPrimaryLight,
            surfaceVariant = if (highContrast) Color(0xFFDCE6F7) else AuraLightSurfaceVariant,
            onSurfaceVariant = if (highContrast) Color(0xFF172338) else TextSecondaryLight,
            outline = if (highContrast) Color(0xFF5D7499) else AuraLightBorder
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

    CompositionLocalProvider(LocalAuraAccessibility provides accessibilityConfig) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
