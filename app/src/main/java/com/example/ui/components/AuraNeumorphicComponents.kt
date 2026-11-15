package com.example.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalAuraAccessibility
import com.example.ui.theme.LocalAuraNeumorphicPalette

/**
 * Raised (Convex) Neumorphic Surface Modifier:
 * - Soft top-left highlight shadow + opposing bottom-right cool shadow.
 * - Subtle top-left rim sheen for tactile extruded depth.
 * - Adapts seamlessly to Dark mode, Reduce Motion, and High Contrast accessibility.
 */
fun Modifier.neuRaised(
    cornerRadius: Dp = 20.dp,
    backgroundColor: Color = Color.Unspecified,
    shadowOffset: Dp = 6.dp,
    blurRadius: Dp = 12.dp,
    pressed: Boolean = false,
    accentBorder: Color? = null
): Modifier = composed {
    val neu = LocalAuraNeumorphicPalette.current
    val a11y = LocalAuraAccessibility.current
    val resolvedBg = if (backgroundColor == Color.Unspecified) neu.surface else backgroundColor
    val effectiveOffset = if (pressed) shadowOffset * 0.25f else shadowOffset
    val effectiveBlur = if (pressed) blurRadius * 0.35f else blurRadius
    val shape = RoundedCornerShape(cornerRadius)

    val lightPaint = remember(neu.lightShadow, effectiveBlur) {
        Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = neu.lightShadow.toArgb()
            }
        }
    }
    val darkPaint = remember(neu.darkShadow, effectiveBlur) {
        Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = neu.darkShadow.toArgb()
            }
        }
    }

    this
        .drawBehind {
            val radiusPx = cornerRadius.toPx()
            val offsetPx = effectiveOffset.toPx()
            val blurPx = effectiveBlur.toPx().coerceAtLeast(1f)

            if (!a11y.highContrast) {
                lightPaint.asFrameworkPaint().apply {
                    color = neu.lightShadow.copy(alpha = if (neu.isDark) 0.55f else 0.92f).toArgb()
                    maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                }
                darkPaint.asFrameworkPaint().apply {
                    color = neu.darkShadow.copy(alpha = if (neu.isDark) 0.85f else 0.72f).toArgb()
                    maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                }

                drawIntoCanvas { canvas ->
                    canvas.drawRoundRect(
                        left = -offsetPx,
                        top = -offsetPx,
                        right = size.width - offsetPx,
                        bottom = size.height - offsetPx,
                        radiusX = radiusPx,
                        radiusY = radiusPx,
                        paint = lightPaint
                    )
                    canvas.drawRoundRect(
                        left = offsetPx,
                        top = offsetPx,
                        right = size.width + offsetPx,
                        bottom = size.height + offsetPx,
                        radiusX = radiusPx,
                        radiusY = radiusPx,
                        paint = darkPaint
                    )
                }
            }

            // Soft sculpted surface fill with gentle directional luminosity
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = if (neu.isDark) {
                        listOf(
                            resolvedBg.copy(alpha = 1f),
                            resolvedBg.copy(alpha = 0.96f)
                        )
                    } else {
                        listOf(
                            neu.surfaceElevated.takeIf { resolvedBg == neu.surface } ?: resolvedBg,
                            resolvedBg
                        )
                    },
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                cornerRadius = CornerRadius(radiusPx, radiusPx)
            )
        }
        .border(
            width = if (a11y.highContrast) 1.5.dp else 0.8.dp,
            brush = if (accentBorder != null) {
                Brush.linearGradient(listOf(accentBorder.copy(alpha = 0.65f), accentBorder.copy(alpha = 0.25f)))
            } else if (a11y.highContrast) {
                SolidColor(neu.borderSubtle)
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        neu.lightShadow.copy(alpha = if (neu.isDark) 0.28f else 0.85f),
                        neu.borderSubtle.copy(alpha = if (neu.isDark) 0.35f else 0.4f)
                    )
                )
            },
            shape = shape
        )
        .clip(shape)
}

/**
 * Inset (Recessed / Pressed) Neumorphic Surface Modifier:
 * - Soft inner top-left dark shadow + bottom-right inner light highlight inside the cavity.
 * - Ideal for search bars, text fields, segmented pill tracks, switch wells, and stat wells.
 */
