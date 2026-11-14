package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CategoryEntity
import com.example.data.local.NoteEntity
import com.example.data.local.NoteType
import com.example.ui.components.AppleFluidSprings
import com.example.ui.components.AuraGlassCard
import com.example.ui.components.AuraScrollEdgeMask
import com.example.ui.components.appleProjectMomentum
import com.example.ui.components.appleRubberBand
import com.example.ui.components.appleSpringClickable
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
    selectedFilter: String, // "ALL", "PINNED", "FAVORITES", "CODE", "TASKS", "ARCHIVE", "TRASH"
    onSelectFilter: (String) -> Unit,
    onNoteClick: (NoteEntity) -> Unit,
    onCreateNote: (String) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onToggleFavorite: (NoteEntity) -> Unit,
    onToggleArchive: (NoteEntity) -> Unit,
    onMoveToTrash: (NoteEntity) -> Unit,
    onRestoreFromTrash: (NoteEntity) -> Unit,
    onDeletePermanently: (NoteEntity) -> Unit,
    onDuplicateNote: (NoteEntity) -> Unit,
    onEmptyTrash: () -> Unit,
    onOpenCommandPalette: () -> Unit
) {
    var isGridView by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val isWideScreen = maxWidth >= 640.dp
        val useGrid = isGridView || isWideScreen

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Translucent Header Bar with Optical Display Typography
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (selectedFilter) {
                                "PINNED" -> "Pinned"
                                "FAVORITES" -> "Favorites"
                                "CODE" -> "Code Studio"
                                "TASKS" -> "Checklists"
                                "ARCHIVE" -> "Archive"
                                "TRASH" -> "Trash"
                                else -> "AuraNotes"
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF30D158))
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "OFFLINE VAULT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    val activeCatName = categories.find { it.id == selectedCategory }?.name
                    Text(
                        text = buildString {
                            append("${notes.size} item${if (notes.size == 1) "" else "s"}")
                            if (activeCatName != null) append(" in $activeCatName")
                            append(" · Swipe card to pin or archive")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Header Action Pills with Immediate Pointer-Down Response
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .testTag("command_palette_button")
                            .appleSpringClickable(
                                pressedScale = 0.92f,
                                enableHaptics = true,
                                onClick = onOpenCommandPalette
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = "Open Command Center",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .testTag("toggle_layout_button")
                            .appleSpringClickable(
                                pressedScale = 0.92f,
                                enableHaptics = true,
                                onClick = { isGridView = !isGridView }
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (useGrid) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                contentDescription = "Toggle Grid or List Layout",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (selectedFilter == "TRASH" && notes.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .appleSpringClickable(
                                    pressedScale = 0.92f,
                                    enableHaptics = true,
                                    onClick = onEmptyTrash
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "Empty Trash",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Apple Spotlight-Style Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "Search titles, rich notes, code, or #tags...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Cancel, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .testTag("home_search_input"),
                singleLine = true
            )

            // Segmented Filter Pills Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "ALL" to "Library",
                    "PINNED" to "Pinned",
                    "FAVORITES" to "Favorites",
                    "CODE" to "Code Studio",
                    "TASKS" to "Checklists",
                    "ARCHIVE" to "Archive",
                    "TRASH" to "Trash"
                )

                items(filters, key = { it.first }) { (key, label) ->
                    val isSelected = selectedFilter == key
                    val icon = when (key) {
                        "PINNED" -> Icons.Default.PushPin
                        "FAVORITES" -> Icons.Default.Favorite
                        "CODE" -> Icons.Default.Code
                        "TASKS" -> Icons.Default.Checklist
                        "ARCHIVE" -> Icons.Default.Archive
                        "TRASH" -> Icons.Default.Delete
                        else -> Icons.Default.FolderCopy
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .appleSpringClickable(
                                pressedScale = 0.94f,
                                enableHaptics = true,
                                onClick = { onSelectFilter(key) }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Categories Row
            if (selectedFilter != "TRASH" && selectedFilter != "ARCHIVE") {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val isAll = selectedCategory == null
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isAll) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .appleSpringClickable(
                                    pressedScale = 0.94f,
                                    enableHaptics = true,
                                    onClick = { onSelectCategory(null) }
                                )
                        ) {
                            Text(
                                text = "All Spaces",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAll) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    items(categories, key = { it.id }) { cat ->
                        val isCatSelected = selectedCategory == cat.id
                        val color = try {
                            Color(android.graphics.Color.parseColor(cat.colorHex))
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isCatSelected) color.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isCatSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .appleSpringClickable(
                                    pressedScale = 0.94f,
                                    enableHaptics = true,
                                    onClick = { onSelectCategory(if (isCatSelected) null else cat.id) }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCatSelected) color else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Content Area with Scroll Edge Mask
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
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(76.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    when (selectedFilter) {
                                        "TRASH" -> Icons.Default.DeleteOutline
                                        "ARCHIVE" -> Icons.Default.Archive
                                        "CODE" -> Icons.Default.Code
                                        else -> Icons.Default.AutoAwesome
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = when (selectedFilter) {
                                "TRASH" -> "Trash is Empty"
                                "ARCHIVE" -> "No Archived Notes"
                                "CODE" -> "No Code Studio Files Yet"
                                else -> if (searchQuery.isNotEmpty()) "No Matches Found" else "Your Workspace is Ready"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "No notes or code matched \"$searchQuery\". Try clearing filters."
                            } else {
                                "Capture rich markdown notes, voice recordings, or executable Python & Web code."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedFilter != "TRASH" && selectedFilter != "ARCHIVE") {
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onCreateNote(if (selectedFilter == "CODE") NoteType.CODE.name else NoteType.RICH_TEXT.name) },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (selectedFilter == "CODE") "New Code Script" else "Create Rich Note")
                                }
                            }
                        }
                    }
                } else if (useGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 270.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            val category = categories.find { it.id == note.categoryId }
                            NoteCardItem(
                                note = note,
                                category = category,
                                isInTrash = selectedFilter == "TRASH",
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            val category = categories.find { it.id == note.categoryId }
                            NoteCardItem(
                                note = note,
                                category = category,
                                isInTrash = selectedFilter == "TRASH",
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

                // Subtle Scroll Edge Mask at the top of the list (§12)
                AuraScrollEdgeMask(
                    modifier = Modifier.align(Alignment.TopCenter),
                    fromTop = true,
                    height = 10.dp
                )
            }
        }
    }
}

/**
 * Apple Fluid Note Card with:
 * - 1:1 Direct Manipulation horizontal swipe gesture (right = Pin/Unpin, left = Archive/Trash)
 * - Apple rubber-banding progressive resistance past action threshold
 * - Momentum projection & velocity handoff on release
 * - Full mid-flight interruptibility
 * - Immediate pointer-down scale feedback
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCardItem(
    note: NoteEntity,
    category: CategoryEntity?,
    isInTrash: Boolean,
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

    // 1:1 Direct Manipulation horizontal offset
    val offsetX = remember { Animatable(0f) }
    val actionThresholdPx = with(density) { 88.dp.toPx() }
    val maxRubberRangePx = with(density) { 180.dp.toPx() }
    var thresholdCrossedHapticFired by remember { mutableStateOf(false) }

    val formattedDate = remember(note.updatedAt) {
        SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(note.updatedAt))
    }

    val categoryColor = remember(category) {
        category?.let {
            try {
                Color(android.graphics.Color.parseColor(it.colorHex))
            } catch (_: Exception) {
                null
            }
        } ?: Color(0xFF5E5CE6)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Background Reveal Layer during 1:1 Direct Manipulation Drag
        if (abs(offsetX.value) > 4f) {
            val isDraggingRight = offsetX.value > 0f
            val progress = (abs(offsetX.value) / actionThresholdPx).coerceIn(0f, 1f)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isDraggingRight) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f + 0.35f * progress)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f + 0.35f * progress)
                },
                modifier = Modifier.matchParentSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = if (isDraggingRight) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDraggingRight) {
                            if (isInTrash) Icons.Default.Restore else Icons.Default.PushPin
                        } else {
                            if (isInTrash) Icons.Default.DeleteForever else Icons.Default.DeleteOutline
                        },
                        contentDescription = null,
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

        // Foreground Translucent Material Card
        AuraGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(note.id, isInTrash) {
                    val velocityTracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            thresholdCrossedHapticFired = false
                            coroutineScope.launch { offsetX.stop() } // Interruptible mid-flight!
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val rawNext = offsetX.value + dragAmount
                            // Apply Apple rubber-banding resistance past threshold
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

                            coroutineScope.launch {
                                offsetX.snapTo(damped)
                            }
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
            borderColor = if (note.isPinned) {
                categoryColor.copy(alpha = 0.68f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)
            },
            onClick = onClick,
            onLongClick = { menuExpanded = true }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row: Category badge, Code/Task pill, Pin, Favorite, Overflow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (category != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = categoryColor.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(categoryColor)
                                    )
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = categoryColor
                                    )
                                }
                            }
                        }

                        if (note.noteType == NoteType.CODE.name) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "<${note.language.uppercase()}>",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        } else if (note.noteType == NoteType.CHECKLIST.name) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "CHECKLIST",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isInTrash) {
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTogglePin()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = if (note.isPinned) "Unpin note" else "Pin note",
                                    tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleFavorite()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (note.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (note.isFavorite) "Remove favorite" else "Favorite note",
                                    tint = if (note.isFavorite) Color(0xFFFF375F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    contentDescription = "Note actions",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                if (isInTrash) {
                                    DropdownMenuItem(
                                        text = { Text("Restore to Library") },
                                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onRestore()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuExpanded = false
                                            onDeletePermanently()
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(if (note.isPinned) "Unpin Note" else "Pin to Top") },
                                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onTogglePin()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate Note") },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onDuplicate()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (note.isArchived) "Unarchive" else "Move to Archive") },
                                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onToggleArchive()
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Move to Trash", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
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

                Spacer(Modifier.height(8.dp))

                // Title
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Content preview snippet
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

                Spacer(Modifier.height(12.dp))

                // Footer: Tags & Timestamp
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
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}
