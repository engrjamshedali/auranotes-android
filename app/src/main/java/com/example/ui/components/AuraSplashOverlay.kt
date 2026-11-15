package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalAuraAccessibility
import com.example.ui.theme.LocalAuraNeumorphicPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Neumorphic Soft UI Splash Overlay:
 * - Fast, non-blocking reveal (~850ms total) with guaranteed completion callback.
 * - Soft embossed squircle emblem on cloud/slate backdrop + recessed progress well.
 * - Honors Reduce Motion with minimal fade.
 */
@Composable
fun AuraSplashOverlay(
    visible: Boolean,
    onSplashFinished: () -> Unit
) {
    val a11y = LocalAuraAccessibility.current
    val neu = LocalAuraNeumorphicPalette.current
    val logoScale = remember { Animatable(if (a11y.reducedMotion) 1f else 0.86f) }
    val logoAlpha = remember { Animatable(if (a11y.reducedMotion) 1f else 0f) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (a11y.reducedMotion) {
            progress.snapTo(1f)
            delay(420)
        } else {
            launch {
                logoAlpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            }
            launch {
                logoScale.animateTo(1f, AppleFluidSprings.criticallyDamped())
            }
            launch {
                progress.animateTo(1f, tween(740, easing = FastOutSlowInEasing))
            }
            delay(840)
        }
        onSplashFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = if (a11y.reducedMotion) {
            fadeOut(tween(180))
        } else {
            fadeOut(tween(240)) + scaleOut(
                targetScale = 1.04f,
                animationSpec = tween(240)
            )
        }
    ) {
        AuraLiquidBackground(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(32.dp)
                        .graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                            alpha = logoAlpha.value
                        }
                ) {
                    // Outer Neumorphic Raised Well
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(112.dp)
                            .neuRaised(
                                cornerRadius = 32.dp,
                                backgroundColor = neu.surface,
                                shadowOffset = 8.dp,
                                blurRadius = 16.dp
                            )
                    ) {
                        // Inner Soft Accent Emblem
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(a11y.accentPrimary, a11y.accentSecondary)
                                    )
                                )
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "AuraNotes Logo",
                                modifier = Modifier.size(76.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        text = "AuraNotes",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Notes · Vault · Code Studio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(28.dp))

                    // Recessed Neumorphic Progress Track
                    Box(
                        modifier = Modifier
                            .width(136.dp)
                            .height(7.dp)
                            .neuInset(
                                cornerRadius = 4.dp,
                                backgroundColor = neu.surfaceRecessed,
                                shadowOffset = 2.dp,
                                blurRadius = 4.dp
                            )
                            .padding(1.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(50))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(a11y.accentPrimary, a11y.accentSecondary)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
