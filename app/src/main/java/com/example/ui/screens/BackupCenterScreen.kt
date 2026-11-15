package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.auth.GoogleUserProfile
import com.example.cloud.CloudBackupFileInfo
import com.example.cloud.CloudNoteResolutionChoice
import com.example.cloud.CloudRestoreMode
import com.example.cloud.CloudRestorePreview
import com.example.cloud.CloudRestoreReport
import com.example.cloud.CloudSyncCoordinator
import com.example.data.local.AuraUserPreferences
import com.example.data.local.BackupEntity
import com.example.data.local.CloudSyncStatus
import com.example.domain.BackupManager
import com.example.domain.RestorePreview
import com.example.domain.RestoreReport
import com.example.ui.components.AuraGlassCard
import com.example.ui.components.AuraIcon
import com.example.ui.components.AuraIconContainer
import com.example.ui.components.AuraIconSize
import com.example.ui.components.AuraIcons
import com.example.ui.components.AuraNeuButton
import com.example.ui.components.AuraNeuDivider
import com.example.ui.components.AuraNeuIconButton
import com.example.ui.components.AuraScrollEdgeMask
import com.example.ui.components.CloudRestoreWizardDialog
import com.example.ui.components.GoogleDriveConnectionCard
import com.example.ui.components.NighttimeScheduleCard
import com.example.ui.components.formatReadableBytes
import com.example.ui.components.neuInset
import com.example.ui.components.neuRaised
import com.example.ui.theme.LocalAuraNeumorphicPalette
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupCenterScreen(
    backups: List<BackupEntity>,
    backupManager: BackupManager,
    preferences: AuraUserPreferences,
    userProfile: GoogleUserProfile?,
    syncStatus: CloudSyncStatus,
    cloudBackups: List<CloudBackupFileInfo>,
    isCloudListLoading: Boolean,
    isBackingUpNow: Boolean,
    isSigningInNow: Boolean,
    cloudSyncCoordinator: CloudSyncCoordinator,
    showDrawerButton: Boolean = true,
    onOpenDrawer: () -> Unit = {},
    onSignInWithGoogle: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
    onToggleDriveBackup: (Boolean) -> Unit,
    onCloudBackupNow: () -> Unit,
    onRefreshCloudBackups: () -> Unit,
    onSetBackupFrequency: (String) -> Unit,
    onSetNightWindow: (Int, Int) -> Unit,
    onSetAutoSyncOnChange: (Boolean) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetRequireCharging: (Boolean) -> Unit,
    onSetRequireIdle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val neu = LocalAuraNeumorphicPalette.current

    var isExporting by remember { mutableStateOf(false) }
    var isInspecting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }

    var lastExportedFile by remember { mutableStateOf<File?>(null) }
    var restorePreview by remember { mutableStateOf<RestorePreview?>(null) }
    var restoreReport by remember { mutableStateOf<RestoreReport?>(null) }
    var showRestoreModal by remember { mutableStateOf(false) }
    var selectedRestoreMode by remember { mutableStateOf("MERGE") }

    // Cloud Drive Restore state
    var showCloudRestoreModal by remember { mutableStateOf(false) }
    var isInspectingCloud by remember { mutableStateOf(false) }
    var isRestoringCloud by remember { mutableStateOf(false) }
    var cloudRestorePreview by remember { mutableStateOf<CloudRestorePreview?>(null) }
    var cloudRestoreReport by remember { mutableStateOf<CloudRestoreReport?>(null) }

    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    var saveStatusMessage by remember { mutableStateOf<String?>(null) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        val src = pendingSaveFile
        if (destUri != null && src != null && src.exists()) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        src.inputStream().use { input -> input.copyTo(out) }
                    }
                    saveStatusMessage = "Exported ${src.name}"
                } catch (e: Exception) {
                    saveStatusMessage = "Export failed: ${e.localizedMessage ?: "Write error"}"
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isInspecting = true
            coroutineScope.launch {
                val preview = backupManager.inspectBackup(it)
                restorePreview = preview
                isInspecting = false
                showRestoreModal = true
            }
        }
    }

    fun triggerManualBackup() {
        isExporting = true
        coroutineScope.launch {
            val file = backupManager.createCompleteBackup(backupType = "MANUAL")
            lastExportedFile = file
            isExporting = false
        }
    }

    fun shareBackup(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export AuraNotes Backup"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDrawerButton) {
                AuraNeuIconButton(
                    onClick = onOpenDrawer,
                    size = 44.dp,
                    cornerRadius = 14.dp,
                    modifier = Modifier.testTag("open_drawer_button")
                ) {
                    AuraIcon(
                        AuraIcons.Menu,
                        contentDescription = "Open Navigation Drawer",
                        size = AuraIconSize.Toolbar
                    )
                }
                Spacer(Modifier.width(10.dp))
            }

            Column {
                Text(
                    "Backups & Cloud Sync",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Google Drive AppData & portable archives",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Google Account & Drive Connection Card
                item {
                    GoogleDriveConnectionCard(
                        userProfile = userProfile,
                        preferences = preferences,
                        syncStatus = syncStatus,
                        isBackingUpNow = isBackingUpNow,
                        isSigningInNow = isSigningInNow,
                        onSignInWithGoogle = onSignInWithGoogle,
                        onSwitchAccount = onSwitchAccount,
                        onSignOut = onSignOut,
                        onToggleDriveBackup = onToggleDriveBackup,
                        onBackupNow = onCloudBackupNow,
                        onRefreshCloudList = onRefreshCloudBackups
                    )
                }

                // Quick Actions Card (Local .auranotes archive)
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuraIconContainer(
                                    imageVector = AuraIcons.CloudUpload,
                                    containerSize = 38.dp,
                                    iconSize = AuraIconSize.Toolbar,
                                    cornerRadius = 12.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Portable Archive", style = MaterialTheme.typography.titleMedium)
                            }

                            Text(
                                "Bundle notes, categories, and media into one SHA-256 verified archive.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AuraNeuButton(
                                    onClick = { triggerManualBackup() },
                                    enabled = !isExporting,
                                    isAccent = true,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isExporting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        AuraIcon(AuraIcons.Upload, contentDescription = null, size = AuraIconSize.Button, tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Create", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                    }
                                }

                                AuraNeuButton(
                                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    enabled = !isInspecting,
                                    isAccent = false,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isInspecting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        AuraIcon(AuraIcons.Download, contentDescription = null, size = AuraIconSize.Button, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Restore", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }

                            lastExportedFile?.let { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .neuInset(
                                            cornerRadius = 16.dp,
                                            backgroundColor = neu.surfaceRecessed,
                                            shadowOffset = 3.dp,
                                            blurRadius = 6.dp
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Ready: ${file.name}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${file.length() / 1024} KB written",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AuraNeuIconButton(
                                            onClick = {
                                                pendingSaveFile = file
                                                saveDocumentLauncher.launch(file.name)
                                            },
                                            size = 38.dp,
                                            cornerRadius = 11.dp
                                        ) {
                                            AuraIcon(AuraIcons.Download, contentDescription = "Save to Files", size = AuraIconSize.Button)
                                        }
                                        AuraNeuIconButton(
                                            onClick = { shareBackup(file) },
                                            size = 38.dp,
                                            cornerRadius = 11.dp,
                                            isAccent = true
                                        ) {
                                            AuraIcon(AuraIcons.Share2, contentDescription = "Share", size = AuraIconSize.Button, tint = Color.White)
                                        }
                                    }
                                }
                            }

                            saveStatusMessage?.let { statusMsg ->
                                Text(
                                    text = statusMsg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Smart Night-Time Schedule Card
                item {
                    NighttimeScheduleCard(
                        preferences = preferences,
                        onSetBackupFrequency = onSetBackupFrequency,
                        onSetNightWindow = onSetNightWindow,
                        onSetAutoSyncOnChange = onSetAutoSyncOnChange,
                        onSetWifiOnly = onSetWifiOnly,
                        onSetRequireCharging = onSetRequireCharging,
                        onSetRequireIdle = onSetRequireIdle
                    )
                }

                // Google Drive Cloud Backups List
                if (userProfile != null && preferences.driveBackupEnabled) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Google Drive Snapshots (${cloudBackups.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (isCloudListLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                    }

                    if (cloudBackups.isEmpty() && !isCloudListLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .neuInset(
                                        cornerRadius = 18.dp,
                                        backgroundColor = neu.surfaceRecessed,
                                        shadowOffset = 3.dp,
                                        blurRadius = 6.dp
                                    )
                                    .padding(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No Google Drive snapshots found yet. Tap Backup Now to create one.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(cloudBackups, key = { it.fileId }) { cloudFile ->
                            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            AuraIcon(
                                                AuraIcons.Cloud,
                                                contentDescription = null,
                                                size = AuraIconSize.Small,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = if (cloudFile.kind == "latest") "Latest Auto-Sync Snapshot" else cloudFile.name,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            "${cloudFile.noteCount} notes · ${formatReadableBytes(cloudFile.sizeBytes)} · v${cloudFile.schemaVersion}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (cloudFile.modifiedTimeMillis > 0L) {
                                            Text(
                                                SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
                                                    .format(Date(cloudFile.modifiedTimeMillis)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                            )
                                        }
                                    }

                                    AuraNeuButton(
                                        onClick = {
                                            showCloudRestoreModal = true
                                            isInspectingCloud = true
                                            cloudRestorePreview = null
                                            coroutineScope.launch {
                                                val inspected = cloudSyncCoordinator.previewCloudRestore(
                                                    fileInfo = cloudFile,
                                                    callerContext = context
                                                )
                                                cloudRestorePreview = inspected
                                                isInspectingCloud = false
                                            }
                                        },
                                        isAccent = false,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 42.dp)
                                    ) {
                                        AuraIcon(
                                            AuraIcons.Download,
                                            contentDescription = "Restore Cloud Snapshot",
                                            size = AuraIconSize.Small,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Restore", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // Local History Header
                item {
                    Text(
                        "Backup History (${backups.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (backups.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neuInset(
                                    cornerRadius = 18.dp,
                                    backgroundColor = neu.surfaceRecessed,
                                    shadowOffset = 3.dp,
                                    blurRadius = 6.dp
                                )
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No backups created yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(backups, key = { it.id }) { bk ->
                        AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bk.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "${bk.noteCount} notes · ${bk.attachmentCount} files · ${bk.sizeBytes / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(bk.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AuraNeuIconButton(
                                        onClick = {
                                            val f = File(bk.filePath)
                                            if (f.exists()) {
                                                pendingSaveFile = f
                                                saveDocumentLauncher.launch(f.name)
                                            }
                                        },
                                        size = 40.dp,
                                        cornerRadius = 12.dp
                                    ) {
                                        AuraIcon(AuraIcons.Download, contentDescription = "Save to Files", size = AuraIconSize.Button)
                                    }
                                    AuraNeuIconButton(
                                        onClick = {
                                            val f = File(bk.filePath)
                                            if (f.exists()) shareBackup(f)
                                        },
                                        size = 40.dp,
                                        cornerRadius = 12.dp
                                    ) {
                                        AuraIcon(AuraIcons.Share2, contentDescription = "Share", size = AuraIconSize.Button)
                                    }
                                }
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

    // Restore Wizard Modal Dialog
    if (showRestoreModal && restorePreview != null) {
        val preview = restorePreview!!
        Dialog(onDismissRequest = {
            if (!isRestoring) {
                showRestoreModal = false
                restorePreview = null
            }
        }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .neuRaised(
                        cornerRadius = 26.dp,
                        backgroundColor = neu.surface,
                        shadowOffset = 8.dp,
                        blurRadius = 16.dp
                    )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AuraIconContainer(
                            imageVector = if (preview.isValid) AuraIcons.ShieldCheck else AuraIcons.AlertTriangle,
                            containerSize = 38.dp,
                            iconSize = AuraIconSize.Button,
                            cornerRadius = 11.dp,
                            tint = if (preview.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (preview.isValid) "Restore Archive" else "Invalid Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!preview.isValid) {
                        Text(
                            preview.error ?: "Failed to validate backup.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neuInset(
                                    cornerRadius = 14.dp,
                                    backgroundColor = neu.surfaceRecessed,
                                    shadowOffset = 2.5.dp,
                                    blurRadius = 5.dp
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Package: ${preview.manifest?.appName} (v${preview.manifest?.appVersion})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Notes: ${preview.notes.size} · Categories: ${preview.categories.size} · Media: ${preview.attachments.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (preview.conflictingNoteCount > 0) {
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AuraIcon(
                                        AuraIcons.AlertTriangle,
                                        contentDescription = null,
                                        tint = neu.warning,
                                        size = AuraIconSize.Small
                                    )
                                    Text(
                                        "${preview.conflictingNoteCount} existing note ID conflicts",
                                        color = neu.warning,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        AuraNeuDivider()

                        Text("Conflict Resolution", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RestoreOptionRow(
                                selected = selectedRestoreMode == "MERGE",
                                title = "Merge (Recommended)",
                                subtitle = "Keep existing notes and add missing items",
                                onClick = { selectedRestoreMode = "MERGE" }
                            )
                            RestoreOptionRow(
                                selected = selectedRestoreMode == "KEEP_BOTH",
                                title = "Keep Both",
                                subtitle = "Create new IDs for duplicate notes",
                                onClick = { selectedRestoreMode = "KEEP_BOTH" }
                            )
                            RestoreOptionRow(
                                selected = selectedRestoreMode == "CLEAN_REPLACE",
                                title = "Clean Replace",
                                subtitle = "Creates a safety snapshot first, then replaces vault",
                                isDestructive = true,
                                onClick = { selectedRestoreMode = "CLEAN_REPLACE" }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showRestoreModal = false
                                restorePreview = null
                            },
                            enabled = !isRestoring
                        ) {
                            Text("Cancel")
                        }

                        if (preview.isValid) {
                            Spacer(Modifier.width(8.dp))
                            AuraNeuButton(
                                onClick = {
                                    isRestoring = true
                                    coroutineScope.launch {
                                        val report = backupManager.executeRestore(preview, selectedRestoreMode)
                                        restoreReport = report
                                        isRestoring = false
                                        showRestoreModal = false
                                    }
                                },
                                enabled = !isRestoring,
                                isAccent = true
                            ) {
                                if (isRestoring) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Restore", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Restore Result Dialog
    restoreReport?.let { report ->
        AlertDialog(
            onDismissRequest = { restoreReport = null },
            containerColor = neu.surfaceElevated,
            icon = {
                AuraIconContainer(
                    imageVector = if (report.success) AuraIcons.CheckCircle2 else AuraIcons.AlertCircle,
                    containerSize = 44.dp,
                    iconSize = AuraIconSize.Card,
                    cornerRadius = 14.dp,
                    tint = if (report.success) neu.success else MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(if (report.success) "Restore Complete" else "Restore Failed")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(report.message)
                    if (report.success) {
                        Spacer(Modifier.height(6.dp))
                        Text("Notes Restored: ${report.notesRestored}", fontSize = 13.sp)
                        Text("Categories Restored: ${report.categoriesRestored}", fontSize = 13.sp)
                        Text("Attachments Restored: ${report.attachmentsRestored}", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                AuraNeuButton(onClick = { restoreReport = null }, isAccent = true) {
                    Text("Done", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    // Cloud Restore Wizard Dialog
    if (showCloudRestoreModal) {
        CloudRestoreWizardDialog(
            preview = cloudRestorePreview,
            isLoading = isInspectingCloud,
            isRestoring = isRestoringCloud,
            onDismiss = {
                showCloudRestoreModal = false
                cloudRestorePreview = null
            },
            onConfirmRestore = { mode: CloudRestoreMode, choices: Map<String, CloudNoteResolutionChoice>, restoreSettings: Boolean ->
                val preview = cloudRestorePreview ?: return@CloudRestoreWizardDialog
                isRestoringCloud = true
                coroutineScope.launch {
                    val result = cloudSyncCoordinator.executeCloudRestore(
                        preview = preview,
                        mode = mode,
                        conflictChoices = choices,
                        restoreSettings = restoreSettings
                    )
                    cloudRestoreReport = result
                    isRestoringCloud = false
                    showCloudRestoreModal = false
                    cloudRestorePreview = null
                }
            }
        )
    }

    // Cloud Restore Result Dialog
    cloudRestoreReport?.let { report ->
        AlertDialog(
            onDismissRequest = { cloudRestoreReport = null },
            containerColor = neu.surfaceElevated,
            icon = {
                AuraIconContainer(
                    imageVector = if (report.success) AuraIcons.CheckCircle2 else AuraIcons.AlertCircle,
                    containerSize = 44.dp,
                    iconSize = AuraIconSize.Card,
                    cornerRadius = 14.dp,
                    tint = if (report.success) neu.success else MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(if (report.success) "Cloud Restore Complete" else "Cloud Restore Failed")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(report.message)
                    if (report.success) {
                        Spacer(Modifier.height(6.dp))
                        Text("Notes Restored: ${report.notesRestored}", fontSize = 13.sp)
                        Text("Conflicts Resolved: ${report.conflictsResolved}", fontSize = 13.sp)
                        Text("Categories Restored: ${report.categoriesRestored}", fontSize = 13.sp)
                        Text("Settings Restored: ${if (report.settingsRestored) "Yes" else "Skipped"}", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                AuraNeuButton(onClick = { cloudRestoreReport = null }, isAccent = true) {
                    Text("Done", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Composable
private fun RestoreOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
