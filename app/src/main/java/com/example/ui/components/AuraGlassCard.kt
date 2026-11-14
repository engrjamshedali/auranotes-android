package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuraDarkSpecularEdge
import com.example.ui.theme.AuraDarkSpecularSubtle
import com.example.ui.theme.AuraLightSpecularEdge
import com.example.ui.theme.AuraLightSpecularSubtle
import com.example.ui.theme.LocalAuraAccessibility

/**
 * Apple Liquid Glass Ambient Canvas:
 * Renders soft, spatial radial light pools in the active system accent colors behind the UI
 * so translucent Liquid Glass cards and floating bars naturally refract color and depth.
 * Automatically disables ambient orbs when Reduce Transparency or High Contrast is enabled.
 */
@Composable
fun AuraLiquidBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val a11y = LocalAuraAccessibility.current
    val baseBg = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
            .drawBehind {
                if (!a11y.reducedTransparency && !a11y.highContrast) {
                    val primaryAlpha = if (a11y.isDark) 0.16f else 0.12f
                    val secondaryAlpha = if (a11y.isDark) 0.13f else 0.10f

                    // Top-left spatial liquid light pool
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                a11y.accentPrimary.copy(alpha = primaryAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, size.height * 0.12f),
                            radius = size.width * 0.75f
                        ),
                        radius = size.width * 0.75f,
                        center = Offset(size.width * 0.15f, size.height * 0.12f)
                    )

                    // Center-right aqua/secondary refractive pool
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                a11y.accentSecondary.copy(alpha = secondaryAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.88f, size.height * 0.45f),
                            radius = size.width * 0.70f
                        ),
                        radius = size.width * 0.70f,
                        center = Offset(size.width * 0.88f, size.height * 0.45f)
                    )

                    // Bottom-left ambient glow behind floating dock
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                a11y.accentPrimary.copy(alpha = primaryAlpha * 0.75f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.30f, size.height * 0.90f),
                            radius = size.width * 0.65f
                        ),
                        radius = size.width * 0.65f,
                        center = Offset(size.width * 0.30f, size.height * 0.90f)
                    )
                }
            },
        content = content
    )
}

/**
 * Apple Liquid Glass Surface (§12 Materials & Depth):
 * - Multi-layered translucent glass with diagonal refractive sheen and 3D specular rim border.
 * - Top meniscus highlight catching overhead light.
 * - Honors Reduce Transparency (switches to solid opaque surface) and Increase Contrast.
 * - Immediate pointer-down spring response when interactive.
 */
@Composable
fun AuraGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 3.dp,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val a11y = LocalAuraAccessibility.current
    val resolvedBg = when {
        a11y.reducedTransparency || a11y.highContrast -> backgroundColor.copy(alpha = 1f)
        a11y.isDark -> backgroundColor.copy(alpha = 0.76f)
        else -> backgroundColor.copy(alpha = 0.84f)
    }

    val specularBright = if (a11y.isDark) AuraDarkSpecularEdge else AuraLightSpecularEdge
    val specularSubtle = if (a11y.isDark) AuraDarkSpecularSubtle else AuraLightSpecularSubtle

    val liquidRimBrush = if (a11y.highContrast) {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline))
    } else {
        Brush.linearGradient(
            colors = listOf(
                specularBright,
                borderColor,
                a11y.accentSecondary.copy(alpha = if (a11y.isDark) 0.28f else 0.22f),
                specularSubtle
            )
        )
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.appleSpringClickable(
                        pressedScale = 0.98f,
                        enableHaptics = true,
                        onLongClick = onLongClick,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .drawWithContent {
                // Inner diagonal liquid glass sheen
                if (!a11y.reducedTransparency && !a11y.highContrast) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (a11y.isDark) 0.06f else 0.35f),
                                Color.Transparent,
                                a11y.accentPrimary.copy(alpha = if (a11y.isDark) 0.04f else 0.03f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        )
                    )
                }
                drawContent()
                if (!a11y.highContrast) {
                    // Top liquid meniscus highlight
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                specularBright,
                                specularBright,
                                Color.Transparent
                            )
                        ),
                        start = Offset(20f, 1.2f),
                        end = Offset(size.width - 20f, 1.2f),
                        strokeWidth = 1.8f
                    )
                }
            },
        shape = shape,
        color = resolvedBg,
        tonalElevation = elevation,
        shadowElevation = if (a11y.highContrast) 0.dp else elevation,
        border = BorderStroke(if (a11y.highContrast) 1.5.dp else 1.dp, liquidRimBrush)
    ) {
        Box(content = content)
    }
}

/**
 * Scroll-Edge Effect Mask (§12: "Scroll edge effects, not hard dividers"):
 * Renders a soft vertical gradient mask where scrolling content meets floating translucent chrome.
 */
@Composable
fun AuraScrollEdgeMask(
    modifier: Modifier = Modifier,
    fromTop: Boolean = true,
    height: Dp = 14.dp
) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = if (fromTop) {
                        listOf(bg.copy(alpha = 0.85f), Color.Transparent)
                    } else {
                        listOf(Color.Transparent, bg.copy(alpha = 0.85f))
                    }
                )
            )
    )
}
