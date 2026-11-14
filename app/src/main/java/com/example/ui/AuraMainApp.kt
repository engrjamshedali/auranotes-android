package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AuraDatabase
import com.example.data.local.NoteEntity
import com.example.data.local.NoteType
import com.example.data.local.PreferencesRepository
import com.example.domain.BackupManager
import com.example.domain.NoteRepository
import com.example.ui.components.AppleFluidSprings
import com.example.ui.components.AuraLiquidBackground
import com.example.ui.components.CommandPaletteDialog
import com.example.ui.components.PaletteAction
import com.example.ui.components.appleSpringClickable
import com.example.ui.screens.*
import com.example.ui.theme.AuraDarkSpecularEdge
import com.example.ui.theme.AuraDarkSpecularSubtle
import com.example.ui.theme.AuraLightSpecularEdge
import com.example.ui.theme.AuraLightSpecularSubtle
import com.example.ui.theme.AuraNotesTheme
import com.example.ui.theme.LocalAuraAccessibility
import java.util.UUID
import kotlinx.coroutines.launch

enum class AppDestination {
    HOME,
    RICH_TEXT_EDITOR,
    CODE_STUDIO,
    MEDIA_VAULT,
    BACKUP_CENTER,
    SETTINGS
}

private data class NavSection(
    val destination: AppDestination,
    val label: String,
    val icon: ImageVector
)