fun Modifier.neuInset(
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color = Color.Unspecified,
    shadowOffset: Dp = 3.5.dp,
    blurRadius: Dp = 7.dp,
    focusBorderColor: Color? = null
): Modifier = composed {
    val neu = LocalAuraNeumorphicPalette.current
    val a11y = LocalAuraAccessibility.current
    val resolvedBg = if (backgroundColor == Color.Unspecified) neu.surfaceRecessed else backgroundColor
    val shape = RoundedCornerShape(cornerRadius)

    val darkInnerPaint = remember(neu.darkShadow) {
        Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
            }
        }
    }
    val lightInnerPaint = remember(neu.lightShadow) {
        Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
            }
        }
    }

    this
        .drawBehind {
            val radiusPx = cornerRadius.toPx()
            val offsetPx = shadowOffset.toPx()
            val blurPx = blurRadius.toPx().coerceAtLeast(1f)
            val strokeW = (offsetPx * 2.2f).coerceAtLeast(6f)
            val halfStroke = strokeW / 2f

            drawRoundRect(
                color = resolvedBg,
                cornerRadius = CornerRadius(radiusPx, radiusPx)
            )

            if (!a11y.highContrast) {
                val clipShapePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, size.width, size.height),
                            cornerRadius = CornerRadius(radiusPx, radiusPx)
                        )
                    )
                }

                darkInnerPaint.asFrameworkPaint().apply {
                    color = neu.darkShadow.copy(alpha = if (neu.isDark) 0.90f else 0.78f).toArgb()
                    strokeWidth = strokeW
                    maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                }
                lightInnerPaint.asFrameworkPaint().apply {
                    color = neu.lightShadow.copy(alpha = if (neu.isDark) 0.55f else 0.92f).toArgb()
                    strokeWidth = strokeW
                    maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                }

                clipPath(clipShapePath) {
                    drawIntoCanvas { canvas ->
                        // Top-left inner dark shadow
                        canvas.drawRoundRect(
                            left = offsetPx - halfStroke,
                            top = offsetPx - halfStroke,
                            right = size.width + offsetPx + halfStroke,
                            bottom = size.height + offsetPx + halfStroke,
                            radiusX = radiusPx + halfStroke,
                            radiusY = radiusPx + halfStroke,
                            paint = darkInnerPaint
                        )
                        // Bottom-right inner light glow
                        canvas.drawRoundRect(
                            left = -offsetPx - halfStroke,
                            top = -offsetPx - halfStroke,
                            right = size.width - offsetPx + halfStroke,
                            bottom = size.height - offsetPx + halfStroke,
                            radiusX = radiusPx + halfStroke,
                            radiusY = radiusPx + halfStroke,
                            paint = lightInnerPaint
                        )
                    }
                }
            }
        }
        .border(
            width = if (focusBorderColor != null || a11y.highContrast) 1.4.dp else 0.6.dp,
            color = focusBorderColor
                ?: if (a11y.highContrast) neu.borderSubtle else neu.borderSubtle.copy(alpha = 0.45f),
            shape = shape
        )
        .clip(shape)
}

/**
 * Tactile Neumorphic Raised Button (Primary Accent or Soft Surface).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraNeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAccent: Boolean = true,
    enabled: Boolean = true,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val neu = LocalAuraNeumorphicPalette.current
    val a11y = LocalAuraAccessibility.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !a11y.reducedMotion) 0.965f else 1f,
        animationSpec = AppleFluidSprings.adaptiveSpec(),
        label = "neu_btn_scale"
    )

    val bgColor = when {
        !enabled -> neu.surfaceRecessed
        isAccent -> MaterialTheme.colorScheme.primary
        else -> neu.surface
    }
    val fgColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isAccent -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isAccent && enabled) {
                    Modifier.neuRaised(
                        cornerRadius = cornerRadius,
                        backgroundColor = bgColor,
                        shadowOffset = if (isPressed) 2.dp else 5.dp,
                        blurRadius = if (isPressed) 5.dp else 11.dp,
                        pressed = isPressed,
                        accentBorder = a11y.accentSecondary
                    )
                } else if (isPressed && enabled) {
                    Modifier.neuInset(
                        cornerRadius = cornerRadius,
                        backgroundColor = neu.surfaceRecessed,
                        shadowOffset = 2.5.dp,
                        blurRadius = 5.dp
                    )
                } else {
                    Modifier.neuRaised(
                        cornerRadius = cornerRadius,
                        backgroundColor = bgColor,
                        shadowOffset = if (enabled) 5.dp else 2.dp,
                        blurRadius = if (enabled) 10.dp else 4.dp
                    )
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides fgColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

/**
 * Circular / Rounded-Square Tactile Neumorphic Icon Button (min 48dp hit target).
 */
