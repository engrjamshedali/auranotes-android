package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.auth.GoogleUserProfile
import com.example.cloud.CloudNoteConflict
import com.example.cloud.CloudNoteResolutionChoice
import com.example.cloud.CloudRestoreMode
import com.example.cloud.CloudRestorePreview
import com.example.cloud.NighttimeBackupScheduler
import com.example.data.local.AuraUserPreferences
import com.example.data.local.CloudSyncStatus
import com.example.ui.screens.GoogleLogoMark
import com.example.ui.theme.LocalAuraNeumorphicPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GoogleDriveConnectionCard(
    userProfile: GoogleUserProfile?,
    preferences: AuraUserPreferences,
    syncStatus: CloudSyncStatus,
    isBackingUpNow: Boolean,
    isSigningInNow: Boolean,
    onSignInWithGoogle: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
    onToggleDriveBackup: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onRefreshCloudList: () -> Unit
) {
    val neu = LocalAuraNeumorphicPalette.current
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    val (badgeLabel, badgeColor) = when {
        userProfile == null -> "Not Signed In" to MaterialTheme.colorScheme.onSurfaceVariant
        !preferences.driveBackupEnabled -> "Backup Paused" to MaterialTheme.colorScheme.onSurfaceVariant
        isBackingUpNow || syncStatus == CloudSyncStatus.BACKING_UP -> "Backing up" to MaterialTheme.colorScheme.primary
        syncStatus == CloudSyncStatus.SYNCED -> "Synced" to neu.success
        syncStatus == CloudSyncStatus.WAITING_FOR_WIFI -> "Waiting for Wi-Fi" to neu.warning
        syncStatus == CloudSyncStatus.WAITING_FOR_IDLE -> "Waiting for idle" to neu.warning
        syncStatus == CloudSyncStatus.OFFLINE -> "Offline" to neu.warning
        syncStatus == CloudSyncStatus.BACKUP_FAILED -> "Backup failed" to MaterialTheme.colorScheme.error
        syncStatus == CloudSyncStatus.AUTH_REQUIRED -> "Auth required" to MaterialTheme.colorScheme.error
        else -> syncStatus.label to MaterialTheme.colorScheme.primary
    }

    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AuraIconContainer(
                        imageVector = if (preferences.driveBackupEnabled && userProfile != null) {
                            AuraIcons.Cloud
                        } else {
                            AuraIcons.CloudUpload
                        },
                        containerSize = 38.dp,
                        iconSize = AuraIconSize.Toolbar,
                        cornerRadius = 12.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Google Drive Sync", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Private appDataFolder",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .neuInset(
                            cornerRadius = 10.dp,
                            backgroundColor = neu.surfaceRecessed,
                            shadowOffset = 2.dp,
                            blurRadius = 4.dp
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            maxLines = 1
                        )
                    }
                }
            }

            if (isBackingUpNow || syncStatus == CloudSyncStatus.BACKING_UP) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = neu.surfaceRecessed
                )
            }

            if (userProfile == null) {
                Text(
                    "Sign in with Google to enable automatic night-time backups, SHA-256 verified cloud recovery, and account isolation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AuraNeuButton(
                    onClick = onSignInWithGoogle,
                    enabled = !isSigningInNow,
                    isAccent = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("backup_google_signin_button")
                ) {
                    if (isSigningInNow) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Signing in...", style = MaterialTheme.typography.labelLarge)
                    } else {
                        GoogleLogoMark(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Continue with Google",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // Account Row
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!userProfile.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userProfile.photoUrl,
                            contentDescription = "Google Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        AuraIconContainer(
                            imageVector = AuraIcons.UserRound,
                            containerSize = 42.dp,
                            iconSize = AuraIconSize.Button,
                            cornerRadius = 21.dp,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = userProfile.email,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AuraNeuIconButton(
                        onClick = onSwitchAccount,
                        size = 40.dp,
                        cornerRadius = 12.dp,
                        modifier = Modifier.testTag("drive_switch_account_button")
                    ) {
                        AuraIcon(
                            AuraIcons.ArrowLeftRight,
                            contentDescription = "Switch Google Account",
                            size = AuraIconSize.Small,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    AuraNeuIconButton(
                        onClick = { showDisconnectConfirm = true },
                        size = 40.dp,
                        cornerRadius = 12.dp,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("drive_disconnect_button")
                    ) {
                        AuraIcon(
                            AuraIcons.LogOut,
                            contentDescription = "Sign Out Google Account",
                            size = AuraIconSize.Small,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Enable Drive Backup Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            "Enable Google Drive Backup",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (preferences.driveBackupEnabled) {
                                "Least-privilege app folder sync is active"
                            } else {
                                "Grant drive.appdata access to automate cloud backups"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AuraNeuSwitch(
                        checked = preferences.driveBackupEnabled,
                        onCheckedChange = onToggleDriveBackup,
                        modifier = Modifier.testTag("drive_backup_toggle")
                    )
                }

                // Telemetry summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DriveStatBox(
                        label = "Last Cloud Backup",
                        value = if (preferences.lastCloudBackupAt > 0L) {
                            SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
                                .format(Date(preferences.lastCloudBackupAt))
                        } else {
                            "Never"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    DriveStatBox(
                        label = "Snapshot Size",
                        value = if (preferences.lastCloudBackupSizeBytes > 0L) {
                            formatReadableBytes(preferences.lastCloudBackupSizeBytes)
                        } else {
                            "—"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = if (preferences.backupFrequency == "OFF") {
                        "Night-time schedule is off · Pending changes: ${if (preferences.hasPendingChanges) "Yes" else "None"}"
                    } else {
                        "Night window: ${NighttimeBackupScheduler.formatWindowLabel(preferences.nightWindowStartHour, preferences.nightWindowEndHour)}" +
                            if (preferences.hasPendingChanges) " · Changes queued" else ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(visible = preferences.lastCloudBackupError.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neuInset(
                                cornerRadius = 12.dp,
                                backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                                shadowOffset = 2.dp,
                                blurRadius = 4.dp
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuraIcon(
                            AuraIcons.AlertCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            size = AuraIconSize.Small
                        )
                        Text(
                            text = preferences.lastCloudBackupError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isRetry = syncStatus == CloudSyncStatus.BACKUP_FAILED || syncStatus == CloudSyncStatus.AUTH_REQUIRED
                    AuraNeuButton(
                        onClick = onBackupNow,
                        enabled = !isBackingUpNow,
                        isAccent = true,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("drive_backup_now_button")
                    ) {
                        if (isBackingUpNow) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing...", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        } else {
                            AuraIcon(
                                if (isRetry) AuraIcons.RefreshCw else AuraIcons.CloudUpload,
                                contentDescription = null,
                                size = AuraIconSize.Button,
                                tint = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isRetry) "Retry Backup" else "Backup Now",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    AuraNeuButton(
                        onClick = onRefreshCloudList,
                        enabled = !isBackingUpNow && preferences.driveBackupEnabled,
                        isAccent = false,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("drive_refresh_list_button")
                    ) {
                        AuraIcon(
                            AuraIcons.RefreshCw,
                            contentDescription = "Refresh Google Drive Backups",
                            size = AuraIconSize.Button,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            containerColor = neu.surfaceElevated,
            title = { Text("Sign Out of Google?") },
            text = {
                Text(
                    "Your local notes, code scripts, and media stay safe on this device. Scheduled Google Drive backups will pause until you sign back in.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                AuraNeuButton(
                    onClick = {
                        showDisconnectConfirm = false
                        onSignOut()
                    },
                    isAccent = true
                ) {
                    Text("Sign Out", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NighttimeScheduleCard(
    preferences: AuraUserPreferences,
    onSetBackupFrequency: (String) -> Unit,
    onSetNightWindow: (Int, Int) -> Unit,
    onSetAutoSyncOnChange: (Boolean) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetRequireCharging: (Boolean) -> Unit,
    onSetRequireIdle: (Boolean) -> Unit
) {
    val selectedWindowKey = when (preferences.nightWindowStartHour) {
        22 -> "22_3"
        2 -> "2_7"
        else -> "0_5"
    }

    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuraIconContainer(
                    imageVector = AuraIcons.Moon,
                    containerSize = 38.dp,
                    iconSize = AuraIconSize.Toolbar,
                    cornerRadius = 12.dp,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Smart Night-Time Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Scheduled automatically via WorkManager",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AuraNeuSegmentedControl(
                options = listOf(
                    "DAILY" to "Daily",
                    "WEEKLY" to "Weekly",
                    "MONTHLY" to "Monthly",
                    "OFF" to "Off"
                ),
                selectedOption = preferences.backupFrequency,
                onSelect = onSetBackupFrequency
            )

            Text(
                "Preferred Night Window",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AuraNeuSegmentedControl(
                options = listOf(
                    "0_5" to "12–5 AM",
                    "22_3" to "10P–3A",
                    "2_7" to "2–7 AM"
                ),
                selectedOption = selectedWindowKey,
                onSelect = { key ->
                    when (key) {
                        "22_3" -> onSetNightWindow(22, 3)
                        "2_7" -> onSetNightWindow(2, 7)
                        else -> onSetNightWindow(0, 5)
                    }
                }
            )

            AuraNeuDivider()

            ScheduleToggleRow(
                title = "Auto-Sync Local Edits",
                subtitle = "Queue debounced cloud sync shortly after note changes",
                checked = preferences.autoSyncOnChange,
                onCheckedChange = onSetAutoSyncOnChange
            )
            ScheduleToggleRow(
                title = "Wi-Fi Only Backup",
                subtitle = "Avoid metered mobile data; pause until unmetered Wi-Fi",
                checked = preferences.wifiOnlyBackup,
                onCheckedChange = onSetWifiOnly
            )
            ScheduleToggleRow(
                title = "Night Sync Only While Charging",
                subtitle = "Protect battery during scheduled overnight backups",
                checked = preferences.requireChargingForNightBackup,
                onCheckedChange = onSetRequireCharging
            )
            ScheduleToggleRow(
                title = "Prefer Device Idle",
                subtitle = "Run heavy night uploads while the phone is untouched",
                checked = preferences.requireIdleForNightBackup,
                onCheckedChange = onSetRequireIdle
            )

            Text(
                "Note: Android OS manages exact background execution windows for battery optimization.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CloudRestoreWizardDialog(
    preview: CloudRestorePreview?,
    isLoading: Boolean,
    isRestoring: Boolean,
    onDismiss: () -> Unit,
    onConfirmRestore: (
        mode: CloudRestoreMode,
        conflictChoices: Map<String, CloudNoteResolutionChoice>,
        restoreSettings: Boolean
    ) -> Unit
) {
    val neu = LocalAuraNeumorphicPalette.current
    var selectedMode by remember(preview) { mutableStateOf(CloudRestoreMode.SMART_MERGE) }
    var restoreSettings by remember(preview) { mutableStateOf(true) }
    val conflictChoices = remember(preview) {
        mutableStateMapOf<String, CloudNoteResolutionChoice>().apply {
            preview?.conflicts?.forEach { put(it.noteId, it.defaultChoice) }
        }
    }

    Dialog(onDismissRequest = { if (!isRestoring) onDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .neuRaised(
                    cornerRadius = 26.dp,
                    backgroundColor = neu.surface,
                    shadowOffset = 8.dp,
                    blurRadius = 16.dp
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AuraIconContainer(
                        imageVector = if (preview == null || preview.isValid) AuraIcons.ShieldCheck else AuraIcons.AlertTriangle,
                        containerSize = 38.dp,
                        iconSize = AuraIconSize.Button,
                        cornerRadius = 12.dp,
                        tint = if (preview != null && !preview.isValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when {
                            isLoading -> "Inspecting Drive Backup..."
                            preview?.isValid == true -> "Restore from Google Drive"
                            else -> "Invalid Cloud Backup"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text(
                                "Downloading & verifying SHA-256 checksum...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (preview != null && !preview.isValid) {
                    Text(
                        text = preview.error ?: "Unable to verify cloud backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (preview != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Integrity & Metadata Summary
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
                            Text(
                                "Schema v${preview.schemaVersion} · SHA-256 Verified",
                                style = MaterialTheme.typography.labelLarge,
                                color = neu.success
                            )
                            Text(
                                "Notes: ${preview.notes.size} (${preview.newFromCloudCount} new, ${preview.identicalCount} unchanged, ${preview.conflicts.size} conflicts)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Categories: ${preview.categories.size} · Snapshot: ${
                                    SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
                                        .format(Date(preview.exportedAtMillis))
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Restore strategy selector
                        Text("Restore Strategy", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        AuraNeuSegmentedControl(
                            options = listOf(
                                "SMART_MERGE" to "Smart Merge",
                                "KEEP_BOTH" to "Keep Both",
                                "CLEAN_REPLACE" to "Replace"
                            ),
                            selectedOption = selectedMode.name,
                            onSelect = { selectedMode = CloudRestoreMode.valueOf(it) }
                        )

                        if (preview.conflicts.isNotEmpty() && selectedMode == CloudRestoreMode.SMART_MERGE) {
                            Text(
                                "Resolve ${preview.conflicts.size} Note Conflict${if (preview.conflicts.size == 1) "" else "s"}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = neu.warning
                            )
                            preview.conflicts.forEach { conflict ->
                                ConflictResolutionCard(
                                    conflict = conflict,
                                    selectedChoice = conflictChoices[conflict.noteId] ?: conflict.defaultChoice,
                                    onSelectChoice = { conflictChoices[conflict.noteId] = it }
                                )
                            }
                        }

                        if (preview.settings != null) {
                            ScheduleToggleRow(
                                title = "Restore App Theme & Defaults",
                                subtitle = "Theme: ${preview.settings.themeMode}, Format: ${preview.settings.defaultNoteType}",
                                checked = restoreSettings,
                                onCheckedChange = { restoreSettings = it }
                            )
                        }

                        Text(
                            "Safety Guarantee: AuraNotes automatically creates a local pre-restore snapshot before modifying your library.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isRestoring
                    ) {
                        Text("Cancel")
                    }
                    if (!isLoading && preview?.isValid == true) {
                        Spacer(Modifier.width(8.dp))
                        AuraNeuButton(
                            onClick = {
                                onConfirmRestore(selectedMode, conflictChoices.toMap(), restoreSettings)
                            },
                            enabled = !isRestoring,
                            isAccent = true,
                            modifier = Modifier.testTag("confirm_cloud_restore_button")
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Restore Now", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictResolutionCard(
    conflict: CloudNoteConflict,
    selectedChoice: CloudNoteResolutionChoice,
    onSelectChoice: (CloudNoteResolutionChoice) -> Unit
) {
    val neu = LocalAuraNeumorphicPalette.current
    val fmt = remember { SimpleDateFormat("MMM d HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neuInset(
                cornerRadius = 14.dp,
                backgroundColor = neu.surfaceRecessed,
                shadowOffset = 2.dp,
                blurRadius = 4.dp
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = conflict.cloudNote.title.ifBlank { conflict.localNote.title.ifBlank { "Untitled Note" } },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Local: ${fmt.format(Date(conflict.localNote.updatedAt))} · Cloud: ${fmt.format(Date(conflict.cloudNote.updatedAt))}" +
                if (conflict.cloudIsNewer) " (Cloud newer)" else " (Local newer)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                CloudNoteResolutionChoice.USE_CLOUD to "Cloud",
                CloudNoteResolutionChoice.KEEP_LOCAL to "Local",
                CloudNoteResolutionChoice.KEEP_BOTH to "Both"
            ).forEach { (choice, label) ->
                val isSelected = selectedChoice == choice
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else neu.surface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else neu.darkShadow.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectChoice(choice) }
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val neu = LocalAuraNeumorphicPalette.current
    Column(
        modifier = modifier
            .neuInset(
                cornerRadius = 14.dp,
                backgroundColor = neu.surfaceRecessed,
                shadowOffset = 2.5.dp,
                blurRadius = 5.dp
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScheduleToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AuraNeuSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

fun formatReadableBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}
