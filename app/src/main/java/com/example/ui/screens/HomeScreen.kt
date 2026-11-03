package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CategoryEntity
import com.example.data.local.NoteEntity
import com.example.data.local.NoteType
import com.example.ui.components.AuraGlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AuraNotes",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "OFFLINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "${notes.size} note${if (notes.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onOpenCommandPalette) {
                    Icon(Icons.Default.Terminal, contentDescription = "Command Palette", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(
                        if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                        contentDescription = "Toggle Grid/List View"
                    )
                }
                if (selectedFilter == "TRASH" && notes.isNotEmpty()) {
                    IconButton(onClick = onEmptyTrash) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Empty Trash", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search notes, code, tags...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true
        )

        // Filter Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "ALL" to "All Notes",
                "PINNED" to "Pinned",
                "FAVORITES" to "Favorites",
                "CODE" to "Code Studio",
                "TASKS" to "Checklists",
                "ARCHIVE" to "Archived",
                "TRASH" to "Trash"
            )

            items(filters) { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { onSelectFilter(key) },
                    label = { Text(label) },
                    leadingIcon = {
                        val icon = when (key) {
                            "PINNED" -> Icons.Default.PushPin
                            "FAVORITES" -> Icons.Default.Favorite
                            "CODE" -> Icons.Default.Code
                            "TASKS" -> Icons.Default.Checklist
                            "ARCHIVE" -> Icons.Default.Archive
                            "TRASH" -> Icons.Default.Delete
                            else -> Icons.Default.Notes
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }

        // Categories Row
        if (selectedFilter != "TRASH" && selectedFilter != "ARCHIVE") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AssistChip(
                        onClick = { onSelectCategory(null) },
                        label = { Text("All Categories") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selectedCategory == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                        )
                    )
                }

                items(categories) { cat ->
                    val isCatSelected = selectedCategory == cat.id
                    val color = try { Color(android.graphics.Color.parseColor(cat.colorHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                    AssistChip(
                        onClick = { onSelectCategory(if (isCatSelected) null else cat.id) },
                        label = { Text(cat.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isCatSelected) color.copy(alpha = 0.2f) else Color.Transparent
                        )
                    )
                }
            }
        }

        // Notes List / Grid
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        when (selectedFilter) {
                            "TRASH" -> Icons.Default.DeleteOutline
                            "ARCHIVE" -> Icons.Default.Archive
                            "CODE" -> Icons.Default.Code
                            else -> Icons.Default.NoteAdd
                        },
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Text(
                        text = when (selectedFilter) {
                            "TRASH" -> "Trash is empty"
                            "ARCHIVE" -> "No archived notes"
                            "CODE" -> "No code files yet"
                            else -> if (searchQuery.isNotEmpty()) "No notes match \"$searchQuery\"" else "Your vault is empty"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Try adjusting your query or filters" else "Create a new note or code snippet to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
    }
}

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
    val formattedDate = remember(note.updatedAt) {
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(note.updatedAt))
    }

    val categoryColor = remember(category) {
        category?.let {
            try { Color(android.graphics.Color.parseColor(it.colorHex)) } catch (_: Exception) { null }
        } ?: Color(0xFF6366F1)
    }

    AuraGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        borderColor = if (note.isPinned) categoryColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Category badge, Code badge, Pin, Favorite, More
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
                            color = categoryColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = categoryColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (note.noteType == NoteType.CODE.name) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "<${note.language.uppercase()}>",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (note.noteType == NoteType.CHECKLIST.name) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "CHECKLIST",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isInTrash) {
                        IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin",
                                tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (note.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (note.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (isInTrash) {
                                DropdownMenuItem(
                                    text = { Text("Restore Note") },
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
                                    text = { Text(if (note.isPinned) "Unpin Note" else "Pin Note") },
                                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onTogglePin()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onDuplicate()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (note.isArchived) "Unarchive" else "Archive") },
                                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onToggleArchive()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Move to Trash", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
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
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Content preview snippet
            if (note.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note.content.replace("#", "").replace("*", "").take(160),
                    style = if (note.noteType == NoteType.CODE.name) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Footer: Tags & Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (note.tags.isNotBlank()) {
                    Text(
                        text = note.tags.split(",").joinToString(" ") { "#${it.trim()}" },
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
