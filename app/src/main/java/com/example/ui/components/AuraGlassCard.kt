package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.LocalAuraAccessibility
import com.example.ui.theme.LocalAuraNeumorphicPalette

/**
 * Neumorphic Soft Pastel Ambient Canvas:
 * Uses soft cloud-white / slate base with ultra-gentle pastel corner light washes
 * so raised and recessed Neumorphic elements blend naturally with zero harsh visual noise.
 */
@Composable
fun AuraLiquidBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val a11y = LocalAuraAccessibility.current
    val neu = LocalAuraNeumorphicPalette.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(neu.background)
            .drawBehind {
                if (!a11y.highContrast) {
                    val cornerAlpha = if (neu.isDark) 0.08f else 0.055f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                a11y.accentPrimary.copy(alpha = cornerAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.12f, size.height * 0.10f),
                            radius = size.width * 0.75f
                        ),
                        radius = size.width * 0.75f,
                        center = Offset(size.width * 0.12f, size.height * 0.10f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                a11y.accentSecondary.copy(alpha = cornerAlpha * 0.85f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.88f, size.height * 0.82f),
                            radius = size.width * 0.70f
                        ),
                        radius = size.width * 0.70f,
                        center = Offset(size.width * 0.88f, size.height * 0.82f)
                    )
                }
            },
        content = content
    )
}

/**
 * Raised Neumorphic Card Surface (preserves AuraGlassCard symbol for seamless screen integration):
 * - Dual-direction outer shadows (light top-left + soft cool bottom-right).
 * - Spring depression on pointer-down when interactive.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    borderColor: Color = Color.Transparent,
    backgroundColor: Color = Color.Unspecified,
    elevation: Dp = 6.dp,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val neu = LocalAuraNeumorphicPalette.current
    val a11y = LocalAuraAccessibility.current
    val haptics = LocalHapticFeedback.current
    val resolvedBg = if (backgroundColor == Color.Unspecified) neu.surface else backgroundColor
    val cornerRadius = 22.dp

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed && !a11y.reducedMotion) 0.985f else 1f,
        animationSpec = AppleFluidSprings.adaptiveSpec(),
        label = "neu_card_scale"
    )

    val resolvedAccentBorder = if (borderColor != Color.Transparent) borderColor else null

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .neuRaised(
                cornerRadius = cornerRadius,
                backgroundColor = resolvedBg,
                shadowOffset = elevation.coerceIn(3.dp, 8.dp),
                blurRadius = (elevation * 2f).coerceIn(6.dp, 16.dp),
                pressed = isPressed && onClick != null,
                accentBorder = resolvedAccentBorder
            )
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onLongClick = onLongClick?.let { cb ->
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                cb()
                            }
                        },
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    )
                } else {
                    Modifier
                }
            ),
        content = content
    )
}

/**
 * Soft Scroll-Edge Fade Mask blending scrolling content cleanly beneath floating headers/bars.
 */
@Composable
fun AuraScrollEdgeMask(
    modifier: Modifier = Modifier,
    fromTop: Boolean = true,
    height: Dp = 14.dp
) {
    val bg = LocalAuraNeumorphicPalette.current.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = if (fromTop) {
                        listOf(bg.copy(alpha = 0.92f), Color.Transparent)
                    } else {
                        listOf(Color.Transparent, bg.copy(alpha = 0.92f))
                    }
                )
            )
    )
}
