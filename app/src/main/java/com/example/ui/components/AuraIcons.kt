package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalAuraAccessibility
import com.example.ui.theme.LocalAuraNeumorphicPalette

/**
 * Centralized Icon Size Tokens following the Lucide / Apple HIG sizing specification.
 */
object AuraIconSize {
    val Small: Dp = 16.dp
    val Input: Dp = 18.dp
    val Button: Dp = 18.dp
    val Toolbar: Dp = 20.dp
    val SidebarNav: Dp = 21.dp
    val MobileNav: Dp = 22.dp
    val Card: Dp = 22.dp
    val EmptyState: Dp = 44.dp
}

/**
 * Standard AuraIcon wrapper enforcing consistent sizing and smooth color transitions.
 */
@Composable
fun AuraIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = AuraIconSize.Toolbar,
    tint: Color = LocalContentColor.current
) {
    val a11y = LocalAuraAccessibility.current
    val animatedTint by animateColorAsState(
        targetValue = tint,
        animationSpec = AppleFluidSprings.adaptiveSpec(),
        label = "aura_icon_tint"
    )
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = if (a11y.reducedMotion) tint else animatedTint,
        modifier = modifier.size(size)
    )
}

/**
 * Animated Navigation Icon with subtle spring scale on active state (respecting Reduce Motion).
 */
@Composable
fun AuraAnimatedNavIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    selected: Boolean,
    size: Dp = AuraIconSize.MobileNav,
    tint: Color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
) {
    val a11y = LocalAuraAccessibility.current
    val scale by animateFloatAsState(
        targetValue = if (selected && !a11y.reducedMotion) 1.08f else 1f,
        animationSpec = AppleFluidSprings.adaptiveSpec(),
        label = "nav_icon_scale"
    )
    AuraIcon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        size = size,
        tint = tint,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

/**
 * Selective Neumorphic Icon Container for drawer headers, featured cards, command items, and empty states.
 */
@Composable
fun AuraIconContainer(
    imageVector: ImageVector,
    contentDescription: String? = null,
    containerSize: Dp = 40.dp,
    iconSize: Dp = AuraIconSize.Toolbar,
    cornerRadius: Dp = 12.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = Color.Unspecified,
    recessed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val neu = LocalAuraNeumorphicPalette.current
    val resolvedColor = if (containerColor == Color.Unspecified) {
        if (recessed) neu.surfaceRecessed else neu.surfaceElevated
    } else {
        containerColor
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(containerSize)
            .then(
                if (recessed) {
                    Modifier.neuInset(
                        cornerRadius = cornerRadius,
                        backgroundColor = resolvedColor,
                        shadowOffset = 3.dp,
                        blurRadius = 6.dp
                    )
                } else {
                    Modifier.neuRaised(
                        cornerRadius = cornerRadius,
                        backgroundColor = resolvedColor,
                        shadowOffset = 4.dp,
                        blurRadius = 8.dp
                    )
                }
            )
    ) {
        AuraIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            size = iconSize,
            tint = tint
        )
    }
}

private inline fun lucideIcon(
    name: String,
    block: ImageVector.Builder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply(block).build()

private inline fun ImageVector.Builder.strokePath(
    crossinline builder: PathBuilder.() -> Unit
) {
    path(
        fill = null,
        fillAlpha = 1f,
        stroke = SolidColor(Color.Black),
        strokeAlpha = 1f,
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        strokeLineMiter = 4f,
        pathFillType = PathFillType.NonZero
    ) {
        builder()
    }
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, cx + r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, cx - r, cy)
    close()
}

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    val cr = r.coerceAtMost(minOf(w, h) / 2f)
    moveTo(x + cr, y)
    lineTo(x + w - cr, y)
    quadTo(x + w, y, x + w, y + cr)
    lineTo(x + w, y + h - cr)
    quadTo(x + w, y + h, x + w - cr, y + h)
    lineTo(x + cr, y + h)
    quadTo(x, y + h, x, y + h - cr)
    lineTo(x, y + cr)
    quadTo(x, y, x + cr, y)
    close()
}

/**
 * Curated Lucide Stroke Iconography System (24x24, uniform 1.9dp rounded stroke).
 */
object AuraIcons {

    val FolderOpen: ImageVector by lazy {
        lucideIcon("FolderOpen") {
            strokePath {
                moveTo(6f, 14f)
                lineTo(7.5f, 11.1f)
                quadTo(8f, 10f, 9.2f, 10f)
                lineTo(20f, 10f)
                quadTo(21.6f, 10f, 21.1f, 11.5f)
                lineTo(19.4f, 18.5f)
                quadTo(19f, 20f, 17.5f, 20f)
                lineTo(4f, 20f)
                quadTo(2f, 20f, 2f, 18f)
                lineTo(2f, 6f)
                quadTo(2f, 4f, 4f, 4f)
                lineTo(7.9f, 4f)
                quadTo(9f, 4f, 9.6f, 4.9f)
                lineTo(10.4f, 6.1f)
                quadTo(11f, 7f, 12.1f, 7f)
                lineTo(18f, 7f)
                quadTo(20f, 7f, 20f, 9f)
                lineTo(20f, 10f)
            }
        }
    }

