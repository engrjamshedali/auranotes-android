package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.data.local.BackupEntity
import com.example.domain.BackupManager
import com.example.domain.RestorePreview
import com.example.domain.RestoreReport
import com.example.ui.components.AuraGlassCard
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupCenterScreen(
    backups: List<BackupEntity>,
    backupManager: BackupManager,
    backupFrequency: String,
    onSetBackupFrequency: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isExporting by remember { mutableStateOf(false) }
    var isInspecting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }

    var lastExportedFile by remember { mutableStateOf<File?>(null) }
    var restorePreview by remember { mutableStateOf<RestorePreview?>(null) }
    var restoreReport by remember { mutableStateOf<RestoreReport?>(null) }
    var showRestoreModal by remember { mutableStateOf(false) }
    var selectedRestoreMode by remember { mutableStateOf("MERGE") } // MERGE, KEEP_BOTH, CLEAN_REPLACE

    // Backup file picker for restore
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
            context.startActivity(Intent.createChooser(intent, "Share AuraNotes Backup"))
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Backup & Migration Center",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Zero cloud required · Portable .auranotes packages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Actions Card
            item {
                AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("AuraNotes Portable Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            "Export your complete database, notes, rich-text formatting, categories, and media vault attachments into a single verified .auranotes ZIP package. Transfer to any Android phone and restore without an account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { triggerManualBackup() },
                                enabled = !isExporting,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Upload, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create Backup")
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                },
                                enabled = !isInspecting,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isInspecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restore Backup")
                                }
                            }
                        }

                        // Last exported notification banner
                        lastExportedFile?.let { file ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Backup Ready: ${file.name}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text("${file.length() / 1024} KB written successfully", fontSize = 11.sp)
                                    }
                                    FilledIconButton(onClick = { shareBackup(file) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Scheduled Backup Settings Card
            item {
                AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Automatic Backup Scheduler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            "Periodic backups keep snapshots of your vault safe in app storage. Android battery optimization may slightly delay periodic tasks to optimize system efficiency.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("WEEKLY", "DAILY", "MONTHLY", "OFF").forEach { freq ->
                                FilterChip(
                                    selected = backupFrequency == freq,
                                    onClick = { onSetBackupFrequency(freq) },
                                    label = { Text(freq) }
                                )
                            }
                        }
                    }
                }
            }

            // Backup History Header
            item {
                Text(
                    "Backup History (${backups.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // History Items
            if (backups.isEmpty()) {
                item {
                    Text(
                        "No backups created yet. Tap 'Create Backup' above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(backups, key = { it.id }) { bk ->
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bk.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${bk.noteCount} notes · ${bk.attachmentCount} attachments · ${bk.sizeBytes / 1024} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(bk.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            Row {
                                IconButton(onClick = {
                                    val f = File(bk.filePath)
                                    if (f.exists()) shareBackup(f)
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full Restore Wizard Modal Dialog
    if (showRestoreModal && restorePreview != null) {
        val preview = restorePreview!!
        Dialog(onDismissRequest = {
            if (!isRestoring) {
                showRestoreModal = false
                restorePreview = null
            }
        }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (preview.isValid) Icons.Default.Verified else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (preview.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (preview.isValid) "AuraNotes Restore Wizard" else "Invalid Backup File",
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
                        // Manifest Details Summary
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Package: ${preview.manifest?.appName} (v${preview.manifest?.appVersion})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Notes to restore: ${preview.notes.size}", fontSize = 12.sp)
                            Text("Categories: ${preview.categories.size}", fontSize = 12.sp)
                            Text("Media Attachments: ${preview.attachments.size}", fontSize = 12.sp)
                            if (preview.conflictingNoteCount > 0) {
                                Text(
                                    "⚠️ Found ${preview.conflictingNoteCount} existing note ID conflicts",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider()

                        Text("Select Conflict Resolution Strategy:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                        // Mode selection options
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedRestoreMode == "MERGE",
                                    onClick = { selectedRestoreMode = "MERGE" }
                                )
                                Column {
                                    Text("Merge (Recommended)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Keep existing notes, add new ones without overwriting", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedRestoreMode == "KEEP_BOTH",
                                    onClick = { selectedRestoreMode = "KEEP_BOTH" }
                                )
                                Column {
                                    Text("Keep Both", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Generate new IDs for imported duplicates", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedRestoreMode == "CLEAN_REPLACE",
                                    onClick = { selectedRestoreMode = "CLEAN_REPLACE" }
                                )
                                Column {
                                    Text("Clean Replace", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Safety backup created first, replaces existing vault", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
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
                            Button(
                                onClick = {
                                    isRestoring = true
                                    coroutineScope.launch {
                                        val report = backupManager.executeRestore(preview, selectedRestoreMode)
                                        restoreReport = report
                                        isRestoring = false
                                        showRestoreModal = false
                                    }
                                },
                                enabled = !isRestoring
                            ) {
                                if (isRestoring) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Execute Restore")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Success / Result Report Dialog
    restoreReport?.let { report ->
        AlertDialog(
            onDismissRequest = { restoreReport = null },
            icon = {
                Icon(
                    if (report.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (report.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(if (report.success) "Restore Completed Successfully" else "Restore Failed")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(report.message)
                    if (report.success) {
                        Spacer(Modifier.height(6.dp))
                        Text("• Notes Restored: ${report.notesRestored}", fontSize = 13.sp)
                        Text("• Categories Restored: ${report.categoriesRestored}", fontSize = 13.sp)
                        Text("• Attachments Restored: ${report.attachmentsRestored}", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { restoreReport = null }) {
                    Text("OK")
                }
            }
        )
    }
}
