package com.example.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.auth.GoogleAuthManager
import com.example.data.local.AuraDatabase
import com.example.data.local.CloudSyncStatus
import com.example.data.local.PreferencesRepository
import com.example.domain.BackupManager

class DriveBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefsRepo = PreferencesRepository(applicationContext)
        val prefs = prefsRepo.getSnapshot()
        val authManager = GoogleAuthManager(applicationContext)

        try {
            val currentUser = authManager.currentUser()
            if (currentUser == null || !prefs.driveBackupEnabled) {
                prefsRepo.updateCloudSyncStatus(CloudSyncStatus.AUTH_REQUIRED, "Google Drive backup disabled or signed out.")
                return Result.success()
            }

            val kind = inputData.getString(NighttimeBackupScheduler.KEY_BACKUP_KIND)
                ?: NighttimeBackupScheduler.KIND_AUTO_SYNC
            val isNighttime = kind == NighttimeBackupScheduler.KIND_NIGHTTIME

            if (!prefs.hasPendingChanges && prefs.lastCloudBackupAt > 0L) {
                if (!isNighttime) {
                    return Result.success()
                }
                // For nighttime schedule, skip redundant upload if content was already synced recently
                val elapsed = System.currentTimeMillis() - prefs.lastCloudBackupAt
                if (elapsed < 12L * 60L * 60L * 1000L) {
                    prefsRepo.updateCloudSyncStatus(CloudSyncStatus.SYNCED, "")
                    return Result.success()
                }
            }

            val db = AuraDatabase.getInstance(applicationContext)
            val backupManager = BackupManager(
                context = applicationContext,
                noteDao = db.noteDao(),
                categoryDao = db.categoryDao(),
                attachmentDao = db.attachmentDao(),
                backupDao = db.backupDao()
            )
            val coordinator = CloudSyncCoordinator(
                context = applicationContext,
                authManager = authManager,
                driveClient = GoogleDriveClient(),
                noteDao = db.noteDao(),
                categoryDao = db.categoryDao(),
                attachmentDao = db.attachmentDao(),
                backupDao = db.backupDao(),
                backupManager = backupManager,
                preferencesRepository = prefsRepo
            )

            return when (
                coordinator.performCloudBackup(
                    force = false,
                    createHistorySnapshot = isNighttime,
                    interactiveAllowed = false
                )
            ) {
                is CloudSyncOutcome.Success -> Result.success()
                is CloudSyncOutcome.WaitingForCondition,
                is CloudSyncOutcome.RetryLater -> Result.retry()
                is CloudSyncOutcome.ResolutionRequired,
                is CloudSyncOutcome.Failed -> Result.failure()
            }
        } finally {
            authManager.dispose()
        }
    }
}