    val Folder: ImageVector by lazy {
        lucideIcon("Folder") {
            strokePath {
                moveTo(20f, 20f)
                quadTo(22f, 20f, 22f, 18f)
                lineTo(22f, 8f)
                quadTo(22f, 6f, 20f, 6f)
                lineTo(12.1f, 6f)
                quadTo(11f, 6f, 10.4f, 5.1f)
                lineTo(9.6f, 3.9f)
                quadTo(9f, 3f, 7.9f, 3f)
                lineTo(4f, 3f)
                quadTo(2f, 3f, 2f, 5f)
                lineTo(2f, 18f)
                quadTo(2f, 20f, 4f, 20f)
                close()
            }
        }
    }

    val Images: ImageVector by lazy {
        lucideIcon("Images") {
            strokePath {
                moveTo(18f, 22f)
                lineTo(4f, 22f)
                quadTo(2f, 22f, 2f, 20f)
                lineTo(2f, 6f)
                moveTo(22f, 13f)
                lineTo(18.9f, 9.9f)
                quadTo(18f, 9f, 17f, 9.9f)
                lineTo(10f, 16.9f)
                roundRect(6f, 2f, 16f, 16f, 2f)
                circle(11f, 7f, 1.5f)
            }
        }
    }

    val Image: ImageVector by lazy {
        lucideIcon("Image") {
            strokePath {
                roundRect(3f, 3f, 18f, 18f, 2f)
                circle(9f, 9f, 2f)
                moveTo(21f, 15f)
                lineTo(17.9f, 11.9f)
                quadTo(16.5f, 10.5f, 15.1f, 11.9f)
                lineTo(6f, 21f)
            }
        }
    }

    val ImagePlus: ImageVector by lazy {
        lucideIcon("ImagePlus") {
            strokePath {
                moveTo(21f, 12f)
                lineTo(21f, 19f)
                quadTo(21f, 21f, 19f, 21f)
                lineTo(5f, 21f)
                quadTo(3f, 21f, 3f, 19f)
                lineTo(3f, 5f)
                quadTo(3f, 3f, 5f, 3f)
                lineTo(12f, 3f)
                moveTo(16f, 5f)
                lineTo(22f, 5f)
                moveTo(19f, 2f)
                lineTo(19f, 8f)
                circle(9f, 9f, 2f)
                moveTo(21f, 15f)
                lineTo(17.9f, 11.9f)
                quadTo(16.5f, 10.5f, 15.1f, 11.9f)
                lineTo(6f, 21f)
            }
        }
    }