@Composable
fun AuraMainApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Initialize Database & Repositories
    val database = remember { AuraDatabase.getInstance(context) }
    val noteRepository = remember {
        NoteRepository(
            context = context,
            noteDao = database.noteDao(),
            categoryDao = database.categoryDao(),
            attachmentDao = database.attachmentDao(),
            backupDao = database.backupDao()
        )
    }
    val backupManager = remember {
        BackupManager(
            context = context,
            noteDao = database.noteDao(),
            categoryDao = database.categoryDao(),
            attachmentDao = database.attachmentDao(),
            backupDao = database.backupDao()
        )
    }
    val preferencesRepository = remember { PreferencesRepository(context) }

    // User preferences
    val preferences by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = com.example.data.local.AuraUserPreferences()
    )

    // Current navigation state
    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }
    var activeNoteId by remember { mutableStateOf<String?>(null) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }

    // Filters and search query for Home
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    // Flows
    val allActiveNotes by noteRepository.activeNotes.collectAsStateWithLifecycle(initialValue = emptyList())
    val archivedNotes by noteRepository.archivedNotes.collectAsStateWithLifecycle(initialValue = emptyList())
    val trashNotes by noteRepository.trashNotes.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by noteRepository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())
    val attachments by noteRepository.allAttachments.collectAsStateWithLifecycle(initialValue = emptyList())
    val backups by noteRepository.allBackups.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeNoteCount by noteRepository.activeNoteCount.collectAsStateWithLifecycle(initialValue = 0)

    // Filter notes based on user selection
    val displayedNotes = remember(
        allActiveNotes,
        archivedNotes,
        trashNotes,
        selectedFilter,
        selectedCategoryId,
        searchQuery
    ) {
        val baseList = when (selectedFilter) {
            "ARCHIVE" -> archivedNotes
            "TRASH" -> trashNotes
            "PINNED" -> allActiveNotes.filter { it.isPinned }
            "FAVORITES" -> allActiveNotes.filter { it.isFavorite }
            "CODE" -> allActiveNotes.filter { it.noteType == NoteType.CODE.name }
            "TASKS" -> allActiveNotes.filter { it.noteType == NoteType.CHECKLIST.name }
            else -> allActiveNotes
        }

        baseList.filter { note ->
            val matchCat = selectedCategoryId == null || note.categoryId == selectedCategoryId
            val matchQuery = searchQuery.isBlank() ||
                    note.title.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true) ||
                    note.tags.contains(searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    // Active note being edited
    val activeNote = remember(activeNoteId, allActiveNotes, archivedNotes, trashNotes) {
        val pool = allActiveNotes + archivedNotes + trashNotes
        pool.find { it.id == activeNoteId }
    }

    val activeNoteAttachments by remember(activeNoteId) {
        if (activeNoteId != null) {
            noteRepository.getAttachmentsForNote(activeNoteId!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Back handling
    BackHandler(enabled = currentDestination != AppDestination.HOME) {
        currentDestination = AppDestination.HOME
        activeNoteId = null
    }

    // Theme calculation
    val isDark = when (preferences.themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    fun openNewNote(type: NoteType, language: String = "python") {
        coroutineScope.launch {
            val newId = "note_" + UUID.randomUUID().toString().take(8)
            val now = System.currentTimeMillis()
            val newNote = NoteEntity(
                id = newId,
                title = if (type == NoteType.CODE) "script.$language" else "New Note",
                content = if (type == NoteType.CODE) "# Universal Code Studio\nprint('Hello from AuraNotes')\n" else "",
                noteType = type.name,
                language = language,
                categoryId = selectedCategoryId ?: "cat_personal",
                createdAt = now,
                updatedAt = now
            )
            noteRepository.saveNote(newNote)
            activeNoteId = newId
            currentDestination = if (type == NoteType.CODE) AppDestination.CODE_STUDIO else AppDestination.RICH_TEXT_EDITOR
        }
    }

    val navSections = remember {
        listOf(
            NavSection(AppDestination.HOME, "Library", Icons.Default.FolderCopy),
            NavSection(AppDestination.MEDIA_VAULT, "Media Vault", Icons.Default.PermMedia),
            NavSection(AppDestination.BACKUP_CENTER, "Backups", Icons.Default.CloudSync),
            NavSection(AppDestination.SETTINGS, "Settings", Icons.Default.Settings)
        )
    }

    // Command palette actions
    val commandActions = remember(categories) {
        listOf(
            PaletteAction(
                title = "New Rich Note",
                subtitle = "Create a formatted markdown document",
                icon = Icons.Default.EditNote,
                category = "Create"
            ) { openNewNote(NoteType.RICH_TEXT) },
            PaletteAction(
                title = "New Python Script",
                subtitle = "Run offline Python analytics & code",
                icon = Icons.Default.Code,
                category = "Code Studio"
            ) { openNewNote(NoteType.CODE, "python") },
            PaletteAction(
                title = "New HTML/JS Web Sandbox",
                subtitle = "Interactive live web preview & console",
                icon = Icons.Default.Language,
                category = "Code Studio"
            ) { openNewNote(NoteType.CODE, "html") },
            PaletteAction(
                title = "New SQLite Query",
                subtitle = "Run in-memory SQL tables & queries",
                icon = Icons.Default.Storage,
                category = "Code Studio"
            ) { openNewNote(NoteType.CODE, "sql") },
            PaletteAction(
                title = "New Checklist",
                subtitle = "Interactive task checklist",
                icon = Icons.Default.Checklist,
                category = "Create"
            ) { openNewNote(NoteType.CHECKLIST) },
            PaletteAction(
                title = "Open Media Vault",
                subtitle = "Browse photos, documents & voice notes",
                icon = Icons.Default.PermMedia,
                category = "Navigate"
            ) { currentDestination = AppDestination.MEDIA_VAULT },
            PaletteAction(
                title = "Create Portable Backup",
                subtitle = "Export SHA-256 verified .auranotes archive",
                icon = Icons.Default.CloudSync,
                category = "Backup"
            ) { currentDestination = AppDestination.BACKUP_CENTER },
            PaletteAction(
                title = "Open Settings & Accessibility",
                subtitle = "Themes, Reduce Motion, Transparency & Contrast",
                icon = Icons.Default.Settings,
                category = "Navigate"
            ) { currentDestination = AppDestination.SETTINGS }
        )
    }

    AuraNotesTheme(
        darkTheme = isDark,
        accentIndex = preferences.accentIndex,
        reducedMotion = preferences.reducedMotion,
        reducedTransparency = preferences.reducedTransparency,
        highContrast = preferences.highContrast
    ) {
        val a11y = LocalAuraAccessibility.current
        val isTopLevelScreen = currentDestination in listOf(
            AppDestination.HOME,
            AppDestination.MEDIA_VAULT,
            AppDestination.BACKUP_CENTER,
            AppDestination.SETTINGS
        )

        AuraLiquidBackground {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTabletOrWide = maxWidth >= 680.dp

                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    },
                    bottomBar = {
                        if (isTopLevelScreen && !isTabletOrWide) {
                            AuraTranslucentBottomBar(
                                sections = navSections,
                                currentDestination = currentDestination,
                                onSelectDestination = { currentDestination = it }
                            )
                        }
                    },
                    floatingActionButton = {
                        if (currentDestination == AppDestination.HOME) {
                            Box {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    shadowElevation = 10.dp,
                                    border = BorderStroke(
                                        1.2.dp,
                                        Brush.linearGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.65f),
                                                a11y.accentSecondary.copy(alpha = 0.5f),
                                                Color.White.copy(alpha = 0.15f)
                                            )
                                        )
                                    ),
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(a11y.accentPrimary, a11y.accentSecondary)
                                            )
                                        )
                                        .testTag("create_note_fab")
                                        .appleSpringClickable(
                                            pressedScale = 0.91f,
                                            enableHaptics = true,
                                            onClick = { showCreateMenu = true }
                                        )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Create New Note or Code File",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showCreateMenu,
                                    onDismissRequest = { showCreateMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rich Text Document", fontWeight = FontWeight.Medium) },
                                        leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            showCreateMenu = false
                                            openNewNote(NoteType.RICH_TEXT)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Code Studio Script", fontWeight = FontWeight.Medium) },
                                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                                        onClick = {
                                            showCreateMenu = false
                                            openNewNote(NoteType.CODE, "python")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Task Checklist", fontWeight = FontWeight.Medium) },
                                        leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                                        onClick = {
                                            showCreateMenu = false
                                            openNewNote(NoteType.CHECKLIST)
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // iPadOS / Tablet Translucent Navigation Sidebar Rail
                        if (isTopLevelScreen && isTabletOrWide) {
                            AuraTabletSidebarRail(
                                sections = navSections,
                                currentDestination = currentDestination,
                                onSelectDestination = { currentDestination = it },
                                onOpenCommandPalette = { showCommandPalette = true }
                            )
                        }

                        // Spatially Consistent, Interruptible Screen Router (§3 & §7)
                        AnimatedContent(
                            targetState = currentDestination,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            transitionSpec = {
                                if (a11y.reducedMotion) {
                                    // Reduced motion: gentle cross-fade without spatial slide
                                    fadeIn(tween(160)) togetherWith fadeOut(tween(160))
                                } else {
                                    val isOpeningDetail = targetState in listOf(AppDestination.RICH_TEXT_EDITOR, AppDestination.CODE_STUDIO)
                                    val isClosingDetail = initialState in listOf(AppDestination.RICH_TEXT_EDITOR, AppDestination.CODE_STUDIO)
                                    when {
                                        isOpeningDetail -> {
                                            (slideInHorizontally(
                                                animationSpec = AppleFluidSprings.criticallyDamped(),
                                                initialOffsetX = { fullWidth -> fullWidth / 4 }
                                            ) + fadeIn(tween(200))) togetherWith
                                                (slideOutHorizontally(
                                                    animationSpec = AppleFluidSprings.criticallyDamped(),
                                                    targetOffsetX = { fullWidth -> -fullWidth / 10 }
                                                ) + fadeOut(tween(180)))
                                        }
                                        isClosingDetail -> {
                                            // Exit along the exact symmetric path it entered (§7 Spatial Consistency)
                                            (slideInHorizontally(
                                                animationSpec = AppleFluidSprings.criticallyDamped(),
                                                initialOffsetX = { fullWidth -> -fullWidth / 10 }
                                            ) + fadeIn(tween(200))) togetherWith
                                                (slideOutHorizontally(
                                                    animationSpec = AppleFluidSprings.criticallyDamped(),
                                                    targetOffsetX = { fullWidth -> fullWidth / 4 }
                                                ) + fadeOut(tween(180)))
                                        }
                                        else -> {
                                            fadeIn(tween(180)) togetherWith fadeOut(tween(150))
                                        }
                                    }
                                }
                            },
                            label = "aura_screen_router"
                        ) { destination ->
                            when (destination) {
                                AppDestination.HOME -> {
                                    HomeScreen(
                                        notes = displayedNotes,
                                        categories = categories,
                                        searchQuery = searchQuery,
                                        onSearchQueryChange = { searchQuery = it },
                                        selectedCategory = selectedCategoryId,
                                        onSelectCategory = { selectedCategoryId = it },
                                        selectedFilter = selectedFilter,
                                        onSelectFilter = { selectedFilter = it },
                                        onNoteClick = { note ->
                                            activeNoteId = note.id
                                            currentDestination = if (note.noteType == NoteType.CODE.name) {
                                                AppDestination.CODE_STUDIO
                                            } else {
                                                AppDestination.RICH_TEXT_EDITOR
                                            }
                                        },
                                        onCreateNote = { typeStr ->
                                            val type = try { NoteType.valueOf(typeStr) } catch (_: Exception) { NoteType.RICH_TEXT }
                                            openNewNote(type)
                                        },
                                        onTogglePin = { note ->
                                            coroutineScope.launch { noteRepository.togglePin(note.id, note.isPinned) }
                                        },
                                        onToggleFavorite = { note ->
                                            coroutineScope.launch { noteRepository.toggleFavorite(note.id, note.isFavorite) }
                                        },
                                        onToggleArchive = { note ->
                                            coroutineScope.launch {
                                                val newState = !note.isArchived
                                                noteRepository.toggleArchive(note.id, note.isArchived)
                                                val res = snackbarHostState.showSnackbar(
                                                    message = if (newState) "Archived \"${note.title}\"" else "Unarchived \"${note.title}\"",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (res == SnackbarResult.ActionPerformed) {
                                                    noteRepository.toggleArchive(note.id, newState)
                                                }
                                            }
                                        },
                                        onMoveToTrash = { note ->
                                            coroutineScope.launch {
                                                noteRepository.moveToTrash(note.id)
                                                val res = snackbarHostState.showSnackbar(
                                                    message = "Moved \"${note.title.ifBlank { "Untitled" }}\" to Trash",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (res == SnackbarResult.ActionPerformed) {
                                                    noteRepository.restoreFromTrash(note.id)
                                                }
                                            }
                                        },
                                        onRestoreFromTrash = { note ->
                                            coroutineScope.launch {
                                                noteRepository.restoreFromTrash(note.id)
                                                snackbarHostState.showSnackbar("Restored to Library")
                                            }
                                        },
                                        onDeletePermanently = { note ->
                                            coroutineScope.launch { noteRepository.deletePermanently(note.id) }
                                        },
                                        onDuplicateNote = { note ->
                                            coroutineScope.launch {
                                                noteRepository.duplicateNote(note)
                                                snackbarHostState.showSnackbar("Duplicated \"${note.title}\"")
                                            }
                                        },
                                        onEmptyTrash = {
                                            coroutineScope.launch { noteRepository.emptyTrash() }
                                        },
                                        onOpenCommandPalette = { showCommandPalette = true }
                                    )
                                }

                                AppDestination.RICH_TEXT_EDITOR -> {
                                    if (activeNote != null) {
                                        RichTextEditorScreen(
                                            note = activeNote,
                                            categories = categories,
                                            attachments = activeNoteAttachments,
                                            onSaveNote = { updated ->
                                                coroutineScope.launch { noteRepository.saveNote(updated) }
                                            },
                                            onBack = {
                                                currentDestination = AppDestination.HOME
                                                activeNoteId = null
                                            },
                                            onAddAttachmentUri = { uri, name, mime ->
                                                coroutineScope.launch {
                                                    noteRepository.saveAttachmentFromUri(activeNote.id, uri, name, mime)
                                                }
                                            },
                                            onAddAudioFile = { file, durationMs ->
                                                coroutineScope.launch {
                                                    noteRepository.saveAudioAttachment(activeNote.id, file, durationMs)
                                                }
                                            },
                                            onDeleteAttachment = { attId ->
                                                coroutineScope.launch {
                                                    noteRepository.deleteAttachment(attId)
                                                }
                                            }
                                        )
                                    } else {
                                        LaunchedEffect(Unit) { currentDestination = AppDestination.HOME }
                                    }
                                }

                                AppDestination.CODE_STUDIO -> {
                                    if (activeNote != null) {
                                        CodeStudioScreen(
                                            note = activeNote,
                                            onSaveNote = { updated ->
                                                coroutineScope.launch { noteRepository.saveNote(updated) }
                                            },
                                            onBack = {
                                                currentDestination = AppDestination.HOME
                                                activeNoteId = null
                                            }
                                        )
                                    } else {
                                        LaunchedEffect(Unit) { currentDestination = AppDestination.HOME }
                                    }
                                }

                                AppDestination.MEDIA_VAULT -> {
                                    MediaVaultScreen(
                                        attachments = attachments,
                                        onAddAttachmentUri = { uri, name, mime ->
                                            coroutineScope.launch {
                                                noteRepository.saveAttachmentFromUri("vault_standalone", uri, name, mime)
                                            }
                                        },
                                        onDeleteAttachment = { attId ->
                                            coroutineScope.launch {
                                                noteRepository.deleteAttachment(attId)
                                            }
                                        }
                                    )
                                }

                                AppDestination.BACKUP_CENTER -> {
                                    BackupCenterScreen(
                                        backups = backups,
                                        backupManager = backupManager,
                                        backupFrequency = preferences.backupFrequency,
                                        onSetBackupFrequency = { freq ->
                                            coroutineScope.launch { preferencesRepository.setBackupFrequency(freq) }
                                        }
                                    )
                                }

                                AppDestination.SETTINGS -> {
                                    SettingsScreen(
                                        preferences = preferences,
                                        onSetThemeMode = { mode ->
                                            coroutineScope.launch { preferencesRepository.setThemeMode(mode) }
                                        },
                                        onSetAccentIndex = { idx ->
                                            coroutineScope.launch { preferencesRepository.setAccentIndex(idx) }
                                        },
                                        onSetDefaultNoteType = { type ->
                                            coroutineScope.launch { preferencesRepository.setDefaultNoteType(type) }
                                        },
                                        onSetReducedMotion = { red ->
                                            coroutineScope.launch { preferencesRepository.setReducedMotion(red) }
                                        },
                                        onSetReducedTransparency = { redTrans ->
                                            coroutineScope.launch { preferencesRepository.setReducedTransparency(redTrans) }
                                        },
                                        onSetHighContrast = { hiCon ->
                                            coroutineScope.launch { preferencesRepository.setHighContrast(hiCon) }
                                        },
                                        noteCount = activeNoteCount,
                                        attachmentCount = attachments.size,
                                        backupCount = backups.size
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Global Command Palette Dialog
        if (showCommandPalette) {
            CommandPaletteDialog(
                onDismiss = { showCommandPalette = false },
                actions = commandActions
            )
        }
    }
}

/**
 * Apple Liquid Glass Floating Pill Dock (§12 Materials & Depth):
 * - Floating capsule dock with 3D specular rim border, inner meniscus highlight,
 *   and luminous active capsule pill.
 * - Automatically adapts to Reduce Transparency and Increase Contrast.
 */
@Composable
private fun AuraTranslucentBottomBar(
    sections: List<NavSection>,
    currentDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit
) {
    val a11y = LocalAuraAccessibility.current
    val specularBright = if (a11y.isDark) AuraDarkSpecularEdge else AuraLightSpecularEdge
    val specularSubtle = if (a11y.isDark) AuraDarkSpecularSubtle else AuraLightSpecularSubtle
    val barColor = if (a11y.reducedTransparency || a11y.highContrast) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = if (a11y.isDark) 0.80f else 0.86f)
    }

    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = pillShape,
            color = barColor,
            tonalElevation = 8.dp,
            shadowElevation = if (a11y.highContrast) 0.dp else 14.dp,
            border = BorderStroke(
                width = if (a11y.highContrast) 1.5.dp else 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        specularBright,
                        a11y.accentSecondary.copy(alpha = 0.35f),
                        specularSubtle
                    )
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(pillShape)
                .drawWithContent {
                    if (!a11y.reducedTransparency && !a11y.highContrast) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (a11y.isDark) 0.08f else 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                    drawContent()
                    if (!a11y.highContrast) {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, specularBright, specularBright, Color.Transparent)
                            ),
                            start = Offset(32f, 1.2f),
                            end = Offset(size.width - 32f, 1.2f),
                            strokeWidth = 1.8f
                        )
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                sections.forEach { section ->
                    val isSelected = currentDestination == section.destination
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (isSelected && !a11y.highContrast) {
                            BorderStroke(
                                0.8.dp,
                                Brush.linearGradient(
                                    listOf(
                                        specularBright,
                                        a11y.accentPrimary.copy(alpha = 0.4f)
                                    )
                                )
                            )
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .appleSpringClickable(
                                pressedScale = 0.92f,
                                enableHaptics = true,
                                onClick = { onSelectDestination(section.destination) }
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                section.icon,
                                contentDescription = section.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp)
                            )
                            Text(
                                text = section.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Adaptive iPadOS / Tablet Translucent Navigation Sidebar Rail (§7 Responsive Design)
 */
@Composable
private fun AuraTabletSidebarRail(
    sections: List<NavSection>,
    currentDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    onOpenCommandPalette: () -> Unit
) {
    val a11y = LocalAuraAccessibility.current
    val railColor = if (a11y.reducedTransparency || a11y.highContrast) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    }

    Surface(
        color = railColor,
        tonalElevation = 4.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .width(104.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .appleSpringClickable(
                        pressedScale = 0.92f,
                        enableHaptics = true,
                        onClick = onOpenCommandPalette
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = "Command Center",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            sections.forEach { section ->
                val isSelected = currentDestination == section.destination
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .appleSpringClickable(
                            pressedScale = 0.94f,
                            enableHaptics = true,
                            onClick = { onSelectDestination(section.destination) }
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            section.icon,
                            contentDescription = section.label,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