@Composable
fun AuraNeuIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 14.dp,
    isActive: Boolean = false,
    isAccent: Boolean = false,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    val neu = LocalAuraNeumorphicPalette.current
    val a11y = LocalAuraAccessibility.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !a11y.reducedMotion) 0.92f else 1f,
        animationSpec = AppleFluidSprings.adaptiveSpec(),
        label = "neu_icon_btn_scale"
    )

    val resolvedBg = when {
        isAccent -> MaterialTheme.colorScheme.primary
        isActive || isPressed -> neu.surfaceRecessed
        else -> neu.surface
    }
    val resolvedTint = when {
        tint != Color.Unspecified -> tint
        isAccent -> Color.White
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .then(
                    if (isActive || (isPressed && !isAccent)) {
                        Modifier.neuInset(
                            cornerRadius = cornerRadius,
                            backgroundColor = resolvedBg,
                            shadowOffset = 2.5.dp,
                            blurRadius = 5.dp,
                            focusBorderColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else null
                        )
                    } else {
                        Modifier.neuRaised(
                            cornerRadius = cornerRadius,
                            backgroundColor = resolvedBg,
                            shadowOffset = if (isPressed) 2.dp else 4.dp,
                            blurRadius = if (isPressed) 4.dp else 9.dp,
                            pressed = isPressed
                        )
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                )
        ) {
            CompositionLocalProvider(LocalContentColor provides resolvedTint) {
                content()
            }
        }
    }
}

/**
 * Recessed Neumorphic Text Input Field with animated focus ring.
 */
@Composable
fun AuraNeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    cornerRadius: Dp = 16.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val neu = LocalAuraNeumorphicPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val focusBorder by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.Transparent,
        animationSpec = tween(160),
        label = "neu_field_focus"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .neuInset(
                cornerRadius = cornerRadius,
                backgroundColor = neu.surfaceRecessed,
                shadowOffset = 3.dp,
                blurRadius = 6.dp,
                focusBorderColor = if (isFocused) focusBorder else null
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(10.dp))
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    interactionSource = interactionSource,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(6.dp))
                trailingIcon()
            }
        }
    }
}

/**
 * Custom Neumorphic Toggle Switch:
 * Recessed capsule track + raised floating thumb that slides smoothly.
 */
@Composable
fun AuraNeuSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val neu = LocalAuraNeumorphicPalette.current
    val a11y = LocalAuraAccessibility.current
    val haptics = LocalHapticFeedback.current

    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else neu.surfaceRecessed,
        animationSpec = AppleFluidSprings.adaptiveSpec(),
        label = "neu_switch_track"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 3.dp,
        animationSpec = AppleFluidSprings.adaptiveSpec(),
        label = "neu_switch_thumb"
    )

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .defaultMinSize(minWidth = 54.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(!checked)
                }
            )
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .width(54.dp)
                .height(30.dp)
                .neuInset(
                    cornerRadius = 15.dp,
                    backgroundColor = trackColor,
                    shadowOffset = 2.5.dp,
                    blurRadius = 5.dp,
                    focusBorderColor = if (checked && a11y.highContrast) MaterialTheme.colorScheme.primary else null
                )
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(24.dp)
                    .neuRaised(
                        cornerRadius = 12.dp,
                        backgroundColor = if (checked) Color.White else neu.surfaceElevated,
                        shadowOffset = 2.dp,
                        blurRadius = 4.dp
                    )
            )
        }
    }
}

/**
 * Neumorphic Segmented Control:
 * Recessed outer track housing raised active segment.
 */
@Composable
fun <T> AuraNeuSegmentedControl(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val neu = LocalAuraNeumorphicPalette.current
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .neuInset(
                cornerRadius = 16.dp,
                backgroundColor = neu.surfaceRecessed,
                shadowOffset = 3.dp,
                blurRadius = 6.dp
            )
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        options.forEach { (value, label) ->
            val selected = selectedOption == value
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .then(
                        if (selected) {
                            Modifier.neuRaised(
                                cornerRadius = 12.dp,
                                backgroundColor = neu.surfaceElevated,
                                shadowOffset = 3.dp,
                                blurRadius = 6.dp,
                                accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            )
                        } else {
                            Modifier.clip(RoundedCornerShape(12.dp))
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(value)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Debossed Neumorphic Groove Divider.
 */
@Composable
fun AuraNeuDivider(modifier: Modifier = Modifier) {
    val neu = LocalAuraNeumorphicPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(neu.darkShadow.copy(alpha = if (neu.isDark) 0.65f else 0.48f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(neu.lightShadow.copy(alpha = if (neu.isDark) 0.35f else 0.75f))
        )
    }
}
