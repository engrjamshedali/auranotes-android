package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.auth.GoogleUserProfile
import com.example.data.local.CloudSyncStatus
import com.example.ui.theme.LocalAuraAccessibility
import com.example.ui.theme.LocalAuraNeumorphicPalette

data class DrawerNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int? = null,
    val isSelected: Boolean = false,
    val accentTint: Color? = null,
    val dotColor: Color? = null,
    val onClick: () -> Unit
)

data class DrawerNavCategory(
    val id: String,
    val title: String,
    val items: List<DrawerNavItem>,
    val initiallyExpanded: Boolean = true
)

/**
 * Reusable Neumorphic Expandable Drawer Panel:
 * - Used inside ModalNavigationDrawer on phones and as a smoothly collapsible sidebar on tablets/desktops.
 * - Includes app logo, workspace profile, expandable accordion categories, and active recessed indicators.
 */
@Composable
fun AuraExpandableDrawerContent(
    categories: List<DrawerNavCategory>,
    isCollapsed: Boolean = false,
    showCollapseToggle: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    showCloseButton: Boolean = false,
    onCloseDrawer: () -> Unit = {},
    userProfile: GoogleUserProfile? = null,
    syncStatus: CloudSyncStatus = CloudSyncStatus.AUTH_REQUIRED,
    onAccountCardClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val neu = LocalAuraNeumorphicPalette.current
    val a11y = LocalAuraAccessibility.current
    val haptics = LocalHapticFeedback.current

    val expandedStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            categories.forEach { put(it.id, it.initiallyExpanded) }
        }
    }

    val drawerWidth by animateDpAsState(
        targetValue = if (isCollapsed) 84.dp else 292.dp,
        animationSpec = AppleFluidSprings.adaptiveSpec(),
        label = "drawer_width"
    )

    Column(
        modifier = modifier
            .width(drawerWidth)
            .fillMaxHeight()
            .background(neu.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = if (isCollapsed) 10.dp else 16.dp, vertical = 14.dp)
    ) {
        // Drawer Header: Logo, Brand, Expand/Collapse or Close control
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            if (!isCollapsed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .neuRaised(
                                cornerRadius = 15.dp,
                                backgroundColor = MaterialTheme.colorScheme.primary,
                                shadowOffset = 4.dp,
                                blurRadius = 8.dp
                            )
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "AuraNotes Logo",
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "AuraNotes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                            Box(
                                modifier = Modifier
                                    .neuInset(
                                        cornerRadius = 8.dp,
                                        backgroundColor = neu.surfaceRecessed,
                                        shadowOffset = 2.dp,
                                        blurRadius = 4.dp
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v2.0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = if (userProfile != null) "Cloud & Local Vault" else "Offline Workspace",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            if (showCollapseToggle) {
                AuraNeuIconButton(
                    onClick = onToggleCollapse,
                    size = 40.dp,
                    cornerRadius = 12.dp,
                    modifier = Modifier.testTag("drawer_collapse_toggle")
                ) {
                    AuraIcon(
                        imageVector = if (isCollapsed) AuraIcons.PanelLeftOpen else AuraIcons.PanelLeftClose,
                        contentDescription = if (isCollapsed) "Expand Navigation Drawer" else "Collapse Navigation Drawer",
                        size = AuraIconSize.Button,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (showCloseButton) {
                AuraNeuIconButton(
                    onClick = onCloseDrawer,
                    size = 40.dp,
                    cornerRadius = 12.dp,
                    modifier = Modifier.testTag("drawer_close_button")
                ) {
                    AuraIcon(
                        imageVector = AuraIcons.X,
                        contentDescription = "Close Drawer",
                        size = AuraIconSize.Button
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Profile / Workspace Identity Card (hidden when collapsed to rail)
        AnimatedVisibility(
            visible = !isCollapsed,
            enter = fadeIn(tween(180)) + expandVertically(),
            exit = fadeOut(tween(140)) + shrinkVertically()
        ) {
            val statusDotColor = when (syncStatus) {
                CloudSyncStatus.SYNCED -> neu.success
                CloudSyncStatus.BACKING_UP -> MaterialTheme.colorScheme.primary
                CloudSyncStatus.WAITING_FOR_WIFI,
                CloudSyncStatus.WAITING_FOR_IDLE,
                CloudSyncStatus.OFFLINE -> neu.warning
                CloudSyncStatus.BACKUP_FAILED,
                CloudSyncStatus.AUTH_REQUIRED -> {
                    if (userProfile == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.error
                }
            }
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .testTag("drawer_account_card")
                        .neuRaised(
                            cornerRadius = 18.dp,
                            backgroundColor = neu.surface,
                            shadowOffset = 4.dp,
                            blurRadius = 9.dp
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onAccountCardClick() }
                        .padding(12.dp)
                ) {
                    if (!userProfile?.photoUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .neuInset(
                                    cornerRadius = 12.dp,
                                    backgroundColor = neu.surfaceRecessed,
                                    shadowOffset = 2.dp,
                                    blurRadius = 4.dp
                                )
                                .padding(2.dp)
                        ) {
                            AsyncImage(
                                model = userProfile?.photoUrl,
                                contentDescription = "Google Profile Picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    } else {
                        AuraIconContainer(
                            imageVector = AuraIcons.UserRound,
                            contentDescription = null,
                            containerSize = 38.dp,
                            iconSize = AuraIconSize.Button,
                            cornerRadius = 12.dp,
                            tint = MaterialTheme.colorScheme.primary,
                            recessed = true
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile?.displayName ?: "Local Workspace",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = userProfile?.email?.takeIf { it.isNotBlank() }
                                ?: "Not signed in · Tap to connect",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                }

                Spacer(Modifier.height(14.dp))
                AuraNeuDivider()
                Spacer(Modifier.height(10.dp))
            }
        }

        // Scrollable Expandable Categories
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                val isExpanded = expandedStates[category.id] ?: true
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 0f else -90f,
                    animationSpec = AppleFluidSprings.adaptiveSpec(),
                    label = "cat_chevron_${category.id}"
                )

                if (!isCollapsed) {
                    // Category Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                expandedStates[category.id] = !isExpanded
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AuraIcon(
                            imageVector = AuraIcons.ChevronDown,
                            contentDescription = if (isExpanded) "Collapse ${category.title}" else "Expand ${category.title}",
                            size = 15.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = if (a11y.reducedMotion) 0f else chevronRotation
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded || isCollapsed,
                    enter = if (a11y.reducedMotion) fadeIn(tween(120)) else expandVertically(AppleFluidSprings.criticallyDamped()) + fadeIn(tween(180)),
                    exit = if (a11y.reducedMotion) fadeOut(tween(120)) else shrinkVertically(AppleFluidSprings.criticallyDamped()) + fadeOut(tween(150))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        category.items.forEach { item ->
                            DrawerNavItemRow(
                                item = item,
                                isCollapsed = isCollapsed
                            )
                        }
                    }
                }

                if (!isCollapsed) {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun DrawerNavItemRow(
    item: DrawerNavItem,
    isCollapsed: Boolean
) {
    val neu = LocalAuraNeumorphicPalette.current
    val haptics = LocalHapticFeedback.current
    val activeColor = item.accentTint ?: MaterialTheme.colorScheme.primary

    val containerModifier = if (item.isSelected) {
        Modifier.neuInset(
            cornerRadius = 15.dp,
            backgroundColor = neu.surfaceRecessed,
            shadowOffset = 3.dp,
            blurRadius = 6.dp,
            focusBorderColor = activeColor.copy(alpha = 0.28f)
        )
    } else {
        Modifier.clip(RoundedCornerShape(15.dp))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp)
            .then(containerModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                item.onClick()
            }
            .padding(horizontal = if (isCollapsed) 8.dp else 10.dp, vertical = 7.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (item.isSelected) {
                        Modifier.neuRaised(
                            cornerRadius = 10.dp,
                            backgroundColor = activeColor,
                            shadowOffset = 2.5.dp,
                            blurRadius = 5.dp
                        )
                    } else {
                        Modifier.neuRaised(
                            cornerRadius = 10.dp,
                            backgroundColor = neu.surface,
                            shadowOffset = 2.5.dp,
                            blurRadius = 5.dp
                        )
                    }
                )
        ) {
            if (item.dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(if (item.isSelected) Color.White else item.dotColor)
                )
            } else {
                AuraIcon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    size = 17.dp,
                    tint = if (item.isSelected) Color.White else activeColor.copy(alpha = 0.85f)
                )
            }
        }

        if (!isCollapsed) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (item.isSelected) activeColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (item.badgeCount != null && item.badgeCount > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .neuRaised(
                            cornerRadius = 9.dp,
                            backgroundColor = neu.surfaceElevated,
                            shadowOffset = 2.dp,
                            blurRadius = 4.dp
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
