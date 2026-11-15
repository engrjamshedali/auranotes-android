package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.data.local.CloudSyncStatus
import com.example.data.local.NoteEntity
import com.example.data.local.NoteType
import com.example.ui.components.AppleFluidSprings
import com.example.ui.components.AuraGlassCard
import com.example.ui.components.AuraIcon
import com.example.ui.components.AuraIconContainer
import com.example.ui.components.AuraIconSize
import com.example.ui.components.AuraIcons
import com.example.ui.components.AuraNeuButton
import com.example.ui.components.AuraNeuIconButton
import com.example.ui.components.AuraNeuTextField
import com.example.ui.components.AuraScrollEdgeMask
import com.example.ui.components.appleProjectMomentum
import com.example.ui.components.appleRubberBand
import com.example.ui.components.neuInset
import com.example.ui.theme.LocalAuraAccessibility
import com.example.ui.theme.LocalAuraNeumorphicPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    notes: List<NoteEntity>,
    categories: List<CategoryEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    isGridView: Boolean = false,
    onToggleGridView: () -> Unit = {},
    syncStatus: CloudSyncStatus = CloudSyncStatus.OFFLINE,
    isAuthenticated: Boolean = false,
    driveBackupEnabled: Boolean = false,
    onStatusPillClick: () -> Unit = {},
    showDrawerButton: Boolean = true,
    onOpenDrawer: () -> Unit = {},
    onNoteClick: (NoteEntity) -> Unit,
    onCreateNote: (String) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onToggleFavorite: (NoteEntity) -> Unit,
    onToggleArchive: (NoteEntity) -> Unit,
    onMoveToTrash: (NoteEntity) -> Unit,
    onRestoreFromTrash: (NoteEntity) -> Unit,
    onDeletePermanently: (NoteEntity) -> Unit,
    onDuplicateNote: (NoteEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    val neu = LocalAuraNeumorphicPalette.current
    val a11y = LocalAuraAccessibility.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val isCompactWidth = maxWidth < 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Neumorphic Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (showDrawerButton) {
                        AuraNeuIconButton(
                            onClick = onOpenDrawer,
                            size = 48.dp,
                            cornerRadius = 15.dp,
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            AuraIcon(
                                AuraIcons.Menu,
                                contentDescription = "Open Navigation Drawer",
                                size = AuraIconSize.Toolbar
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }

                    Column {
                        val activeCatName = categories.find { it.id == selectedCategory }?.name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (selectedFilter) {
                                    "PINNED" -> "Pinned"
                                    "FAVORITES" -> "Favorites"
                                    "CODE" -> "Code Studio"
                                    "TASKS" -> "Checklists"
                                    "ARCHIVE" -> "Archive"
                                    "TRASH" -> "Trash"
                                    else -> activeCatName ?: "AuraNotes"
                                },
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(10.dp))
                            val (statusLabel, statusColor) = when {
                                !isAuthenticated -> "Offline" to neu.success
                                !driveBackupEnabled -> "Local Vault" to MaterialTheme.colorScheme.primary
                                syncStatus == CloudSyncStatus.SYNCED -> "Synced" to neu.success
                                syncStatus == CloudSyncStatus.BACKING_UP -> "Backing up" to MaterialTheme.colorScheme.primary
                                syncStatus == CloudSyncStatus.WAITING_FOR_WIFI -> "Wi-Fi wait" to neu.warning
                                syncStatus == CloudSyncStatus.WAITING_FOR_IDLE -> "Idle wait" to neu.warning
                                syncStatus == CloudSyncStatus.OFFLINE -> "Offline" to neu.warning
                                syncStatus == CloudSyncStatus.BACKUP_FAILED -> "Sync error" to MaterialTheme.colorScheme.error
                                syncStatus == CloudSyncStatus.AUTH_REQUIRED -> "Auth needed" to MaterialTheme.colorScheme.error
                                else -> "Offline" to neu.warning
                            }
                            Box(
                                modifier = Modifier
                                    .testTag("home_sync_status_pill")
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onStatusPillClick() }
                                    .neuInset(
                                        cornerRadius = 10.dp,
                                        backgroundColor = neu.surfaceRecessed,
                                        shadowOffset = 2.dp,
                                        blurRadius = 4.dp
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = buildString {
                                append("${notes.size} note${if (notes.size == 1) "" else "s"}")
                                if (activeCatName != null && selectedFilter != "ALL") append(" · $activeCatName")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Header Action Controls (Clean Layout Toggle & Conditional Trash Clear)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuraNeuIconButton(
                        onClick = onToggleGridView,
                        size = 48.dp,
                        cornerRadius = 15.dp,
                        isActive = isGridView,
                        modifier = Modifier.testTag("toggle_layout_button")
                    ) {
                        AuraIcon(
                            imageVector = if (isGridView) AuraIcons.ListView else AuraIcons.LayoutGrid,
                            contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                            size = AuraIconSize.Toolbar
                        )
                    }

                    if (selectedFilter == "TRASH" && notes.isNotEmpty()) {
                        AuraNeuIconButton(
                            onClick = onEmptyTrash,
                            size = 48.dp,
                            cornerRadius = 15.dp,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("empty_trash_button")
                        ) {
                            AuraIcon(
                                AuraIcons.Eraser,
                                contentDescription = "Empty Trash",
                                size = AuraIconSize.Toolbar
                            )
                        }
                    }
                }
            }

            // Recessed Neumorphic Search Well
            AuraNeuTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Search notes, code, or tags...",
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
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            AuraIcon(AuraIcons.X, contentDescription = "Clear search", size = AuraIconSize.Small)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("home_search_input")
            )

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (notes.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AuraIconContainer(
                            imageVector = when (selectedFilter) {
                                "TRASH" -> AuraIcons.Trash2
                                "ARCHIVE" -> AuraIcons.Archive
                                "CODE" -> AuraIcons.Code2
                                else -> AuraIcons.Sparkles
                            },
                            containerSize = 84.dp,
                            iconSize = 36.dp,
                            cornerRadius = 24.dp
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = when (selectedFilter) {
                                "TRASH" -> "Trash is Empty"
                                "ARCHIVE" -> "No Archived Notes"
                                "CODE" -> "No Code Files Yet"
                                else -> if (searchQuery.isNotEmpty()) "No Matches Found" else "Workspace Ready"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "Nothing matched \"$searchQuery\"."
                            } else {
                                "Create a rich note, checklist, or local script."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedFilter != "TRASH" && selectedFilter != "ARCHIVE") {
                            Spacer(Modifier.height(22.dp))
                            AuraNeuButton(
                                onClick = {
                                    onCreateNote(if (selectedFilter == "CODE") NoteType.CODE.name else NoteType.RICH_TEXT.name)
                                },
                                isAccent = true
                            ) {
                                AuraIcon(AuraIcons.Plus, contentDescription = null, size = AuraIconSize.Button, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (selectedFilter == "CODE") "New Code Script" else "Create Rich Note",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = isGridView,
                        transitionSpec = {
                            if (a11y.reducedMotion) {
                                fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                            } else {
                                (fadeIn(tween(180)) + scaleIn(initialScale = 0.98f, animationSpec = tween(180))) togetherWith
                                    fadeOut(tween(140))
                            }
                        },
                        label = "notes_layout_switch"
                    ) { gridEnabled ->
                        if (gridEnabled) {
                            val horizontalPad = if (isCompactWidth) 16.dp else 20.dp
                            val spacing = if (isCompactWidth) 12.dp else 16.dp
                            LazyVerticalGrid(
                                columns = if (isCompactWidth) GridCells.Fixed(2) else GridCells.Adaptive(minSize = 260.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("notes_grid_view"),
                                contentPadding = PaddingValues(start = horizontalPad, end = horizontalPad, top = 12.dp, bottom = 104.dp),
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                                verticalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                items(notes, key = { it.id }) { note ->
                                    val category = categories.find { it.id == note.categoryId }
                                    NoteCardItem(
                                        note = note,
                                        category = category,
                                        isInTrash = selectedFilter == "TRASH",
                                        isCompactGrid = isCompactWidth,
                                        onClick = { onNoteClick(note) },
                                        onTogglePin = { onTogglePin(note) },
                                        onToggleFavorite = { onToggleFavorite(note) },
                                        onToggleArchive = { onToggleArchive(note) },
                                        onMoveToTrash = { onMoveToTrash(note) },
                                        onRestore = { onRestoreFromTrash(note) },
                                        onDeletePermanently = { onDeletePermanently(note) },
                                        onDuplicate = { onDuplicateNote(note) }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("notes_list_view"),
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(notes, key = { it.id }) { note ->
                                    val category = categories.find { it.id == note.categoryId }
                                    NoteCardItem(
                                        note = note,
                                        category = category,
                                        isInTrash = selectedFilter == "TRASH",
                                        isCompactGrid = false,
                                        onClick = { onNoteClick(note) },
                                        onTogglePin = { onTogglePin(note) },
                                        onToggleFavorite = { onToggleFavorite(note) },
                                        onToggleArchive = { onToggleArchive(note) },
                                        onMoveToTrash = { onMoveToTrash(note) },
                                        onRestore = { onRestoreFromTrash(note) },
                                        onDeletePermanently = { onDeletePermanently(note) },
                                        onDuplicate = { onDuplicateNote(note) }
                                    )
                                }
                            }
                        }
                    }
                }

                AuraScrollEdgeMask(
                    modifier = Modifier.align(Alignment.TopCenter),
                    fromTop = true,
                    height = 10.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCardItem(
    note: NoteEntity,
    category: CategoryEntity?,
    isInTrash: Boolean,
    isCompactGrid: Boolean = false,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    onDuplicate: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val neu = LocalAuraNeumorphicPalette.current

    val offsetX = remember { Animatable(0f) }
    val actionThresholdPx = with(density) { 88.dp.toPx() }
    val maxRubberRangePx = with(density) { 180.dp.toPx() }
    var thresholdCrossedHapticFired by remember { mutableStateOf(false) }

    val formattedDate = remember(note.updatedAt, isCompactGrid) {
        val pattern = if (isCompactGrid) "MMM d" else "MMM d · HH:mm"
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(note.updatedAt))
    }

    val categoryColor = remember(category) {
        category?.let {
            try {
                Color(android.graphics.Color.parseColor(it.colorHex))
            } catch (_: Exception) {
                null
            }
        } ?: Color(0xFF6366F1)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Background Reveal Layer during 1:1 Swipe Gesture
        if (abs(offsetX.value) > 4f) {
            val isDraggingRight = offsetX.value > 0f
            val progress = (abs(offsetX.value) / actionThresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .neuInset(
                        cornerRadius = 22.dp,
                        backgroundColor = if (isDraggingRight) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f + 0.18f * progress)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.14f + 0.18f * progress)
                        },
                        shadowOffset = 3.dp,
                        blurRadius = 6.dp
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = if (isDraggingRight) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AuraIcon(
                        imageVector = if (isDraggingRight) {
                            if (isInTrash) AuraIcons.RotateCcw else AuraIcons.Pin
                        } else {
                            AuraIcons.Trash2
                        },
                        contentDescription = null,
                        size = AuraIconSize.Toolbar,
                        tint = if (isDraggingRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isDraggingRight) {
                            if (isInTrash) "Restore" else if (note.isPinned) "Unpin" else "Pin"
                        } else {
                            if (isInTrash) "Delete" else "Trash"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isDraggingRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Raised Soft UI Note Card
        AuraGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(note.id, isInTrash) {
                    val velocityTracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            thresholdCrossedHapticFired = false
                            coroutineScope.launch { offsetX.stop() }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val rawNext = offsetX.value + dragAmount
                            val damped = if (abs(rawNext) <= actionThresholdPx) {
                                rawNext
                            } else {
                                val sign = if (rawNext >= 0f) 1f else -1f
                                val overshoot = abs(rawNext) - actionThresholdPx
                                sign * (actionThresholdPx + appleRubberBand(overshoot, maxRubberRangePx))
                            }

                            if (abs(damped) >= actionThresholdPx && !thresholdCrossedHapticFired) {
                                thresholdCrossedHapticFired = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (abs(damped) < actionThresholdPx) {
                                thresholdCrossedHapticFired = false
                            }

                            coroutineScope.launch { offsetX.snapTo(damped) }
                        },
                        onDragEnd = {
                            val releaseVelocity = velocityTracker.calculateVelocity().x
                            val projected = offsetX.value + appleProjectMomentum(releaseVelocity, 0.992f)
                            if (projected >= actionThresholdPx) {
                                if (isInTrash) onRestore() else onTogglePin()
                            } else if (projected <= -actionThresholdPx) {
                                if (isInTrash) onDeletePermanently() else onMoveToTrash()
                            }
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = AppleFluidSprings.momentum(),
                                    initialVelocity = releaseVelocity
                                )
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, AppleFluidSprings.criticallyDamped())
                            }
                        }
                    )
                },
            borderColor = if (note.isPinned) categoryColor else Color.Transparent,
            onClick = onClick,
            onLongClick = { menuExpanded = true }
        ) {
            Column(modifier = Modifier.padding(if (isCompactGrid) 14.dp else 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (category != null) {
                            Box(
                                modifier = Modifier
                                    .neuInset(
                                        cornerRadius = 9.dp,
                                        backgroundColor = neu.surfaceRecessed,
                                        shadowOffset = 2.dp,
                                        blurRadius = 4.dp
                                    )
                                    .padding(horizontal = if (isCompactGrid) 7.dp else 9.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(categoryColor)
                                    )
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = categoryColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (!isCompactGrid || category == null) {
                            if (note.noteType == NoteType.CODE.name) {
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
                                        text = note.language.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1
                                    )
                                }
                            } else if (note.noteType == NoteType.CHECKLIST.name) {
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
                                        text = if (isCompactGrid) "TASKS" else "CHECKLIST",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactGrid) 2.dp else 0.dp)
                    ) {
                        if (!isInTrash) {
                            if (isCompactGrid) {
                                if (note.isPinned) {
                                    AuraIcon(
                                        AuraIcons.Pin,
                                        contentDescription = "Pinned",
                                        tint = MaterialTheme.colorScheme.primary,
                                        size = 13.dp
                                    )
                                }
                                if (note.isFavorite) {
                                    AuraIcon(
                                        AuraIcons.Heart,
                                        contentDescription = "Favorite",
                                        tint = Color(0xFFF43F5E),
                                        size = 13.dp
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTogglePin()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    AuraIcon(
                                        AuraIcons.Pin,
                                        contentDescription = if (note.isPinned) "Unpin note" else "Pin note",
                                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        size = AuraIconSize.Small
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleFavorite()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    AuraIcon(
                                        AuraIcons.Heart,
                                        contentDescription = if (note.isFavorite) "Remove favorite" else "Favorite note",
                                        tint = if (note.isFavorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        size = AuraIconSize.Small
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(if (isCompactGrid) 30.dp else 34.dp)
                            ) {
                                AuraIcon(
                                    AuraIcons.Ellipsis,
                                    contentDescription = "Note actions",
                                    size = AuraIconSize.Button,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                containerColor = neu.surfaceElevated
                            ) {
                                if (isInTrash) {
                                    DropdownMenuItem(
                                        text = { Text("Restore to Library") },
                                        leadingIcon = { AuraIcon(AuraIcons.RotateCcw, contentDescription = null, size = AuraIconSize.Button) },
                                        onClick = {
                                            menuExpanded = false
                                            onRestore()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { AuraIcon(AuraIcons.Trash2, contentDescription = null, size = AuraIconSize.Button, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuExpanded = false
                                            onDeletePermanently()
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(if (note.isPinned) "Unpin Note" else "Pin to Top") },
                                        leadingIcon = { AuraIcon(AuraIcons.Pin, contentDescription = null, size = AuraIconSize.Button) },
                                        onClick = {
                                            menuExpanded = false
                                            onTogglePin()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (note.isFavorite) "Remove Favorite" else "Mark Favorite") },
                                        leadingIcon = {
                                            AuraIcon(
                                                AuraIcons.Heart,
                                                contentDescription = null,
                                                size = AuraIconSize.Button,
                                                tint = if (note.isFavorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onToggleFavorite()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate Note") },
                                        leadingIcon = { AuraIcon(AuraIcons.Copy, contentDescription = null, size = AuraIconSize.Button) },
                                        onClick = {
                                            menuExpanded = false
                                            onDuplicate()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (note.isArchived) "Unarchive" else "Move to Archive") },
                                        leadingIcon = { AuraIcon(AuraIcons.Archive, contentDescription = null, size = AuraIconSize.Button) },
                                        onClick = {
                                            menuExpanded = false
                                            onToggleArchive()
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Move to Trash", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { AuraIcon(AuraIcons.Trash2, contentDescription = null, size = AuraIconSize.Button, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuExpanded = false
                                            onMoveToTrash()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(if (isCompactGrid) 6.dp else 8.dp))

                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isCompactGrid) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (note.content.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = note.content.replace("#", "").replace("*", "").trim().take(160),
                        style = if (note.noteType == NoteType.CODE.name) {
                            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(if (isCompactGrid) 10.dp else 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (note.tags.isNotBlank()) {
                        Text(
                            text = note.tags.split(",").filter { it.isNotBlank() }.joinToString(" ") { "#${it.trim()}" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
