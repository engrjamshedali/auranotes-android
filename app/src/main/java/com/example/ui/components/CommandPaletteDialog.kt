package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.LocalAuraAccessibility
import com.example.ui.theme.LocalAuraNeumorphicPalette

data class PaletteAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val category: String,
    val onExecute: () -> Unit
)

/**
 * Neumorphic Spotlight Command Center:
 * Materializes smoothly with spring scale + fade and houses a recessed search well
 * plus raised command items.
 */
@Composable
fun CommandPaletteDialog(
    onDismiss: () -> Unit,
    actions: List<PaletteAction>
) {
    var searchQuery by remember { mutableStateOf("") }
    val a11y = LocalAuraAccessibility.current
    val neu = LocalAuraNeumorphicPalette.current
    var materialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        materialized = true
    }

    val scale by animateFloatAsState(
        targetValue = if (materialized || a11y.reducedMotion) 1f else 0.94f,
        animationSpec = AppleFluidSprings.adaptiveSpec(),
        label = "spotlight_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (materialized) 1f else 0f,
        animationSpec = AppleFluidSprings.adaptiveSpec(),
        label = "spotlight_alpha"
    )

    val filteredActions = remember(searchQuery, actions) {
        if (searchQuery.isBlank()) actions
        else actions.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.subtitle.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 530.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .neuRaised(
                    cornerRadius = 26.dp,
                    backgroundColor = neu.surface,
                    shadowOffset = 8.dp,
                    blurRadius = 16.dp
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AuraIconContainer(
                            imageVector = AuraIcons.Terminal,
                            containerSize = 40.dp,
                            iconSize = AuraIconSize.Button,
                            cornerRadius = 12.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Command Center",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Quick actions & navigation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AuraNeuIconButton(
                        onClick = onDismiss,
                        size = 38.dp,
                        cornerRadius = 12.dp
                    ) {
                        AuraIcon(
                            AuraIcons.X,
                            contentDescription = "Close Command Center",
                            size = AuraIconSize.Button
                        )
                    }
                }

                // Recessed Search Well
                AuraNeuTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search actions, notes, or tools...",
                    leadingIcon = {
                        AuraIcon(
                            AuraIcons.Search,
                            contentDescription = null,
                            size = AuraIconSize.Input,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                AuraIcon(AuraIcons.X, contentDescription = "Clear search", size = AuraIconSize.Small)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                if (filteredActions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No matching commands for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(filteredActions, key = { it.title }) { action ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .neuRaised(
                                        cornerRadius = 16.dp,
                                        backgroundColor = neu.surface,
                                        shadowOffset = 4.dp,
                                        blurRadius = 8.dp
                                    )
                                    .appleSpringClickable(
                                        pressedScale = 0.98f,
                                        enableHaptics = true,
                                        onClick = {
                                            onDismiss()
                                            action.onExecute()
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AuraIconContainer(
                                    imageVector = action.icon,
                                    containerSize = 38.dp,
                                    iconSize = AuraIconSize.Button,
                                    cornerRadius = 11.dp,
                                    recessed = true
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        action.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        action.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .neuInset(
                                            cornerRadius = 9.dp,
                                            backgroundColor = neu.surfaceRecessed,
                                            shadowOffset = 2.dp,
                                            blurRadius = 4.dp
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = action.category.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
