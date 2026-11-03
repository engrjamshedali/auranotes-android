package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AuraDatabase
import com.example.data.local.NoteEntity
import com.example.data.local.NoteType
import com.example.data.local.PreferencesRepository
import com.example.domain.BackupManager
import com.example.domain.NoteRepository
import com.example.ui.components.CommandPaletteDialog
import com.example.ui.components.PaletteAction
import com.example.ui.screens.*
import com.example.ui.theme.AuraNotesTheme
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class AppDestination {
    HOME,
    RICH_TEXT_EDITOR,
    CODE_STUDIO,
    MEDIA_VAULT,
    BACKUP_CENTER,
    SETTINGS
}

@Composable
fun AuraMainApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PINNED, FAVORITES, CODE, TASKS, ARCHIVE, TRASH

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

    // Command palette actions
    val commandActions = remember(categories) {
        listOf(
            PaletteAction(
                title = "New Rich Note",
                subtitle = "Create a formatted markdown note",
                icon = Icons.Default.EditNote,
                category = "Create"
            ) { openNewNote(NoteType.RICH_TEXT) },
            PaletteAction(
                title = "New Python Code Script",
                subtitle = "Create code file in Code Studio",
                icon = Icons.Default.Code,
                category = "Code"
            ) { openNewNote(NoteType.CODE, "python") },
            PaletteAction(
                title = "New HTML/JS Web Project",
                subtitle = "Create interactive Web code note",
                icon = Icons.Default.Language,
                category = "Code"
            ) { openNewNote(NoteType.CODE, "html") },
            PaletteAction(
                title = "New SQL Database Query",
                subtitle = "In-memory SQLite table query note",
                icon = Icons.Default.Storage,
                category = "Code"
            ) { openNewNote(NoteType.CODE, "sql") },
            PaletteAction(
                title = "New Checklist Note",
                subtitle = "Task checklist with interactive items",
                icon = Icons.Default.Checklist,
                category = "Create"
            ) { openNewNote(NoteType.CHECKLIST) },
            PaletteAction(
                title = "Open Media Vault",
                subtitle = "Browse attachments & voice recordings",
                icon = Icons.Default.PermMedia,
                category = "Navigation"
            ) { currentDestination = AppDestination.MEDIA_VAULT },
            PaletteAction(
                title = "Create Portable Backup",
                subtitle = "Export complete .auranotes package",
                icon = Icons.Default.CloudSync,
                category = "Backup"
            ) { currentDestination = AppDestination.BACKUP_CENTER },
            PaletteAction(
                title = "Open Settings",
                subtitle = "Configure theme, accents & storage",
                icon = Icons.Default.Settings,
                category = "Navigation"
            ) { currentDestination = AppDestination.SETTINGS }
        )
    }

    AuraNotesTheme(
        darkTheme = isDark,
        accentIndex = preferences.accentIndex
    ) {
        Scaffold(
            bottomBar = {
                if (currentDestination in listOf(AppDestination.HOME, AppDestination.MEDIA_VAULT, AppDestination.BACKUP_CENTER, AppDestination.SETTINGS)) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        NavigationBarItem(
                            selected = currentDestination == AppDestination.HOME,
                            onClick = { currentDestination = AppDestination.HOME },
                            icon = { Icon(Icons.Default.Notes, contentDescription = "Notes") },
                            label = { Text("Notes") }
                        )
                        NavigationBarItem(
                            selected = currentDestination == AppDestination.MEDIA_VAULT,
                            onClick = { currentDestination = AppDestination.MEDIA_VAULT },
                            icon = { Icon(Icons.Default.PermMedia, contentDescription = "Vault") },
                            label = { Text("Vault") }
                        )
                        NavigationBarItem(
                            selected = currentDestination == AppDestination.BACKUP_CENTER,
                            onClick = { currentDestination = AppDestination.BACKUP_CENTER },
                            icon = { Icon(Icons.Default.CloudSync, contentDescription = "Backups") },
                            label = { Text("Backups") }
                        )
                        NavigationBarItem(
                            selected = currentDestination == AppDestination.SETTINGS,
                            onClick = { currentDestination = AppDestination.SETTINGS },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (currentDestination == AppDestination.HOME) {
                    Box {
                        FloatingActionButton(
                            onClick = { showCreateMenu = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create")
                        }

                        DropdownMenu(
                            expanded = showCreateMenu,
                            onDismissRequest = { showCreateMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rich Text Note") },
                                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                                onClick = {
                                    showCreateMenu = false
                                    openNewNote(NoteType.RICH_TEXT)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Code Studio File") },
                                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                                onClick = {
                                    showCreateMenu = false
                                    openNewNote(NoteType.CODE, "python")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Checklist Note") },
                                leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) },
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentDestination) {
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
                                coroutineScope.launch { noteRepository.toggleArchive(note.id, note.isArchived) }
                            },
                            onMoveToTrash = { note ->
                                coroutineScope.launch { noteRepository.moveToTrash(note.id) }
                            },
                            onRestoreFromTrash = { note ->
                                coroutineScope.launch { noteRepository.restoreFromTrash(note.id) }
                            },
                            onDeletePermanently = { note ->
                                coroutineScope.launch { noteRepository.deletePermanently(note.id) }
                            },
                            onDuplicateNote = { note ->
                                coroutineScope.launch { noteRepository.duplicateNote(note) }
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
                            currentDestination = AppDestination.HOME
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
                            currentDestination = AppDestination.HOME
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
                            noteCount = activeNoteCount,
                            attachmentCount = attachments.size,
                            backupCount = backups.size
                        )
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
