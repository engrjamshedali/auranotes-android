package com.example.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AuthCancelledException
import com.example.auth.DriveAuthorizationOutcome
import com.example.auth.GoogleAuthManager
import com.example.auth.GoogleAuthUiState
import com.example.cloud.CloudBackupFileInfo
import com.example.cloud.CloudSyncCoordinator
import com.example.cloud.CloudSyncOutcome
import com.example.cloud.ConnectivityMonitor
import com.example.cloud.DriveResolutionException
import com.example.cloud.GoogleDriveClient
import com.example.cloud.NighttimeBackupScheduler
import com.example.data.local.AuraDatabase
import com.example.data.local.CloudSyncStatus
import com.example.data.local.NoteEntity
import com.example.data.local.NoteType
import com.example.data.local.PreferencesRepository
import com.example.domain.BackupManager
import com.example.domain.NoteRepository
import com.example.ui.components.AppleFluidSprings
import com.example.ui.components.AuraAnimatedNavIcon
import com.example.ui.components.AuraExpandableDrawerContent
import com.example.ui.components.AuraIcon
import com.example.ui.components.AuraIconSize
import com.example.ui.components.AuraIcons
import com.example.ui.components.AuraLiquidBackground
import com.example.ui.components.AuraSplashOverlay
import com.example.ui.components.CommandPaletteDialog
import com.example.ui.components.DrawerNavCategory
import com.example.ui.components.DrawerNavItem
import com.example.ui.components.PaletteAction
import com.example.ui.components.appleSpringClickable
import com.example.ui.components.neuInset
import com.example.ui.components.neuRaised
import com.example.ui.screens.*
import com.example.ui.theme.AuraNotesTheme
import com.example.ui.theme.LocalAuraAccessibility
import com.example.ui.theme.LocalAuraNeumorphicPalette
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

    // Initialize Database, Auth, Drive & Repositories
    val database = remember { AuraDatabase.getInstance(context) }
    val preferencesRepository = remember { PreferencesRepository(context) }
    val authManager = remember { GoogleAuthManager(context.applicationContext) }
    DisposableEffect(authManager) {
        onDispose { authManager.dispose() }
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
    val driveClient = remember { GoogleDriveClient() }
    val cloudSyncCoordinator = remember {
        CloudSyncCoordinator(
            context = context.applicationContext,
            authManager = authManager,
            driveClient = driveClient,
            noteDao = database.noteDao(),
            categoryDao = database.categoryDao(),
            attachmentDao = database.attachmentDao(),
            backupDao = database.backupDao(),
            backupManager = backupManager,
            preferencesRepository = preferencesRepository
        )
    }
    val noteRepository = remember {
        NoteRepository(
            context = context,
            noteDao = database.noteDao(),
            categoryDao = database.categoryDao(),
            attachmentDao = database.attachmentDao(),
            backupDao = database.backupDao(),
            preferencesRepository = preferencesRepository,
            onLocalDataMutated = {
                coroutineScope.launch {
                    val snap = preferencesRepository.getSnapshot()
                    NighttimeBackupScheduler.enqueuePendingChangeSync(
                        context = context,
                        preferences = snap,
                        isAuthenticated = authManager.currentUser() != null
                    )
                }
            }
        )
    }

    // User preferences, auth & connectivity
    val preferences by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = com.example.data.local.AuraUserPreferences()
    )
    val authState by authManager.authState.collectAsStateWithLifecycle()
    val currentUser = (authState as? GoogleAuthUiState.SignedIn)?.user
    val isAuthenticated = currentUser != null
    val isSigningInNow = authState is GoogleAuthUiState.SigningIn
    var authErrorBanner by remember { mutableStateOf<String?>(null) }

    val networkAvailability by ConnectivityMonitor.observeNetwork(context).collectAsStateWithLifecycle(
        initialValue = cloudSyncCoordinator.checkNetworkAvailability()
    )
    var cloudBackups by remember { mutableStateOf<List<CloudBackupFileInfo>>(emptyList()) }
    var isCloudListLoading by remember { mutableStateOf(false) }
    var isBackingUpNow by remember { mutableStateOf(false) }
    var pendingDriveAction by remember { mutableStateOf<String?>(null) }

    val effectiveSyncStatus = remember(
        isBackingUpNow,
        isAuthenticated,
        preferences.driveBackupEnabled,
        preferences.cloudSyncStatus,
        preferences.wifiOnlyBackup,
        preferences.hasPendingChanges,
        networkAvailability
    ) {
        when {
            isBackingUpNow -> CloudSyncStatus.BACKING_UP
            !isAuthenticated || !preferences.driveBackupEnabled -> CloudSyncStatus.AUTH_REQUIRED
            networkAvailability == CloudSyncCoordinator.NetworkAvailability.OFFLINE -> CloudSyncStatus.OFFLINE
            preferences.wifiOnlyBackup &&
                networkAvailability == CloudSyncCoordinator.NetworkAvailability.METERED_CELLULAR &&
                preferences.hasPendingChanges -> CloudSyncStatus.WAITING_FOR_WIFI
            else -> CloudSyncStatus.fromName(preferences.cloudSyncStatus)
        }
    }

    fun refreshCloudBackupsList() {
        if (!isAuthenticated || !preferences.driveBackupEnabled) return
        coroutineScope.launch {
            isCloudListLoading = true
            val result = cloudSyncCoordinator.fetchCloudBackups(
                callerContext = context,
                interactiveAllowed = false
            )
            isCloudListLoading = false
            result.onSuccess { files ->
                cloudBackups = files
            }.onFailure { err ->
                if (err is DriveResolutionException) {
                    preferencesRepository.updateCloudSyncStatus(
                        CloudSyncStatus.AUTH_REQUIRED,
                        "Google Drive permission required."
                    )
                }
            }
        }
    }

    fun triggerManualCloudBackup(force: Boolean = true) {
        coroutineScope.launch {
            isBackingUpNow = true
            val outcome = cloudSyncCoordinator.performCloudBackup(
                force = force,
                createHistorySnapshot = force,
                interactiveAllowed = true,
                callerContext = context
            )
            isBackingUpNow = false
            when (outcome) {
                is CloudSyncOutcome.Success -> {
                    snackbarHostState.showSnackbar(outcome.message)
                    refreshCloudBackupsList()
                }
                is CloudSyncOutcome.WaitingForCondition,
                is CloudSyncOutcome.RetryLater,
                is CloudSyncOutcome.Failed -> {
                    val msg = when (outcome) {
                        is CloudSyncOutcome.WaitingForCondition -> outcome.message
                        is CloudSyncOutcome.RetryLater -> outcome.message
                        is CloudSyncOutcome.Failed -> outcome.message
                        else -> ""
                    }
                    if (msg.isNotBlank()) snackbarHostState.showSnackbar(msg)
                }
                is CloudSyncOutcome.ResolutionRequired -> {
                    // Handled below after launcher registration if needed via state
                    snackbarHostState.showSnackbar("Please grant Google Drive AppData permission.")
                }
            }
        }
    }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val tokenRes = authManager.extractTokenFromIntentResult(context, result.data)
            tokenRes.onSuccess {
                coroutineScope.launch {
                    val action = pendingDriveAction
                    pendingDriveAction = null
                    preferencesRepository.setDriveBackupConfig(true, currentUser?.email.orEmpty())
                    if (action == "REFRESH_LIST") {
                        refreshCloudBackupsList()
                    } else {
                        triggerManualCloudBackup(force = true)
                    }
                }
            }.onFailure { err ->
                pendingDriveAction = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(err.localizedMessage ?: "Drive authorization failed.")
                }
            }
        } else {
            pendingDriveAction = null
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Google Drive permission was cancelled.")
            }
        }
    }

    fun launchDriveBackupWithConsent(force: Boolean = true) {
        coroutineScope.launch {
            isBackingUpNow = true
            val outcome = cloudSyncCoordinator.performCloudBackup(
                force = force,
                createHistorySnapshot = force,
                interactiveAllowed = true,
                callerContext = context
            )
            isBackingUpNow = false
            when (outcome) {
                is CloudSyncOutcome.ResolutionRequired -> {
                    pendingDriveAction = "BACKUP_NOW"
                    driveConsentLauncher.launch(IntentSenderRequest.Builder(outcome.intentSender).build())
                }
                is CloudSyncOutcome.Success -> {
                    snackbarHostState.showSnackbar(outcome.message)
                    refreshCloudBackupsList()
                }
                is CloudSyncOutcome.WaitingForCondition -> snackbarHostState.showSnackbar(outcome.message)
                is CloudSyncOutcome.RetryLater -> snackbarHostState.showSnackbar(outcome.message)
                is CloudSyncOutcome.Failed -> snackbarHostState.showSnackbar(outcome.message)
            }
        }
    }

    fun handleGoogleSignIn() {
        coroutineScope.launch {
            authErrorBanner = null
            val res = authManager.signInWithGoogle(context)
            res.onSuccess { profile ->
                preferencesRepository.setOfflineModeBypassedAuth(false)
                snackbarHostState.showSnackbar("Signed in as ${profile.email}")
            }.onFailure { err ->
                if (err !is AuthCancelledException) {
                    val msg = err.localizedMessage ?: "Google Sign-In failed."
                    authErrorBanner = msg
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    fun handleSwitchAccount() {
        coroutineScope.launch {
            NighttimeBackupScheduler.cancelAllCloudWork(context)
            cloudBackups = emptyList()
            preferencesRepository.setDriveBackupConfig(false, "")
            val res = authManager.switchAccount(context)
            res.onSuccess { profile ->
                preferencesRepository.setOfflineModeBypassedAuth(false)
                snackbarHostState.showSnackbar("Switched to ${profile.email}")
            }.onFailure { err ->
                if (err !is AuthCancelledException) {
                    snackbarHostState.showSnackbar(err.localizedMessage ?: "Account switch cancelled.")
                }
            }
        }
    }

    fun handleSignOut() {
        coroutineScope.launch {
            NighttimeBackupScheduler.cancelAllCloudWork(context)
            cloudBackups = emptyList()
            preferencesRepository.clearCloudSessionOnSignOut()
            authManager.signOut()
            snackbarHostState.showSnackbar("Signed out. Local notes preserved.")
        }
    }

    fun handleToggleDriveBackup(enable: Boolean) {
        coroutineScope.launch {
            if (!enable) {
                NighttimeBackupScheduler.cancelAllCloudWork(context)
                cloudBackups = emptyList()
                preferencesRepository.setDriveBackupConfig(false, currentUser?.email.orEmpty())
                snackbarHostState.showSnackbar("Google Drive backup paused.")
            } else {
                when (val outcome = authManager.authorizeDriveAppData(context, interactiveAllowed = true)) {
                    is DriveAuthorizationOutcome.Authorized -> {
                        preferencesRepository.setDriveBackupConfig(true, currentUser?.email.orEmpty())
                        launchDriveBackupWithConsent(force = true)
                    }
                    is DriveAuthorizationOutcome.ResolutionRequired -> {
                        pendingDriveAction = "ENABLE_AND_BACKUP"
                        driveConsentLauncher.launch(IntentSenderRequest.Builder(outcome.intentSender).build())
                    }
                    is DriveAuthorizationOutcome.Failed -> {
                        snackbarHostState.showSnackbar(outcome.message)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val snap = preferencesRepository.getSnapshot()
        if (!snap.offlineModeBypassedAuth && authManager.currentUser() == null) {
            authManager.attemptSilentSignIn(context)
        }
    }

    LaunchedEffect(
        isAuthenticated,
        preferences.driveBackupEnabled,
        preferences.backupFrequency,
        preferences.nightWindowStartHour,
        preferences.nightWindowEndHour,
        preferences.wifiOnlyBackup,
        preferences.requireChargingForNightBackup,
        preferences.requireIdleForNightBackup
    ) {
        NighttimeBackupScheduler.syncSchedules(context, preferences, isAuthenticated)
    }

    LaunchedEffect(
        networkAvailability,
        isAuthenticated,
        preferences.driveBackupEnabled,
        preferences.hasPendingChanges,
        preferences.autoSyncOnChange,
        preferences.wifiOnlyBackup
    ) {
        if (isAuthenticated && preferences.driveBackupEnabled && preferences.autoSyncOnChange && preferences.hasPendingChanges) {
            val canUploadNow = networkAvailability == CloudSyncCoordinator.NetworkAvailability.UNMETERED_WIFI ||
                (!preferences.wifiOnlyBackup && networkAvailability == CloudSyncCoordinator.NetworkAvailability.METERED_CELLULAR)
            if (canUploadNow) {
                NighttimeBackupScheduler.enqueuePendingChangeSync(context, preferences, true)
            }
        }
    }

    // Current navigation, drawer & splash state
    var showSplash by remember { mutableStateOf(true) }
    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }
    var activeNoteId by remember { mutableStateOf<String?>(null) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var isDesktopDrawerCollapsed by remember { mutableStateOf(false) }
    val mobileDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Filters, layout mode, and search query for Home
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var isGridView by rememberSaveable { mutableStateOf(false) }

    // Flows
    val allActiveNotes by noteRepository.activeNotes.collectAsStateWithLifecycle(initialValue = emptyList())
    val archivedNotes by noteRepository.archivedNotes.collectAsStateWithLifecycle(initialValue = emptyList())
    val trashNotes by noteRepository.trashNotes.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by noteRepository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())
    val attachments by noteRepository.allAttachments.collectAsStateWithLifecycle(initialValue = emptyList())
    val backups by noteRepository.allBackups.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeNoteCount by noteRepository.activeNoteCount.collectAsStateWithLifecycle(initialValue = 0)

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

    // Back handling: close drawer first, otherwise return to HOME
    BackHandler(enabled = mobileDrawerState.isOpen) {
        coroutineScope.launch { mobileDrawerState.close() }
    }
    BackHandler(enabled = !mobileDrawerState.isOpen && currentDestination != AppDestination.HOME) {
        currentDestination = AppDestination.HOME
        activeNoteId = null
    }

    val isDark = when (preferences.themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    fun closeMobileDrawer() {
        if (mobileDrawerState.isOpen) {
            coroutineScope.launch { mobileDrawerState.close() }
        }
    }

    fun openNewNote(type: NoteType, language: String = "python") {
        closeMobileDrawer()
        coroutineScope.launch {
            val newId = "note_" + UUID.randomUUID().toString().take(8)
            val now = System.currentTimeMillis()
            val newNote = NoteEntity(
                id = newId,
                title = if (type == NoteType.CODE) "script.$language" else "New Note",
                content = if (type == NoteType.CODE) "# Code Studio\nprint('Hello from AuraNotes')\n" else "",
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
            NavSection(AppDestination.HOME, "Library", AuraIcons.FolderOpen),
            NavSection(AppDestination.MEDIA_VAULT, "Media", AuraIcons.Images),
            NavSection(AppDestination.BACKUP_CENTER, "Backups", AuraIcons.CloudUpload),
            NavSection(AppDestination.SETTINGS, "Settings", AuraIcons.Settings)
        )
    }

    val commandActions = remember(categories) {
        listOf(
            PaletteAction(
                title = "New Rich Note",
                subtitle = "Create a formatted markdown document",
                icon = AuraIcons.FilePenLine,
                category = "Create"
            ) { openNewNote(NoteType.RICH_TEXT) },
            PaletteAction(
                title = "New Python Script",
                subtitle = "Run offline Python analytics & code",
                icon = AuraIcons.Code2,
                category = "Studio"
            ) { openNewNote(NoteType.CODE, "python") },
            PaletteAction(
                title = "New Web Sandbox",
                subtitle = "Interactive HTML/JS live preview",
                icon = AuraIcons.Globe,
                category = "Studio"
            ) { openNewNote(NoteType.CODE, "html") },
            PaletteAction(
                title = "New SQLite Query",
                subtitle = "Run in-memory SQL tables & queries",
                icon = AuraIcons.Database,
                category = "Studio"
            ) { openNewNote(NoteType.CODE, "sql") },
            PaletteAction(
                title = "New Checklist",
                subtitle = "Interactive task checklist",
                icon = AuraIcons.ListChecks,
                category = "Create"
            ) { openNewNote(NoteType.CHECKLIST) },
            PaletteAction(
                title = "Open Media Vault",
                subtitle = "Browse photos, documents & voice notes",
                icon = AuraIcons.Images,
                category = "Vault"
            ) { currentDestination = AppDestination.MEDIA_VAULT },
            PaletteAction(
                title = "Backup & Restore",
                subtitle = "Export SHA-256 verified .auranotes archive",
                icon = AuraIcons.CloudUpload,
                category = "Backup"
            ) { currentDestination = AppDestination.BACKUP_CENTER },
            PaletteAction(
                title = "Settings & Theme",
                subtitle = "Soft UI colors, accessibility & defaults",
                icon = AuraIcons.Settings,
                category = "System"
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
        val neu = LocalAuraNeumorphicPalette.current
        val errorColor = MaterialTheme.colorScheme.error

        val pinnedCount = remember(allActiveNotes) { allActiveNotes.count { it.isPinned } }
        val favoriteCount = remember(allActiveNotes) { allActiveNotes.count { it.isFavorite } }
        val codeCount = remember(allActiveNotes) { allActiveNotes.count { it.noteType == NoteType.CODE.name } }
        val checklistCount = remember(allActiveNotes) { allActiveNotes.count { it.noteType == NoteType.CHECKLIST.name } }

        val drawerCategories = remember(
            currentDestination,
            selectedFilter,
            selectedCategoryId,
            categories,
            allActiveNotes,
            pinnedCount,
            favoriteCount,
            codeCount,
            checklistCount,
            attachments.size,
            archivedNotes.size,
            trashNotes.size,
            backups.size
        ) {
            listOf(
                DrawerNavCategory(
                    id = "main",
                    title = "MAIN",
                    initiallyExpanded = true,
                    items = listOf(
                        DrawerNavItem(
                            id = "main_library",
                            label = "Library",
                            icon = AuraIcons.LayoutDashboard,
                            badgeCount = allActiveNotes.size,
                            isSelected = currentDestination == AppDestination.HOME && selectedFilter == "ALL" && selectedCategoryId == null,
                            onClick = {
                                selectedFilter = "ALL"
                                selectedCategoryId = null
                                currentDestination = AppDestination.HOME
                                closeMobileDrawer()
                            }
                        ),
                        DrawerNavItem(
                            id = "main_pinned",
                            label = "Pinned Notes",
                            icon = AuraIcons.Pin,
                            badgeCount = pinnedCount,
                            isSelected = currentDestination == AppDestination.HOME && selectedFilter == "PINNED",
                            onClick = {
                                selectedFilter = "PINNED"
                                currentDestination = AppDestination.HOME
                                closeMobileDrawer()
                            }
                        ),
                        DrawerNavItem(
                            id = "main_favorites",
                            label = "Favorites",
                            icon = AuraIcons.Heart,
                            badgeCount = favoriteCount,
                            accentTint = Color(0xFFF43F5E),
                            isSelected = currentDestination == AppDestination.HOME && selectedFilter == "FAVORITES",
                            onClick = {
                                selectedFilter = "FAVORITES"
                                currentDestination = AppDestination.HOME
                                closeMobileDrawer()
                            }
                        ),
                        DrawerNavItem(
                            id = "main_command",
                            label = "Command Center",
                            icon = AuraIcons.Terminal,
                            isSelected = false,
                            onClick = {
                                closeMobileDrawer()
                                showCommandPalette = true
                            }
                        )
                    )
                ),
                DrawerNavCategory(
                    id = "studio",
                    title = "STUDIO",
                    initiallyExpanded = true,
                    items = listOf(
                        DrawerNavItem(
                            id = "studio_code",
                            label = "Code Studio",
                            icon = AuraIcons.Code2,
                            badgeCount = codeCount,
                            isSelected = currentDestination == AppDestination.HOME && selectedFilter == "CODE",
                            onClick = {
                                selectedFilter = "CODE"
                                currentDestination = AppDestination.HOME
                                closeMobileDrawer()
                            }
                        ),
                        DrawerNavItem(
                            id = "studio_new_note",
                            label = "New Rich Note",
                            icon = AuraIcons.FilePenLine,
                            onClick = { openNewNote(NoteType.RICH_TEXT) }
                        ),
                        DrawerNavItem(
                            id = "studio_new_python",
                            label = "New Python Script",
                            icon = AuraIcons.FileCode2,
                            onClick = { openNewNote(NoteType.CODE, "python") }
                        ),
                        DrawerNavItem(
                            id = "studio_new_web",
                            label = "New Web Sandbox",
                            icon = AuraIcons.Globe,
                            onClick = { openNewNote(NoteType.CODE, "html") }
                        ),
                        DrawerNavItem(
                            id = "studio_new_sql",
                            label = "New SQLite Query",
                            icon = AuraIcons.Database,
                            onClick = { openNewNote(NoteType.CODE, "sql") }
                        )
                    )
                ),
                DrawerNavCategory(
                    id = "spaces",
                    title = "SPACES",
                    initiallyExpanded = true,
                    items = buildList {
                        add(
                            DrawerNavItem(
                                id = "space_all",
                                label = "All Spaces",
                                icon = AuraIcons.FolderOpen,
                                badgeCount = allActiveNotes.size,
                                isSelected = currentDestination == AppDestination.HOME && selectedFilter == "ALL" && selectedCategoryId == null,
                                onClick = {
                                    selectedCategoryId = null
                                    if (selectedFilter in listOf("ARCHIVE", "TRASH")) {
                                        selectedFilter = "ALL"
                                    }
                                    currentDestination = AppDestination.HOME
                                    closeMobileDrawer()
                                }
                            )
                        )
                        categories.forEach { cat ->
                            val spaceColor = try {
                                Color(android.graphics.Color.parseColor(cat.colorHex))
                            } catch (_: Exception) {
                                Color(0xFF3B82F6)
                            }
                            add(
                                DrawerNavItem(
                                    id = "space_${cat.id}",
                                    label = cat.name,
                                    icon = AuraIcons.Folder,
                                    badgeCount = allActiveNotes.count { it.categoryId == cat.id },
                                    accentTint = spaceColor,
                                    dotColor = spaceColor,
                                    isSelected = currentDestination == AppDestination.HOME && selectedCategoryId == cat.id,
                                    onClick = {
                                        selectedCategoryId = if (selectedCategoryId == cat.id) null else cat.id
                                        if (selectedFilter in listOf("ARCHIVE", "TRASH")) {
                                            selectedFilter = "ALL"
                                        }
                                        currentDestination = AppDestination.HOME
                                        closeMobileDrawer()
                                    }
                                )
                            )
                        }
                    }
                ),
                DrawerNavCategory(
                    id = "workspace",
                    title = "WORKSPACE",
                    initiallyExpanded = true,
                    items = listOf(
                        DrawerNavItem(
                            id = "ws_tasks",
                            label = "Task Checklists",
                            icon = AuraIcons.ListChecks,
                            badgeCount = checklistCount,
                            isSelected = currentDestination == AppDestination.HOME && selectedFilter == "TASKS",
                            onClick = {
                                selectedFilter = "TASKS"
                                currentDestination = AppDestination.HOME
                                closeMobileDrawer()
                            }
                        ),
                        DrawerNavItem(
                            id = "ws_media",
                            label = "Media Vault",
                            icon = AuraIcons.Images,
                            badgeCount = attachments.size,
                            isSelected = currentDestination == AppDestination.MEDIA_VAULT,
                            onClick = {
                                currentDestination = AppDestination.MEDIA_VAULT
                                closeMobileDrawer()
                            }
                        ),
                        DrawerNavItem(
                            id = "ws_archive",
                            label = "Archive",
                            icon = AuraIcons.Archive,
                            badgeCount = archivedNotes.size,
                            isSelected = currentDestination == AppDestination.HOME && selectedFilter == "ARCHIVE",
                            onClick = {
                                selectedFilter = "ARCHIVE"
                                currentDestination = AppDestination.HOME
                                closeMobileDrawer()
                            }
                        ),
                        DrawerNavItem(
                            id = "ws_trash",
                            label = "Trash",
                            icon = AuraIcons.Trash2,
                            badgeCount = trashNotes.size,
                            accentTint = errorColor,
                            isSelected = currentDestination == AppDestination.HOME && selectedFilter == "TRASH",
                            onClick = {
                                selectedFilter = "TRASH"
                                currentDestination = AppDestination.HOME
                                closeMobileDrawer()
                            }
                        )
                    )
                ),
                DrawerNavCategory(
                    id = "backup",
                    title = "BACKUP & STORAGE",
                    initiallyExpanded = true,
                    items = listOf(
                        DrawerNavItem(
                            id = "bk_center",
                            label = "Backup & Restore",
                            icon = AuraIcons.CloudUpload,
                            badgeCount = backups.size,
                            isSelected = currentDestination == AppDestination.BACKUP_CENTER,
                            onClick = {
                                currentDestination = AppDestination.BACKUP_CENTER
                                closeMobileDrawer()
                            }
                        )
                    )
                ),
                DrawerNavCategory(
                    id = "preferences",
                    title = "PREFERENCES",
                    initiallyExpanded = false,
                    items = listOf(
                        DrawerNavItem(
                            id = "pref_settings",
                            label = "Settings & Theme",
                            icon = AuraIcons.Settings,
                            isSelected = currentDestination == AppDestination.SETTINGS,
                            onClick = {
                                currentDestination = AppDestination.SETTINGS
                                closeMobileDrawer()
                            }
                        ),
                        DrawerNavItem(
                            id = "pref_a11y",
                            label = "Accessibility",
                            icon = AuraIcons.Accessibility,
                            onClick = {
                                currentDestination = AppDestination.SETTINGS
                                closeMobileDrawer()
                            }
                        ),
                        DrawerNavItem(
                            id = "pref_about",
                            label = "Developer Profile",
                            icon = AuraIcons.Info,
                            onClick = {
                                currentDestination = AppDestination.SETTINGS
                                closeMobileDrawer()
                            }
                        )
                    )
                )
            )
        }

        val isTopLevelScreen = currentDestination in listOf(
            AppDestination.HOME,
            AppDestination.MEDIA_VAULT,
            AppDestination.BACKUP_CENTER,
            AppDestination.SETTINGS
        )

        AuraLiquidBackground {
            if (!isAuthenticated && !preferences.offlineModeBypassedAuth) {
                GoogleSignInScreen(
                    isSigningIn = isSigningInNow,
                    errorMessage = authErrorBanner,
                    onContinueWithGoogle = { handleGoogleSignIn() },
                    onContinueOffline = {
                        coroutineScope.launch {
                            preferencesRepository.setOfflineModeBypassedAuth(true)
                        }
                    }
                )
            } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTabletOrWide = maxWidth >= 680.dp

                val mainScaffold: @Composable () -> Unit = {
                    Scaffold(
                        containerColor = Color.Transparent,
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState)
                        },
                        bottomBar = {
                            if (isTopLevelScreen && !isTabletOrWide) {
                                AuraNeumorphicBottomBar(
                                    sections = navSections,
                                    currentDestination = currentDestination,
                                    onSelectDestination = { currentDestination = it }
                                )
                            }
                        },
                        floatingActionButton = {
                            if (currentDestination == AppDestination.HOME) {
                                val fabRotation by animateFloatAsState(
                                    targetValue = if (showCreateMenu && !a11y.reducedMotion) 45f else 0f,
                                    animationSpec = AppleFluidSprings.adaptiveSpec(),
                                    label = "fab_rotation"
                                )
                                Box {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(62.dp)
                                            .neuRaised(
                                                cornerRadius = 31.dp,
                                                backgroundColor = MaterialTheme.colorScheme.primary,
                                                shadowOffset = 6.dp,
                                                blurRadius = 12.dp,
                                                accentBorder = a11y.accentSecondary
                                            )
                                            .testTag("create_note_fab")
                                            .appleSpringClickable(
                                                pressedScale = 0.91f,
                                                enableHaptics = true,
                                                onClick = { showCreateMenu = true }
                                            )
                                    ) {
                                        AuraIcon(
                                            imageVector = AuraIcons.Plus,
                                            contentDescription = "Create New Note or Code File",
                                            tint = Color.White,
                                            size = 24.dp,
                                            modifier = Modifier.graphicsLayer { rotationZ = fabRotation }
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showCreateMenu,
                                        onDismissRequest = { showCreateMenu = false },
                                        containerColor = neu.surfaceElevated
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rich Text Document", fontWeight = FontWeight.Medium) },
                                            leadingIcon = { AuraIcon(AuraIcons.FilePenLine, contentDescription = null, size = AuraIconSize.Button, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                showCreateMenu = false
                                                openNewNote(NoteType.RICH_TEXT)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Code Studio Script", fontWeight = FontWeight.Medium) },
                                            leadingIcon = { AuraIcon(AuraIcons.FileCode2, contentDescription = null, size = AuraIconSize.Button, tint = MaterialTheme.colorScheme.secondary) },
                                            onClick = {
                                                showCreateMenu = false
                                                openNewNote(NoteType.CODE, "python")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Task Checklist", fontWeight = FontWeight.Medium) },
                                            leadingIcon = { AuraIcon(AuraIcons.ListChecks, contentDescription = null, size = AuraIconSize.Button, tint = MaterialTheme.colorScheme.tertiary) },
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
                            // Tablet / Desktop Persistent Collapsible Expandable Drawer
                            if (isTopLevelScreen && isTabletOrWide) {
                                AuraExpandableDrawerContent(
                                    categories = drawerCategories,
                                    isCollapsed = isDesktopDrawerCollapsed,
                                    showCollapseToggle = true,
                                    onToggleCollapse = { isDesktopDrawerCollapsed = !isDesktopDrawerCollapsed },
                                    showCloseButton = false,
                                    userProfile = currentUser,
                                    syncStatus = effectiveSyncStatus,
                                    onAccountCardClick = {
                                        currentDestination = AppDestination.BACKUP_CENTER
                                    }
                                )
                            }

                            AnimatedContent(
                                targetState = currentDestination,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                transitionSpec = {
                                    if (a11y.reducedMotion) {
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
                                            isGridView = isGridView,
                                            onToggleGridView = { isGridView = !isGridView },
                                            syncStatus = effectiveSyncStatus,
                                            isAuthenticated = isAuthenticated,
                                            driveBackupEnabled = preferences.driveBackupEnabled,
                                            onStatusPillClick = {
                                                currentDestination = AppDestination.BACKUP_CENTER
                                            },
                                            showDrawerButton = !isTabletOrWide,
                                            onOpenDrawer = { coroutineScope.launch { mobileDrawerState.open() } },
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
                                            }
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
                                            showDrawerButton = !isTabletOrWide,
                                            onOpenDrawer = { coroutineScope.launch { mobileDrawerState.open() } },
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
                                        LaunchedEffect(isAuthenticated, preferences.driveBackupEnabled) {
                                            if (isAuthenticated && preferences.driveBackupEnabled && cloudBackups.isEmpty()) {
                                                refreshCloudBackupsList()
                                            }
                                        }
                                        BackupCenterScreen(
                                            backups = backups,
                                            backupManager = backupManager,
                                            preferences = preferences,
                                            userProfile = currentUser,
                                            syncStatus = effectiveSyncStatus,
                                            cloudBackups = cloudBackups,
                                            isCloudListLoading = isCloudListLoading,
                                            isBackingUpNow = isBackingUpNow,
                                            isSigningInNow = isSigningInNow,
                                            cloudSyncCoordinator = cloudSyncCoordinator,
                                            showDrawerButton = !isTabletOrWide,
                                            onOpenDrawer = { coroutineScope.launch { mobileDrawerState.open() } },
                                            onSignInWithGoogle = { handleGoogleSignIn() },
                                            onSwitchAccount = { handleSwitchAccount() },
                                            onSignOut = { handleSignOut() },
                                            onToggleDriveBackup = { handleToggleDriveBackup(it) },
                                            onCloudBackupNow = { launchDriveBackupWithConsent(force = true) },
                                            onRefreshCloudBackups = { refreshCloudBackupsList() },
                                            onSetBackupFrequency = { freq ->
                                                coroutineScope.launch { preferencesRepository.setBackupFrequency(freq) }
                                            },
                                            onSetNightWindow = { start, end ->
                                                coroutineScope.launch { preferencesRepository.setNightWindow(start, end) }
                                            },
                                            onSetAutoSyncOnChange = { enabled ->
                                                coroutineScope.launch { preferencesRepository.setAutoSyncOnChange(enabled) }
                                            },
                                            onSetWifiOnly = { wifi ->
                                                coroutineScope.launch { preferencesRepository.setWifiOnlyBackup(wifi) }
                                            },
                                            onSetRequireCharging = { charging ->
                                                coroutineScope.launch { preferencesRepository.setRequireChargingForNightBackup(charging) }
                                            },
                                            onSetRequireIdle = { idle ->
                                                coroutineScope.launch { preferencesRepository.setRequireIdleForNightBackup(idle) }
                                            }
                                        )
                                    }

                                    AppDestination.SETTINGS -> {
                                        SettingsScreen(
                                            preferences = preferences,
                                            userProfile = currentUser,
                                            syncStatus = effectiveSyncStatus,
                                            isSigningIn = isSigningInNow,
                                            onSignInWithGoogle = { handleGoogleSignIn() },
                                            onSwitchAccount = { handleSwitchAccount() },
                                            onSignOut = { handleSignOut() },
                                            onOpenCloudBackups = { currentDestination = AppDestination.BACKUP_CENTER },
                                            showDrawerButton = !isTabletOrWide,
                                            onOpenDrawer = { coroutineScope.launch { mobileDrawerState.open() } },
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

                if (isTabletOrWide) {
                    mainScaffold()
                } else {
                    ModalNavigationDrawer(
                        drawerState = mobileDrawerState,
                        gesturesEnabled = isTopLevelScreen,
                        scrimColor = Color.Black.copy(alpha = 0.28f),
                        drawerContent = {
                            ModalDrawerSheet(
                                drawerContainerColor = neu.background,
                                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                                windowInsets = WindowInsets(0, 0, 0, 0)
                            ) {
                                AuraExpandableDrawerContent(
                                    categories = drawerCategories,
                                    isCollapsed = false,
                                    showCollapseToggle = false,
                                    showCloseButton = true,
                                    onCloseDrawer = { closeMobileDrawer() },
                                    userProfile = currentUser,
                                    syncStatus = effectiveSyncStatus,
                                    onAccountCardClick = {
                                        closeMobileDrawer()
                                        currentDestination = AppDestination.BACKUP_CENTER
                                    }
                                )
                            }
                        },
                        content = mainScaffold
                    )
                }
            }
            }
        }

        if (showCommandPalette) {
            CommandPaletteDialog(
                onDismiss = { showCommandPalette = false },
                actions = commandActions
            )
        }

        AuraSplashOverlay(
            visible = showSplash,
            onSplashFinished = { showSplash = false }
        )
    }
}

/**
 * Floating Neumorphic Soft UI Bottom Navigation Dock:
 * - Raised rounded capsule with soft dual-direction outer shadows.
 * - Active tab sits inside a recessed Neumorphic pill well with crisp primary accent tint.
 */
@Composable
private fun AuraNeumorphicBottomBar(
    sections: List<NavSection>,
    currentDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit
) {
    val neu = LocalAuraNeumorphicPalette.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .neuRaised(
                    cornerRadius = 28.dp,
                    backgroundColor = neu.surface,
                    shadowOffset = 6.dp,
                    blurRadius = 13.dp
                )
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sections.forEach { section ->
                val isSelected = currentDestination == section.destination
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (isSelected) {
                                Modifier.neuInset(
                                    cornerRadius = 21.dp,
                                    backgroundColor = neu.surfaceRecessed,
                                    shadowOffset = 2.5.dp,
                                    blurRadius = 5.dp,
                                    focusBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                )
                            } else {
                                Modifier.clip(RoundedCornerShape(21.dp))
                            }
                        )
                        .appleSpringClickable(
                            pressedScale = 0.94f,
                            enableHaptics = true,
                            onClick = { onSelectDestination(section.destination) }
                        )
                        .padding(vertical = 7.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        AuraAnimatedNavIcon(
                            imageVector = section.icon,
                            contentDescription = section.label,
                            selected = isSelected,
                            size = AuraIconSize.MobileNav
                        )
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
