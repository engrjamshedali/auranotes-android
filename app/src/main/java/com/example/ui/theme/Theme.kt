package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun getAccentColors(accentIndex: Int): Pair<Color, Color> {
    return when (accentIndex) {
        1 -> AccentCyan to AccentCyanGlow
        2 -> AccentEmerald to AccentEmeraldGlow
        3 -> AccentRose to AccentRoseGlow
        4 -> AccentAmber to AccentAmberGlow
        else -> AccentViolet to AccentVioletGlow
    }
}

@Composable
fun AuraNotesTheme(
    darkTheme: Boolean = true,
    accentIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val (primaryColor, tertiaryColor) = getAccentColors(accentIndex)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.2f),
            onPrimaryContainer = tertiaryColor,
            secondary = tertiaryColor,
            onSecondary = Color.Black,
            tertiary = tertiaryColor,
            background = AuraDarkBackground,
            onBackground = TextPrimaryDark,
            surface = AuraDarkSurface,
            onSurface = TextPrimaryDark,
            surfaceVariant = AuraDarkSurfaceVariant,
            onSurfaceVariant = TextSecondaryDark,
            outline = AuraDarkBorder
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.12f),
            onPrimaryContainer = primaryColor,
            secondary = tertiaryColor,
            onSecondary = Color.White,
            tertiary = tertiaryColor,
            background = AuraLightBackground,
            onBackground = TextPrimaryLight,
            surface = AuraLightSurface,
            onSurface = TextPrimaryLight,
            surfaceVariant = AuraLightSurfaceVariant,
            onSurfaceVariant = TextSecondaryLight,
            outline = AuraLightBorder
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
