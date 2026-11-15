package com.example.cloud

import android.content.Context
import android.content.IntentSender
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.auth.CloudAuthProvider
import com.example.auth.DriveAuthorizationOutcome
import com.example.data.local.AttachmentDao
import com.example.data.local.BackupDao
import com.example.data.local.BackupEntity
import com.example.data.local.CategoryDao
import com.example.data.local.CloudSyncStatus
import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import com.example.data.local.PreferencesRepository
import com.example.domain.BackupManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed interface CloudSyncOutcome {
    data class Success(
        val message: String,
        val uploadedBytes: Long = 0L,
        val skippedUnchanged: Boolean = false
    ) : CloudSyncOutcome

    data class ResolutionRequired(val intentSender: IntentSender) : CloudSyncOutcome
    data class WaitingForCondition(val status: CloudSyncStatus, val message: String) : CloudSyncOutcome
    data class RetryLater(val status: CloudSyncStatus, val message: String) : CloudSyncOutcome
    data class Failed(val status: CloudSyncStatus, val message: String) : CloudSyncOutcome
}

class CloudSyncCoordinator(
    private val context: Context,
    private val authManager: CloudAuthProvider,
    private val driveClient: DriveRemoteStorage,
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val attachmentDao: AttachmentDao,
    private val backupDao: BackupDao,
    private val backupManager: BackupManager,
    private val preferencesRepository: PreferencesRepository
) {
    companion object {
        private val syncMutex = Mutex()
        private const val HISTORY_SNAPSHOT_MIN_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 hours
    }

    enum class NetworkAvailability {
        UNMETERED_WIFI,
        METERED_CELLULAR,
        OFFLINE
    }

    fun checkNetworkAvailability(): NetworkAvailability {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkAvailability.OFFLINE
            val activeNetwork = cm.activeNetwork ?: return NetworkAvailability.OFFLINE
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkAvailability.OFFLINE
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (!hasInternet) return NetworkAvailability.OFFLINE
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                NetworkAvailability.UNMETERED_WIFI
            } else {
                NetworkAvailability.METERED_CELLULAR
            }
        } catch (_: Exception) {
            NetworkAvailability.OFFLINE
        }
    }

    suspend fun performCloudBackup(
        force: Boolean,
        createHistorySnapshot: Boolean,
        interactiveAllowed: Boolean,
        callerContext: Context = context
    ): CloudSyncOutcome = syncMutex.withLock {
        val user = authManager.currentUser()
        if (user == null) {
            preferencesRepository.updateCloudSyncStatus(CloudSyncStatus.AUTH_REQUIRED, "Sign in with Google to back up.")
            return@withLock CloudSyncOutcome.Failed(CloudSyncStatus.AUTH_REQUIRED, "Authentication required.")
        }

        val prefs = preferencesRepository.getSnapshot()
        if (!prefs.driveBackupEnabled && !force) {
            return@withLock CloudSyncOutcome.Failed(CloudSyncStatus.AUTH_REQUIRED, "Google Drive backup is disabled.")
        }

        // Check network conditions before calling Google Drive
        val netState = checkNetworkAvailability()
        if (netState == NetworkAvailability.OFFLINE) {
            preferencesRepository.updateCloudSyncStatus(CloudSyncStatus.OFFLINE, "Waiting for internet connection.")
            return@withLock CloudSyncOutcome.WaitingForCondition(
                CloudSyncStatus.OFFLINE,
                "Device is offline. Changes are saved locally and queued for sync."
            )
        }
        if (prefs.wifiOnlyBackup && !force && netState == NetworkAvailability.METERED_CELLULAR) {
            preferencesRepository.updateCloudSyncStatus(
                CloudSyncStatus.WAITING_FOR_WIFI,
                "Waiting for unmetered Wi-Fi."
            )
            return@withLock CloudSyncOutcome.WaitingForCondition(
                CloudSyncStatus.WAITING_FOR_WIFI,
                "Waiting for Wi-Fi to upload backup."
            )
        }

        // Acquire short-lived Google Drive AppData OAuth token
        val authOutcome = authManager.authorizeDriveAppData(
            context = callerContext,
            interactiveAllowed = interactiveAllowed
        )
        val accessToken = when (authOutcome) {
            is DriveAuthorizationOutcome.Authorized -> authOutcome.accessToken
            is DriveAuthorizationOutcome.ResolutionRequired -> {
                preferencesRepository.updateCloudSyncStatus(
                    CloudSyncStatus.AUTH_REQUIRED,
                    "Google Drive permission needed."
                )
                return@withLock CloudSyncOutcome.ResolutionRequired(authOutcome.intentSender)
            }
            is DriveAuthorizationOutcome.Failed -> {
                val targetStatus = if (authOutcome.isAuthRequired) CloudSyncStatus.AUTH_REQUIRED else CloudSyncStatus.BACKUP_FAILED
                preferencesRepository.updateCloudSyncStatus(targetStatus, authOutcome.message)
                return@withLock if (authOutcome.isAuthRequired) {
                    CloudSyncOutcome.Failed(targetStatus, authOutcome.message)
                } else {
                    CloudSyncOutcome.RetryLater(targetStatus, authOutcome.message)
                }
            }
        }

        val now = System.currentTimeMillis()
        val notes = noteDao.getAllNotesSync()
        val categories = categoryDao.getAllCategoriesSync()
        val attachments = attachmentDao.getAllAttachmentsSync()
        val targetRevision = prefs.localDataRevision

        val payload = CloudBackupSerializer.buildBackupJson(
            userId = user.uid,
            userEmail = user.email,
            notes = notes,
            categories = categories,
            attachments = attachments,
            preferences = prefs,
            timestampMillis = now
        )

        // Avoid unnecessary full uploads when content checksum matches and not forced
        if (!force && prefs.lastCloudBackupChecksum == payload.contentChecksumSha256 && prefs.lastCloudBackupAt > 0L) {
            preferencesRepository.recordCloudBackupSuccess(
                timestamp = prefs.lastCloudBackupAt,
                sizeBytes = prefs.lastCloudBackupSizeBytes,
                checksum = payload.contentChecksumSha256,
                syncedRevision = targetRevision
            )
            return@withLock CloudSyncOutcome.Success(
                message = "Already up to date with Google Drive.",
                skippedUnchanged = true
            )
        }

        preferencesRepository.updateCloudSyncStatus(CloudSyncStatus.BACKING_UP, "")

        return@withLock executeUploadWithRetry(
            accessToken = accessToken,
            callerContext = callerContext,
            interactiveAllowed = interactiveAllowed,
            userId = user.uid,
            userEmail = user.email,
            payload = payload,
            targetRevision = targetRevision,
            now = now,
            createHistorySnapshot = createHistorySnapshot || (now - prefs.lastCloudBackupAt > HISTORY_SNAPSHOT_MIN_INTERVAL_MS),
            retentionCount = prefs.backupRetentionCount,
            attachmentCount = attachments.size
        )
    }

    private suspend fun executeUploadWithRetry(
        accessToken: String,
        callerContext: Context,
        interactiveAllowed: Boolean,
        userId: String,
        userEmail: String,
        payload: CloudBackupPayloadResult,
        targetRevision: Long,
        now: Long,
        createHistorySnapshot: Boolean,
        retentionCount: Int,
        attachmentCount: Int
    ): CloudSyncOutcome {
        var token = accessToken
        for (attempt in 0..1) {
            try {
                val latestInfo = driveClient.uploadOrUpdateLatest(
                    accessToken = token,
                    userId = userId,
                    userEmail = userEmail,
                    payload = payload
                )

                var finalSize = latestInfo.sizeBytes.takeIf { it > 0L } ?: payload.jsonString.toByteArray().size.toLong()
                var recordedName = CloudBackupSerializer.LATEST_BACKUP_FILENAME

                if (createHistorySnapshot) {
                    val histName = CloudBackupSerializer.historyFilenameFor(now)
                    val histInfo = driveClient.uploadHistorySnapshot(
                        accessToken = token,
                        fileName = histName,
                        userId = userId,
                        userEmail = userEmail,
                        payload = payload
                    )
                    recordedName = histInfo.name
                    if (histInfo.sizeBytes > 0L) finalSize = histInfo.sizeBytes
                    pruneOldHistoryBackups(token, userId, retentionCount)
                }

                preferencesRepository.recordCloudBackupSuccess(
                    timestamp = now,
                    sizeBytes = finalSize,
                    checksum = payload.contentChecksumSha256,
                    syncedRevision = targetRevision
                )

                backupDao.insertBackup(
                    BackupEntity(
                        id = "cloud_" + UUID.randomUUID().toString().take(8),
                        name = "Drive · $recordedName",
                        filePath = "drive://appDataFolder/$recordedName",
                        noteCount = payload.noteCount,
                        attachmentCount = attachmentCount,
                        sizeBytes = finalSize,
                        backupType = "CLOUD_DRIVE",
                        status = "SUCCESS",
                        createdAt = now
                    )
                )

                return CloudSyncOutcome.Success(
                    message = "Synced ${payload.noteCount} notes to Google Drive AppData.",
                    uploadedBytes = finalSize
                )
            } catch (e: DriveApiException.Unauthorized) {
                if (attempt == 0) {
                    // Token expired -> request fresh access token once via AuthorizationClient
                    val refreshed = authManager.authorizeDriveAppData(callerContext, interactiveAllowed = interactiveAllowed)
                    if (refreshed is DriveAuthorizationOutcome.Authorized) {
                        token = refreshed.accessToken
                        continue
                    } else if (refreshed is DriveAuthorizationOutcome.ResolutionRequired) {
                        preferencesRepository.updateCloudSyncStatus(CloudSyncStatus.AUTH_REQUIRED, "Re-authorization required.")
                        return CloudSyncOutcome.ResolutionRequired(refreshed.intentSender)
                    }
                }
                preferencesRepository.updateCloudSyncStatus(CloudSyncStatus.AUTH_REQUIRED, e.message ?: "Session expired")
                return CloudSyncOutcome.Failed(CloudSyncStatus.AUTH_REQUIRED, e.message ?: "Authentication required.")
            } catch (e: DriveApiException.Network) {
                preferencesRepository.updateCloudSyncStatus(CloudSyncStatus.OFFLINE, e.message ?: "Offline")
                return CloudSyncOutcome.RetryLater(CloudSyncStatus.OFFLINE, e.message ?: "No internet connection.")
            } catch (e: DriveApiException.TransientServer) {
                preferencesRepository.updateCloudSyncStatus(CloudSyncStatus.BACKUP_FAILED, e.message ?: "Drive unavailable")
                return CloudSyncOutcome.RetryLater(CloudSyncStatus.BACKUP_FAILED, e.message ?: "Temporary Drive error; will retry.")
            } catch (e: DriveApiException.QuotaOrPermission) {
                val status = if (e.isQuotaExceeded) CloudSyncStatus.BACKUP_FAILED else CloudSyncStatus.AUTH_REQUIRED
                preferencesRepository.updateCloudSyncStatus(status, e.message ?: "Drive permission or quota error")
                return CloudSyncOutcome.Failed(status, e.message ?: "Drive permission or quota error.")
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Cloud backup failed."
                preferencesRepository.updateCloudSyncStatus(CloudSyncStatus.BACKUP_FAILED, msg)
                return CloudSyncOutcome.RetryLater(CloudSyncStatus.BACKUP_FAILED, msg)
            }
        }
        return CloudSyncOutcome.RetryLater(CloudSyncStatus.BACKUP_FAILED, "Could not complete Drive backup.")
    }

    private suspend fun pruneOldHistoryBackups(accessToken: String, userId: String, keepCount: Int) {
        try {
            val all = driveClient.listBackups(accessToken, userId)
            val historyOnly = all
                .filter { it.kind == "history" && it.name != CloudBackupSerializer.LATEST_BACKUP_FILENAME }
                .sortedByDescending { it.modifiedTimeMillis }
            if (historyOnly.size > keepCount.coerceAtLeast(3)) {
                historyOnly.drop(keepCount.coerceAtLeast(3)).forEach { old ->
                    driveClient.deleteBackup(accessToken, old.fileId)
                }
            }
        } catch (_: Exception) {
            // Non-critical cleanup failure
        }
    }

    suspend fun fetchCloudBackups(
        callerContext: Context = context,
        interactiveAllowed: Boolean = true
    ): Result<List<CloudBackupFileInfo>> {
        val user = authManager.currentUser()
            ?: return Result.failure(IllegalStateException("Sign in with Google first."))
        if (checkNetworkAvailability() == NetworkAvailability.OFFLINE) {
            return Result.failure(DriveApiException.Network("Device is offline."))
        }

        return when (val authOutcome = authManager.authorizeDriveAppData(callerContext, interactiveAllowed)) {
            is DriveAuthorizationOutcome.Authorized -> {
                try {
                    val files = driveClient.listBackups(authOutcome.accessToken, user.uid)
                    Result.success(files)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            is DriveAuthorizationOutcome.ResolutionRequired -> {
                Result.failure(DriveResolutionException(authOutcome.intentSender))
            }
            is DriveAuthorizationOutcome.Failed -> {
                Result.failure(IllegalStateException(authOutcome.message))
            }
        }
    }

    suspend fun previewCloudRestore(
        fileInfo: CloudBackupFileInfo,
        callerContext: Context = context
    ): CloudRestorePreview {
        val user = authManager.currentUser()
            ?: return CloudRestorePreview(isValid = false, error = "Sign in with Google to inspect cloud backups.")

        val authOutcome = authManager.authorizeDriveAppData(callerContext, interactiveAllowed = true)
        val token = when (authOutcome) {
            is DriveAuthorizationOutcome.Authorized -> authOutcome.accessToken
            else -> return CloudRestorePreview(isValid = false, error = "Google Drive authorization required.")
        }

        return try {
            val json = driveClient.downloadBackup(token, fileInfo.fileId)
            val localNotes = noteDao.getAllNotesSync()
            CloudBackupSerializer.parseAndAnalyzeBackup(
                rawJson = json,
                fileInfo = fileInfo,
                localNotes = localNotes,
                expectedUserId = user.uid
            )
        } catch (e: Exception) {
            CloudRestorePreview(
                isValid = false,
                error = "Failed to download cloud backup: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    /**
     * Executes safe restoration with an automatic pre-restore recovery snapshot
     * and non-destructive conflict handling.
     */
    suspend fun executeCloudRestore(
        preview: CloudRestorePreview,
        mode: CloudRestoreMode,
        conflictChoices: Map<String, CloudNoteResolutionChoice> = emptyMap(),
        restoreSettings: Boolean = true
    ): CloudRestoreReport {
        if (!preview.isValid) {
            return CloudRestoreReport(
                success = false,
                notesRestored = 0,
                conflictsResolved = 0,
                categoriesRestored = 0,
                settingsRestored = false,
                message = preview.error ?: "Invalid backup preview."
            )
        }

        // Always create a local recovery point before modifying local database
        val safetyFile = backupManager.createCompleteBackup(backupType = "SAFETY_PRE_RESTORE")

        return try {
            preview.categories.forEach { categoryDao.insertCategory(it) }

            val localById = noteDao.getAllNotesSync().associateBy { it.id }
            val conflictById = preview.conflicts.associateBy { it.noteId }

            var restoredCount = 0
            var conflictsResolved = 0

            if (mode == CloudRestoreMode.CLEAN_REPLACE) {
                localById.keys.forEach { noteDao.deleteNotePermanently(it) }
                preview.notes.forEach { cloudNote ->
                    noteDao.insertNote(cloudNote)
                    restoredCount++
                }
                conflictsResolved = preview.conflicts.size
            } else {
                preview.notes.forEach { cloudNote ->
                    val localNote = localById[cloudNote.id]
                    if (localNote == null) {
                        noteDao.insertNote(cloudNote)
                        restoredCount++
                    } else if (mode == CloudRestoreMode.KEEP_BOTH) {
                        val conflict = conflictById[cloudNote.id]
                        if (conflict != null) {
                            val copyNote = cloudNote.copy(
                                id = "note_" + UUID.randomUUID().toString().take(8),
                                title = "${cloudNote.title.ifBlank { "Untitled" }} (Cloud Copy)"
                            )
                            noteDao.insertNote(copyNote)
                            restoredCount++
                            conflictsResolved++
                        }
                    } else {
                        // SMART_MERGE: respect explicit user choices or preserve newer/conflicting copy safely
                        val conflict = conflictById[cloudNote.id]
                        if (conflict != null) {
                            val choice = conflictChoices[cloudNote.id] ?: conflict.defaultChoice
                            when (choice) {
                                CloudNoteResolutionChoice.USE_CLOUD -> {
                                    // Preserve local copy if local was actually edited more recently
                                    if (!conflict.cloudIsNewer) {
                                        val safetyCopy = localNote.copy(
                                            id = "note_" + UUID.randomUUID().toString().take(8),
                                            title = "${localNote.title.ifBlank { "Untitled" }} (Local Conflict)"
                                        )
                                        noteDao.insertNote(safetyCopy)
                                    }
                                    noteDao.insertNote(cloudNote)
                                    restoredCount++
                                    conflictsResolved++
                                }
                                CloudNoteResolutionChoice.KEEP_BOTH -> {
                                    val copyNote = cloudNote.copy(
                                        id = "note_" + UUID.randomUUID().toString().take(8),
                                        title = "${cloudNote.title.ifBlank { "Untitled" }} (Cloud Copy)"
                                    )
                                    noteDao.insertNote(copyNote)
                                    restoredCount++
                                    conflictsResolved++
                                }
                                CloudNoteResolutionChoice.KEEP_LOCAL -> {
                                    conflictsResolved++
                                }
                            }
                        }
                    }
                }
            }

            var appliedSettings = false
            if (restoreSettings && preview.settings != null) {
                preferencesRepository.applyRestoredSettings(
                    themeMode = preview.settings.themeMode,
                    accentIndex = preview.settings.accentIndex,
                    editorFontSize = preview.settings.editorFontSize,
                    backupFrequency = preview.settings.backupFrequency,
                    defaultNoteType = preview.settings.defaultNoteType
                )
                appliedSettings = true
            }

            preferencesRepository.bumpLocalDataRevision()

            CloudRestoreReport(
                success = true,
                notesRestored = restoredCount,
                conflictsResolved = conflictsResolved,
                categoriesRestored = preview.categories.size,
                settingsRestored = appliedSettings,
                safetyBackupName = safetyFile?.name,
                message = buildString {
                    append("Restored $restoredCount note${if (restoredCount == 1) "" else "s"}")
                    if (conflictsResolved > 0) append(", resolved $conflictsResolved conflict${if (conflictsResolved == 1) "" else "s"}")
                    if (safetyFile != null) append(". Safety snapshot saved (${safetyFile.name}).") else append(".")
                }
            )
        } catch (e: Exception) {
            CloudRestoreReport(
                success = false,
                notesRestored = 0,
                conflictsResolved = 0,
                categoriesRestored = 0,
                settingsRestored = false,
                safetyBackupName = safetyFile?.name,
                message = "Cloud restore failed: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }
}

class DriveResolutionException(val intentSender: IntentSender) : Exception("Google Drive consent required")