    val CloudUpload: ImageVector by lazy {
        lucideIcon("CloudUpload") {
            strokePath {
                moveTo(12f, 13f)
                lineTo(12f, 21f)
                moveTo(9f, 16f)
                lineTo(12f, 13f)
                lineTo(15f, 16f)
                moveTo(4f, 14.9f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.2f, 6.5f)
                arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16.9f, 5.8f)
                arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 14.5f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        lucideIcon("Settings") {
            strokePath {
                circle(12f, 12f, 3f)
                moveTo(12f, 2f)
                lineTo(12f, 5f)
                moveTo(12f, 19f)
                lineTo(12f, 22f)
                moveTo(2f, 12f)
                lineTo(5f, 12f)
                moveTo(19f, 12f)
                lineTo(22f, 12f)
                moveTo(4.93f, 4.93f)
                lineTo(7.05f, 7.05f)
                moveTo(16.95f, 16.95f)
                lineTo(19.07f, 19.07f)
                moveTo(19.07f, 4.93f)
                lineTo(16.95f, 7.05f)
                moveTo(7.05f, 16.95f)
                lineTo(4.93f, 19.07f)
                circle(12f, 12f, 7f)
            }
        }
    }

    val Terminal: ImageVector by lazy {
        lucideIcon("Terminal") {
            strokePath {
                moveTo(4f, 17f)
                lineTo(10f, 11f)
                lineTo(4f, 5f)
                moveTo(12f, 19f)
                lineTo(20f, 19f)
            }
        }
    }

    val LayoutGrid: ImageVector by lazy {
        lucideIcon("LayoutGrid") {
            strokePath {
                roundRect(3f, 3f, 7f, 7f, 1.5f)
                roundRect(14f, 3f, 7f, 7f, 1.5f)
                roundRect(14f, 14f, 7f, 7f, 1.5f)
                roundRect(3f, 14f, 7f, 7f, 1.5f)
            }
        }
    }

    val ListView: ImageVector by lazy {
        lucideIcon("ListView") {
            strokePath {
                moveTo(8f, 6f)
                lineTo(21f, 6f)
                moveTo(8f, 12f)
                lineTo(21f, 12f)
                moveTo(8f, 18f)
                lineTo(21f, 18f)
                moveTo(3f, 6f)
                lineTo(3.01f, 6f)
                moveTo(3f, 12f)
                lineTo(3.01f, 12f)
                moveTo(3f, 18f)
                lineTo(3.01f, 18f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        lucideIcon("Plus") {
            strokePath {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
                moveTo(12f, 5f)
                lineTo(12f, 19f)
            }
        }
    }

    val Minus: ImageVector by lazy {
        lucideIcon("Minus") {
            strokePath {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    val Search: ImageVector by lazy {
        lucideIcon("Search") {
            strokePath {
                circle(11f, 11f, 8f)
                moveTo(21f, 21f)
                lineTo(16.65f, 16.65f)
            }
        }
    }

    val X: ImageVector by lazy {
        lucideIcon("X") {
            strokePath {
                moveTo(18f, 6f)
                lineTo(6f, 18f)
                moveTo(6f, 6f)
                lineTo(18f, 18f)
            }
        }
    }

    val ArrowLeft: ImageVector by lazy {
        lucideIcon("ArrowLeft") {
            strokePath {
                moveTo(19f, 12f)
                lineTo(5f, 12f)
                moveTo(12f, 19f)
                lineTo(5f, 12f)
                lineTo(12f, 5f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        lucideIcon("ChevronDown") {
            strokePath {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }
    }

    val Ellipsis: ImageVector by lazy {
        lucideIcon("Ellipsis") {
            strokePath {
                circle(12f, 12f, 1f)
                circle(19f, 12f, 1f)
                circle(5f, 12f, 1f)
            }
        }
    }

    val Pin: ImageVector by lazy {
        lucideIcon("Pin") {
            strokePath {
                moveTo(12f, 17f)
                lineTo(12f, 22f)
                moveTo(9f, 10.76f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.89f, 12.55f)
                lineTo(6.11f, 13.45f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5f, 15.24f)
                lineTo(5f, 16f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 17f)
                lineTo(18f, 17f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19f, 16f)
                lineTo(19f, 15.24f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17.89f, 13.45f)
                lineTo(16.11f, 12.55f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15f, 10.76f)
                lineTo(15f, 7f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 6f)
                lineTo(17f, 6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17f, 2f)
                lineTo(7f, 2f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7f, 6f)
                lineTo(8f, 6f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9f, 7f)
                close()
            }
        }
    }

    val Heart: ImageVector by lazy {
        lucideIcon("Heart") {
            strokePath {
                moveTo(19f, 14f)
                curveTo(20.49f, 12.54f, 22f, 10.79f, 22f, 8.5f)
                curveTo(22f, 5.42f, 19.58f, 3f, 16.5f, 3f)
                curveTo(14.74f, 3f, 13.5f, 3.8f, 12f, 5.2f)
                curveTo(10.5f, 3.8f, 9.26f, 3f, 7.5f, 3f)
                curveTo(4.42f, 3f, 2f, 5.42f, 2f, 8.5f)
                curveTo(2f, 10.8f, 3.5f, 12.55f, 5f, 14f)
                lineTo(12f, 21f)
                close()
            }
        }
    }

    val Archive: ImageVector by lazy {
        lucideIcon("Archive") {
            strokePath {
                roundRect(2f, 3f, 20f, 5f, 1f)
                moveTo(4f, 8f)
                lineTo(4f, 19f)
                quadTo(4f, 21f, 6f, 21f)
                lineTo(18f, 21f)
                quadTo(20f, 21f, 20f, 19f)
                lineTo(20f, 8f)
                moveTo(10f, 12f)
                lineTo(14f, 12f)
            }
        }
    }

    val Trash2: ImageVector by lazy {
        lucideIcon("Trash2") {
            strokePath {
                moveTo(3f, 6f)
                lineTo(21f, 6f)
                moveTo(19f, 6f)
                lineTo(19f, 20f)
                quadTo(19f, 22f, 17f, 22f)
                lineTo(7f, 22f)
                quadTo(5f, 22f, 5f, 20f)
                lineTo(5f, 6f)
                moveTo(8f, 6f)
                lineTo(8f, 4f)
                quadTo(8f, 2f, 10f, 2f)
                lineTo(14f, 2f)
                quadTo(16f, 2f, 16f, 4f)
                lineTo(16f, 6f)
                moveTo(10f, 11f)
                lineTo(10f, 17f)
                moveTo(14f, 11f)
                lineTo(14f, 17f)
            }
        }
    }

    val Eraser: ImageVector by lazy {
        lucideIcon("Eraser") {
            strokePath {
                moveTo(7f, 21f)
                lineTo(20f, 21f)
                moveTo(5.6f, 11.4f)
                lineTo(13.6f, 3.4f)
                quadTo(15f, 2f, 16.4f, 3.4f)
                lineTo(20.6f, 7.6f)
                quadTo(22f, 9f, 20.6f, 10.4f)
                lineTo(10f, 21f)
                lineTo(6.4f, 21f)
                lineTo(3.4f, 18f)
                quadTo(2f, 16.6f, 3.4f, 15.2f)
                close()
                moveTo(10.5f, 6.5f)
                lineTo(17.5f, 13.5f)
            }
        }
    }

    val RotateCcw: ImageVector by lazy {
        lucideIcon("RotateCcw") {
            strokePath {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 3f)
                arcTo(9.75f, 9.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.26f, 5.74f)
                lineTo(3f, 8f)
                moveTo(3f, 3f)
                lineTo(3f, 8f)
                lineTo(8f, 8f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        lucideIcon("Copy") {
            strokePath {
                roundRect(8f, 8f, 14f, 14f, 2f)
                moveTo(4f, 16f)
                quadTo(2f, 16f, 2f, 14f)
                lineTo(2f, 4f)
                quadTo(2f, 2f, 4f, 2f)
                lineTo(14f, 2f)
                quadTo(16f, 2f, 16f, 4f)
            }
        }
    }

    val FilePenLine: ImageVector by lazy {
        lucideIcon("FilePenLine") {
            strokePath {
                moveTo(14f, 2f)
                lineTo(6f, 2f)
                quadTo(4f, 2f, 4f, 4f)
                lineTo(4f, 20f)
                quadTo(4f, 22f, 6f, 22f)
                lineTo(18f, 22f)
                quadTo(20f, 22f, 20f, 20f)
                lineTo(20f, 8f)
                close()
                moveTo(14f, 2f)
                lineTo(14f, 8f)
                lineTo(20f, 8f)
                moveTo(9f, 16.5f)
                lineTo(14.5f, 11f)
                lineTo(16.5f, 13f)
                lineTo(11f, 18.5f)
                lineTo(8.5f, 19f)
                close()
            }
        }
    }

    val FileText: ImageVector by lazy {
        lucideIcon("FileText") {
            strokePath {
                moveTo(15f, 2f)
                lineTo(6f, 2f)
                quadTo(4f, 2f, 4f, 4f)
                lineTo(4f, 20f)
                quadTo(4f, 22f, 6f, 22f)
                lineTo(18f, 22f)
                quadTo(20f, 22f, 20f, 20f)
                lineTo(20f, 7f)
                close()
                moveTo(14f, 2f)
                lineTo(14f, 8f)
                lineTo(20f, 8f)
                moveTo(16f, 13f)
                lineTo(8f, 13f)
                moveTo(16f, 17f)
                lineTo(8f, 17f)
                moveTo(10f, 9f)
                lineTo(8f, 9f)
            }
        }
    }

    val AudioLines: ImageVector by lazy {
        lucideIcon("AudioLines") {
            strokePath {
                moveTo(2f, 10f)
                lineTo(2f, 13f)
                moveTo(6f, 6f)
                lineTo(6f, 17f)
                moveTo(10f, 3f)
                lineTo(10f, 21f)
                moveTo(14f, 8f)
                lineTo(14f, 15f)
                moveTo(18f, 5f)
                lineTo(18f, 18f)
                moveTo(22f, 10f)
                lineTo(22f, 13f)
            }
        }
    }

    val Code2: ImageVector by lazy {
        lucideIcon("Code2") {
            strokePath {
                moveTo(18f, 16f)
                lineTo(22f, 12f)
                lineTo(18f, 8f)
                moveTo(6f, 8f)
                lineTo(2f, 12f)
                lineTo(6f, 16f)
                moveTo(14.5f, 4f)
                lineTo(9.5f, 20f)
            }
        }
    }

    val FileCode2: ImageVector by lazy {
        lucideIcon("FileCode2") {
            strokePath {
                moveTo(14f, 2f)
                lineTo(6f, 2f)
                quadTo(4f, 2f, 4f, 4f)
                lineTo(4f, 20f)
                quadTo(4f, 22f, 6f, 22f)
                lineTo(18f, 22f)
                quadTo(20f, 22f, 20f, 20f)
                lineTo(20f, 8f)
                close()
                moveTo(14f, 2f)
                lineTo(14f, 8f)
                lineTo(20f, 8f)
                moveTo(10f, 13f)
                lineTo(8f, 15f)
                lineTo(10f, 17f)
                moveTo(14f, 17f)
                lineTo(16f, 15f)
                lineTo(14f, 13f)
            }
        }
    }

    val Undo2: ImageVector by lazy {
        lucideIcon("Undo2") {
            strokePath {
                moveTo(9f, 14f)
                lineTo(4f, 9f)
                lineTo(9f, 4f)
                moveTo(4f, 9f)
                lineTo(14.5f, 9f)
                arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.5f, 20f)
                lineTo(11f, 20f)
            }
        }
    }

    val Redo2: ImageVector by lazy {
        lucideIcon("Redo2") {
            strokePath {
                moveTo(15f, 14f)
                lineTo(20f, 9f)
                lineTo(15f, 4f)
                moveTo(20f, 9f)
                lineTo(9.5f, 9f)
                arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.5f, 20f)
                lineTo(13f, 20f)
            }
        }
    }

    val Heading1: ImageVector by lazy {
        lucideIcon("Heading1") {
            strokePath {
                moveTo(4f, 12f)
                lineTo(12f, 12f)
                moveTo(4f, 18f)
                lineTo(4f, 6f)
                moveTo(12f, 18f)
                lineTo(12f, 6f)
                moveTo(17f, 12f)
                lineTo(20f, 10f)
                lineTo(20f, 18f)
            }
        }
    }

    val Heading2: ImageVector by lazy {
        lucideIcon("Heading2") {
            strokePath {
                moveTo(4f, 12f)
                lineTo(12f, 12f)
                moveTo(4f, 18f)
                lineTo(4f, 6f)
                moveTo(12f, 18f)
                lineTo(12f, 6f)
                moveTo(21f, 18f)
                lineTo(17f, 18f)
                curveTo(17f, 14f, 21f, 15f, 21f, 12f)
                curveTo(21f, 10.5f, 19.5f, 10f, 18.5f, 10f)
                curveTo(17.5f, 10f, 17f, 10.8f, 17f, 11.2f)
            }
        }
    }

    val Bold: ImageVector by lazy {
        lucideIcon("Bold") {
            strokePath {
                moveTo(6f, 12f)
                lineTo(15f, 12f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15f, 20f)
                lineTo(7f, 20f)
                quadTo(6f, 20f, 6f, 19f)
                lineTo(6f, 5f)
                quadTo(6f, 4f, 7f, 4f)
                lineTo(14f, 4f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14f, 12f)
            }
        }
    }

    val Italic: ImageVector by lazy {
        lucideIcon("Italic") {
            strokePath {
                moveTo(19f, 4f)
                lineTo(10f, 4f)
                moveTo(14f, 20f)
                lineTo(5f, 20f)
                moveTo(15f, 4f)
                lineTo(9f, 20f)
            }
        }
    }

    val Strikethrough: ImageVector by lazy {
        lucideIcon("Strikethrough") {
            strokePath {
                moveTo(16f, 4f)
                lineTo(9f, 4f)
                arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9f, 11f)
                moveTo(14f, 12f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14f, 20f)
                lineTo(6f, 20f)
                moveTo(4f, 12f)
                lineTo(20f, 12f)
            }
        }
    }

    val ListChecks: ImageVector by lazy {
        lucideIcon("ListChecks") {
            strokePath {
                moveTo(3f, 17f)
                lineTo(5f, 19f)
                lineTo(9f, 15f)
                moveTo(3f, 7f)
                lineTo(5f, 9f)
                lineTo(9f, 5f)
                moveTo(13f, 6f)
                lineTo(21f, 6f)
                moveTo(13f, 12f)
                lineTo(21f, 12f)
                moveTo(13f, 18f)
                lineTo(21f, 18f)
            }
        }
    }

    val ListOrdered: ImageVector by lazy {
        lucideIcon("ListOrdered") {
            strokePath {
                moveTo(10f, 6f)
                lineTo(21f, 6f)
                moveTo(10f, 12f)
                lineTo(21f, 12f)
                moveTo(10f, 18f)
                lineTo(21f, 18f)
                moveTo(4f, 6f)
                lineTo(4f, 10f)
                moveTo(4f, 10f)
                lineTo(6f, 10f)
                moveTo(4f, 18f)
                lineTo(6f, 18f)
                lineTo(4f, 15f)
                lineTo(6f, 15f)
            }
        }
    }

    val Quote: ImageVector by lazy {
        lucideIcon("Quote") {
            strokePath {
                moveTo(3f, 21f)
                curveTo(6f, 21f, 9f, 19f, 9f, 14f)
                lineTo(9f, 6f)
                quadTo(9f, 4f, 7f, 4f)
                lineTo(5f, 4f)
                quadTo(3f, 4f, 3f, 6f)
                lineTo(3f, 11f)
                quadTo(3f, 13f, 5f, 13f)
                lineTo(8f, 13f)
                moveTo(15f, 21f)
                curveTo(18f, 21f, 21f, 19f, 21f, 14f)
                lineTo(21f, 6f)
                quadTo(21f, 4f, 19f, 4f)
                lineTo(17f, 4f)
                quadTo(15f, 4f, 15f, 6f)
                lineTo(15f, 11f)
                quadTo(15f, 13f, 17f, 13f)
                lineTo(20f, 13f)
            }
        }
    }

    val Table2: ImageVector by lazy {
        lucideIcon("Table2") {
            strokePath {
                roundRect(3f, 3f, 18f, 18f, 2f)
                moveTo(3f, 9f)
                lineTo(21f, 9f)
                moveTo(3f, 15f)
                lineTo(21f, 15f)
                moveTo(9f, 3f)
                lineTo(9f, 21f)
            }
        }
    }

    val Link2: ImageVector by lazy {
        lucideIcon("Link2") {
            strokePath {
                moveTo(9f, 17f)
                lineTo(7f, 17f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 7f)
                lineTo(9f, 7f)
                moveTo(15f, 7f)
                lineTo(17f, 7f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 17f)
                lineTo(15f, 17f)
                moveTo(8f, 12f)
                lineTo(16f, 12f)
            }
        }
    }

    val Mic: ImageVector by lazy {
        lucideIcon("Mic") {
            strokePath {
                roundRect(9f, 2f, 6f, 11f, 3f)
                moveTo(19f, 10f)
                lineTo(19f, 12f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 12f)
                lineTo(5f, 10f)
                moveTo(12f, 19f)
                lineTo(12f, 22f)
                moveTo(8f, 22f)
                lineTo(16f, 22f)
            }
        }
    }

    val Paperclip: ImageVector by lazy {
        lucideIcon("Paperclip") {
            strokePath {
                moveTo(21.44f, 11.05f)
                lineTo(12.25f, 20.24f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.76f, 11.76f)
                lineTo(12.95f, 2.57f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.61f, 8.23f)
                lineTo(9.41f, 17.42f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.59f, 14.59f)
                lineTo(15.07f, 6.11f)
            }
        }
    }

    val SearchCode: ImageVector by lazy {
        lucideIcon("SearchCode") {
            strokePath {
                circle(11f, 11f, 8f)
                moveTo(21f, 21f)
                lineTo(16.65f, 16.65f)
                moveTo(9f, 9f)
                lineTo(7f, 11f)
                lineTo(9f, 13f)
                moveTo(13f, 9f)
                lineTo(15f, 11f)
                lineTo(13f, 13f)
            }
        }
    }

    val Play: ImageVector by lazy {
        lucideIcon("Play") {
            strokePath {
                moveTo(6f, 4f)
                lineTo(20f, 12f)
                lineTo(6f, 20f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        lucideIcon("Pause") {
            strokePath {
                roundRect(6f, 4f, 4f, 16f, 1f)
                roundRect(14f, 4f, 4f, 16f, 1f)
            }
        }
    }

    val Square: ImageVector by lazy {
        lucideIcon("Square") {
            strokePath {
                roundRect(5f, 5f, 14f, 14f, 2f)
            }
        }
    }

    val Globe: ImageVector by lazy {
        lucideIcon("Globe") {
            strokePath {
                circle(12f, 12f, 10f)
                moveTo(2f, 12f)
                lineTo(22f, 12f)
                moveTo(12f, 2f)
                curveTo(14.5f, 5f, 16f, 8.5f, 16f, 12f)
                curveTo(16f, 15.5f, 14.5f, 19f, 12f, 22f)
                curveTo(9.5f, 19f, 8f, 15.5f, 8f, 12f)
                curveTo(8f, 8.5f, 9.5f, 5f, 12f, 2f)
            }
        }
    }

    val Database: ImageVector by lazy {
        lucideIcon("Database") {
            strokePath {
                moveTo(3f, 5f)
                arcTo(9f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 5f)
                arcTo(9f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 5f)
                moveTo(3f, 5f)
                lineTo(3f, 19f)
                arcTo(9f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 21f, 19f)
                lineTo(21f, 5f)
                moveTo(3f, 12f)
                arcTo(9f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 21f, 12f)
            }
        }
    }

    val Sparkles: ImageVector by lazy {
        lucideIcon("Sparkles") {
            strokePath {
                moveTo(12f, 3f)
                lineTo(13.9f, 8.1f)
                lineTo(19f, 10f)
                lineTo(13.9f, 11.9f)
                lineTo(12f, 17f)
                lineTo(10.1f, 11.9f)
                lineTo(5f, 10f)
                lineTo(10.1f, 8.1f)
                close()
                moveTo(19f, 16f)
                lineTo(19f, 20f)
                moveTo(17f, 18f)
                lineTo(21f, 18f)
                moveTo(5f, 3f)
                lineTo(5f, 6f)
                moveTo(3.5f, 4.5f)
                lineTo(6.5f, 4.5f)
            }
        }
    }

    val Share2: ImageVector by lazy {
        lucideIcon("Share2") {
            strokePath {
                circle(18f, 5f, 3f)
                circle(6f, 12f, 3f)
                circle(18f, 19f, 3f)
                moveTo(8.59f, 13.51f)
                lineTo(15.42f, 17.49f)
                moveTo(15.41f, 6.51f)
                lineTo(8.59f, 10.49f)
            }
        }
    }

    val Download: ImageVector by lazy {
        lucideIcon("Download") {
            strokePath {
                moveTo(21f, 15f)
                lineTo(21f, 19f)
                quadTo(21f, 21f, 19f, 21f)
                lineTo(5f, 21f)
                quadTo(3f, 21f, 3f, 19f)
                lineTo(3f, 15f)
                moveTo(7f, 10f)
                lineTo(12f, 15f)
                lineTo(17f, 10f)
                moveTo(12f, 15f)
                lineTo(12f, 3f)
            }
        }
    }

    val Upload: ImageVector by lazy {
        lucideIcon("Upload") {
            strokePath {
                moveTo(21f, 15f)
                lineTo(21f, 19f)
                quadTo(21f, 21f, 19f, 21f)
                lineTo(5f, 21f)
                quadTo(3f, 21f, 3f, 19f)
                lineTo(3f, 15f)
                moveTo(17f, 8f)
                lineTo(12f, 3f)
                lineTo(7f, 8f)
                moveTo(12f, 3f)
                lineTo(12f, 15f)
            }
        }
    }

    val Clock: ImageVector by lazy {
        lucideIcon("Clock") {
            strokePath {
                circle(12f, 12f, 10f)
                moveTo(12f, 6f)
                lineTo(12f, 12f)
                lineTo(16f, 14f)
            }
        }
    }

    val Check: ImageVector by lazy {
        lucideIcon("Check") {
            strokePath {
                moveTo(20f, 6f)
                lineTo(9f, 17f)
                lineTo(4f, 12f)
            }
        }
    }

    val CheckCircle2: ImageVector by lazy {
        lucideIcon("CheckCircle2") {
            strokePath {
                circle(12f, 12f, 10f)
                moveTo(9f, 12f)
                lineTo(11f, 14f)
                lineTo(15f, 10f)
            }
        }
    }

    val AlertTriangle: ImageVector by lazy {
        lucideIcon("AlertTriangle") {
            strokePath {
                moveTo(10.29f, 3.86f)
                lineTo(1.82f, 18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.53f, 21f)
                lineTo(20.47f, 21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22.18f, 18f)
                lineTo(13.71f, 3.86f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10.29f, 3.86f)
                close()
                moveTo(12f, 9f)
                lineTo(12f, 13f)
                moveTo(12f, 17f)
                lineTo(12.01f, 17f)
            }
        }
    }

    val AlertCircle: ImageVector by lazy {
        lucideIcon("AlertCircle") {
            strokePath {
                circle(12f, 12f, 10f)
                moveTo(12f, 8f)
                lineTo(12f, 12f)
                moveTo(12f, 16f)
                lineTo(12.01f, 16f)
            }
        }
    }

    val Palette: ImageVector by lazy {
        lucideIcon("Palette") {
            strokePath {
                circle(12f, 12f, 10f)
                circle(13.5f, 6.5f, 1f)
                circle(17.5f, 10.5f, 1f)
                circle(8.5f, 7.5f, 1f)
                circle(6.5f, 12.5f, 1f)
                moveTo(12f, 22f)
                curveTo(12f, 19.5f, 10f, 17.5f, 12.5f, 16f)
                curveTo(14.5f, 14.8f, 17f, 16.5f, 19.5f, 15.5f)
            }
        }
    }

    val Accessibility: ImageVector by lazy {
        lucideIcon("Accessibility") {
            strokePath {
                circle(16f, 4f, 1f)
                moveTo(18f, 19f)
                lineTo(19f, 12f)
                lineTo(13f, 10f)
                moveTo(5f, 8f)
                lineTo(13f, 10f)
                lineTo(11f, 17f)
                lineTo(11f, 22f)
                moveTo(4f, 16f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 11f, 20f)
            }
        }
    }

    val HardDrive: ImageVector by lazy {
        lucideIcon("HardDrive") {
            strokePath {
                moveTo(22f, 12f)
                lineTo(2f, 12f)
                moveTo(5.45f, 5.11f)
                lineTo(2f, 12f)
                lineTo(2f, 18f)
                quadTo(2f, 20f, 4f, 20f)
                lineTo(20f, 20f)
                quadTo(22f, 20f, 22f, 18f)
                lineTo(22f, 12f)
                lineTo(18.55f, 5.11f)
                quadTo(18f, 4f, 16.76f, 4f)
                lineTo(7.24f, 4f)
                quadTo(6f, 4f, 5.45f, 5.11f)
                close()
                moveTo(6f, 16f)
                lineTo(6.01f, 16f)
                moveTo(10f, 16f)
                lineTo(10.01f, 16f)
            }
        }
    }

    val ShieldCheck: ImageVector by lazy {
        lucideIcon("ShieldCheck") {
            strokePath {
                moveTo(12f, 22f)
                curveTo(12f, 22f, 20f, 18f, 20f, 12f)
                lineTo(20f, 5f)
                lineTo(12f, 2f)
                lineTo(4f, 5f)
                lineTo(4f, 12f)
                curveTo(4f, 18f, 12f, 22f, 12f, 22f)
                close()
                moveTo(9f, 12f)
                lineTo(11f, 14f)
                lineTo(15f, 10f)
            }
        }
    }

    val UserRound: ImageVector by lazy {
        lucideIcon("UserRound") {
            strokePath {
                circle(12f, 8f, 5f)
                moveTo(20f, 21f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 21f)
            }
        }
    }

    val Tag: ImageVector by lazy {
        lucideIcon("Tag") {
            strokePath {
                moveTo(12f, 2f)
                lineTo(2f, 2f)
                lineTo(2f, 12f)
                lineTo(12.29f, 22.29f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13.7f, 22.29f)
                lineTo(22.29f, 13.7f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22.29f, 12.29f)
                close()
                circle(7f, 7f, 1f)
            }
        }
    }

    val Menu: ImageVector by lazy {
        lucideIcon("Menu") {
            strokePath {
                moveTo(4f, 6f); lineTo(20f, 6f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 18f); lineTo(20f, 18f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        lucideIcon("ChevronRight") {
            strokePath {
                moveTo(9f, 18f); lineTo(15f, 12f); lineTo(9f, 6f)
            }
        }
    }

    val PanelLeftClose: ImageVector by lazy {
        lucideIcon("PanelLeftClose") {
            strokePath {
                roundRect(3f, 3f, 18f, 18f, 2f)
                moveTo(9f, 3f); lineTo(9f, 21f)
                moveTo(16f, 15f); lineTo(13f, 12f); lineTo(16f, 9f)
            }
        }
    }

    val PanelLeftOpen: ImageVector by lazy {
        lucideIcon("PanelLeftOpen") {
            strokePath {
                roundRect(3f, 3f, 18f, 18f, 2f)
                moveTo(9f, 3f); lineTo(9f, 21f)
                moveTo(14f, 9f); lineTo(17f, 12f); lineTo(14f, 15f)
            }
        }
    }

    val LayoutDashboard: ImageVector by lazy {
        lucideIcon("LayoutDashboard") {
            strokePath {
                roundRect(3f, 3f, 7f, 9f, 1f)
                roundRect(14f, 3f, 7f, 5f, 1f)
                roundRect(14f, 12f, 7f, 9f, 1f)
                roundRect(3f, 16f, 7f, 5f, 1f)
            }
        }
    }

    val Info: ImageVector by lazy {
        lucideIcon("Info") {
            strokePath {
                circle(12f, 12f, 10f)
                moveTo(12f, 16f); lineTo(12f, 12f)
                moveTo(12f, 8f); lineTo(12.01f, 8f)
            }
        }
    }

    val RefreshCw: ImageVector by lazy {
        lucideIcon("RefreshCw") {
            strokePath {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.36f, 5.64f)
                lineTo(21f, 8f)
                moveTo(21f, 3f); lineTo(21f, 8f); lineTo(16f, 8f)
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.64f, 18.36f)
                lineTo(3f, 16f)
                moveTo(8f, 16f); lineTo(3f, 16f); lineTo(3f, 21f)
            }
        }
    }

    val LogOut: ImageVector by lazy {
        lucideIcon("LogOut") {
            strokePath {
                moveTo(9f, 21f); lineTo(5f, 21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 19f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 3f)
                lineTo(9f, 3f)
                moveTo(16f, 17f); lineTo(21f, 12f); lineTo(16f, 7f)
                moveTo(21f, 12f); lineTo(9f, 12f)
            }
        }
    }

    val Cloud: ImageVector by lazy {
        lucideIcon("Cloud") {
            strokePath {
                moveTo(17.5f, 19f)
                lineTo(9f, 19f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15.71f, 8f)
                lineTo(17.5f, 8f)
                arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.5f, 19f)
                close()
            }
        }
    }

    val CloudOff: ImageVector by lazy {
        lucideIcon("CloudOff") {
            strokePath {
                moveTo(2f, 2f); lineTo(22f, 22f)
                moveTo(5.78f, 5.78f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9f, 19f)
                lineTo(19f, 19f)
                moveTo(21.17f, 15.52f)
                arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17.5f, 8f)
                lineTo(15.71f, 8f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.5f, 5.04f)
            }
        }
    }

    val Wifi: ImageVector by lazy {
        lucideIcon("Wifi") {
            strokePath {
                moveTo(12f, 20f); lineTo(12.01f, 20f)
                moveTo(2f, 8.82f); quadTo(12f, 1f, 22f, 8.82f)
                moveTo(5f, 12.85f); quadTo(12f, 7f, 19f, 12.85f)
                moveTo(8.5f, 16.43f); quadTo(12f, 13.4f, 15.5f, 16.43f)
            }
        }
    }

    val WifiOff: ImageVector by lazy {
        lucideIcon("WifiOff") {
            strokePath {
                moveTo(2f, 2f); lineTo(22f, 22f)
                moveTo(12f, 20f); lineTo(12.01f, 20f)
                moveTo(8.5f, 16.43f); quadTo(12f, 13.4f, 15.5f, 16.43f)
                moveTo(5f, 12.85f); quadTo(8.5f, 9.9f, 12f, 9.9f)
                moveTo(16.72f, 11.06f); quadTo(18f, 11.85f, 19f, 12.85f)
                moveTo(2f, 8.82f); quadTo(4.75f, 6.67f, 7.8f, 5.58f)
                moveTo(12f, 4.95f); quadTo(17.25f, 5.0f, 22f, 8.82f)
            }
        }
    }

    val Moon: ImageVector by lazy {
        lucideIcon("Moon") {
            strokePath {
                moveTo(12f, 3f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 21f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 3f)
                close()
            }
        }
    }

    val BatteryCharging: ImageVector by lazy {
        lucideIcon("BatteryCharging") {
            strokePath {
                roundRect(2f, 7f, 16f, 10f, 2f)
                moveTo(22f, 11f); lineTo(22f, 13f)
                moveTo(11f, 9f); lineTo(8f, 12f); lineTo(12f, 12f); lineTo(9f, 15f)
            }
        }
    }

    val ArrowLeftRight: ImageVector by lazy {
        lucideIcon("ArrowLeftRight") {
            strokePath {
                moveTo(8f, 3f); lineTo(4f, 7f); lineTo(8f, 11f)
                moveTo(4f, 7f); lineTo(20f, 7f)
                moveTo(16f, 21f); lineTo(20f, 17f); lineTo(16f, 13f)
                moveTo(20f, 17f); lineTo(4f, 17f)
            }
        }
    }
}
