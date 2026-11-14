package com.example.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.ui.theme.LocalAuraAccessibility
import kotlin.math.abs

/**
 * Apple Fluid Interface Physics & Motion System (WWDC "Designing Fluid Interfaces"):
 * - Default UI springs are critically damped (dampingRatio = 1.0f, zero overshoot, ~0.32s response).
 * - Momentum/flick springs use slight under-damping (dampingRatio = 0.8f) strictly when preceded by user velocity.
 */
object AppleFluidSprings {
    fun <T> criticallyDamped(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy, // 1.0f critically damped
        stiffness = 420f // ~0.32s response
    )

    fun <T> momentum(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.8f, // Apple momentum bounce
        stiffness = 380f
    )

    @Composable
    fun <T> adaptiveSpec(): FiniteAnimationSpec<T> {
        val a11y = LocalAuraAccessibility.current
        return if (a11y.reducedMotion) {
            tween(durationMillis = 150)
        } else {
            criticallyDamped()
        }
    }
}

/**
 * Apple's exact rubber-banding resistance equation:
 * Progressive resistance at boundaries so edges never feel "frozen".
 */
fun appleRubberBand(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float {
    if (dimension <= 0f) return 0f
    return (overshoot * dimension * constant) / (dimension + constant * abs(overshoot))
}

/**
 * Apple's exponential-decay momentum projection formula:
 * Projects where a flick gesture is heading to snap to the natural target.
 */
fun appleProjectMomentum(initialVelocityPxPerSec: Float, decelerationRate: Float = 0.998f): Float {
    return (initialVelocityPxPerSec / 1000f) * decelerationRate / (1f - decelerationRate)
}

/**
 * Immediate pointer-down response modifier (§1 Response — kill latency):
 * - Scales down to 0.97f immediately on pointer-down using a critically damped spring.
 * - If reducedMotion is active, uses a gentle opacity change instead of scaling.
 * - Optional causal haptic feedback synchronized with the interaction.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.appleSpringClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    enableHaptics: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val a11y = LocalAuraAccessibility.current
    val haptics = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !a11y.reducedMotion) pressedScale else 1f,
        animationSpec = if (a11y.reducedMotion) tween(80) else AppleFluidSprings.criticallyDamped(),
        label = "apple_press_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "apple_press_alpha"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onClick = {
                        if (enableHaptics) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onClick()
                    }
                )
            } else {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = {
                        if (enableHaptics) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onClick()
                    }
                )
            }
        )
}
