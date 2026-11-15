package com.example.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.AuraUserPreferences
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NighttimeBackupScheduler {

    const val UNIQUE_NIGHT_BACKUP_WORK = "auranotes_nighttime_backup"
    const val UNIQUE_CHANGE_SYNC_WORK = "auranotes_immediate_cloud_sync"
    const val KEY_BACKUP_KIND = "backup_kind"
    const val KIND_NIGHTTIME = "NIGHTTIME"
    const val KIND_AUTO_SYNC = "AUTO_SYNC"

    /**
     * Computes milliseconds until the start of the user's preferred local nighttime window
     * (default 12:00 AM - 5:00 AM). If already inside the window, returns a short 1-minute delay.
     */
    fun computeInitialDelayMillis(
        startHour: Int,
        endHour: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val normalizedStart = startHour.coerceIn(0, 23)
        val normalizedEnd = endHour.coerceIn(0, 23)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)

        val insideWindow = if (normalizedStart < normalizedEnd) {
            currentHour in normalizedStart until normalizedEnd
        } else if (normalizedStart > normalizedEnd) {
            currentHour >= normalizedStart || currentHour < normalizedEnd
        } else {
            currentHour == normalizedStart
        }

        if (insideWindow) {
            return TimeUnit.MINUTES.toMillis(1)
        }

        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, normalizedStart)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - nowMillis).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
    }

    fun formatWindowLabel(startHour: Int, endHour: Int): String {
        return "${formatHour(startHour)} – ${formatHour(endHour)}"
    }

    private fun formatHour(hour24: Int): String {
        val h = hour24.coerceIn(0, 23)
        return when {
            h == 0 -> "12:00 AM"
            h < 12 -> "$h:00 AM"
            h == 12 -> "12:00 PM"
            else -> "${h - 12}:00 PM"
        }
    }

    fun buildNighttimeConstraints(preferences: AuraUserPreferences): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(if (preferences.wifiOnlyBackup) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(preferences.requireChargingForNightBackup)
            .setRequiresDeviceIdle(preferences.requireIdleForNightBackup)
            .build()
    }

    fun buildSyncConstraints(preferences: AuraUserPreferences): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(if (preferences.wifiOnlyBackup) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    }

    /**
     * Schedules or updates the unique periodic nighttime WorkManager job.
     * Note: Android OS manages exact wakeup timing under Doze and battery policies.
     */
    fun syncSchedules(context: Context, preferences: AuraUserPreferences, isAuthenticated: Boolean) {
        val workManager = WorkManager.getInstance(context.applicationContext)

        if (!isAuthenticated || !preferences.driveBackupEnabled || preferences.backupFrequency == "OFF") {
            workManager.cancelUniqueWork(UNIQUE_NIGHT_BACKUP_WORK)
            if (!isAuthenticated || !preferences.driveBackupEnabled) {
                workManager.cancelUniqueWork(UNIQUE_CHANGE_SYNC_WORK)
            }
            return
        }

        val repeatDays = when (preferences.backupFrequency) {
            "DAILY" -> 1L
            "WEEKLY" -> 7L
            "MONTHLY" -> 30L
            else -> 1L
        }

        val windowHours = computeWindowSpanHours(preferences.nightWindowStartHour, preferences.nightWindowEndHour)
        val flexHours = windowHours.coerceIn(1, 6).toLong()
        val initialDelay = computeInitialDelayMillis(
            preferences.nightWindowStartHour,
            preferences.nightWindowEndHour
        )

        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(
            repeatDays,
            TimeUnit.DAYS,
            flexHours,
            TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(buildNighttimeConstraints(preferences))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_BACKUP_KIND to KIND_NIGHTTIME))
            .addTag(UNIQUE_NIGHT_BACKUP_WORK)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NIGHT_BACKUP_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Debounced WorkManager one-time request queued when local notes or settings change.
     * Waits for connectivity (or Wi-Fi) and uses exponential backoff on transient failures.
     */
    fun enqueuePendingChangeSync(context: Context, preferences: AuraUserPreferences, isAuthenticated: Boolean) {
        if (!isAuthenticated || !preferences.driveBackupEnabled || !preferences.autoSyncOnChange) return

        val workManager = WorkManager.getInstance(context.applicationContext)
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setInitialDelay(12, TimeUnit.SECONDS)
            .setConstraints(buildSyncConstraints(preferences))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_BACKUP_KIND to KIND_AUTO_SYNC))
            .addTag(UNIQUE_CHANGE_SYNC_WORK)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_CHANGE_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAllCloudWork(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(UNIQUE_NIGHT_BACKUP_WORK)
        workManager.cancelUniqueWork(UNIQUE_CHANGE_SYNC_WORK)
    }

    private fun computeWindowSpanHours(startHour: Int, endHour: Int): Int {
        val diff = (endHour - startHour + 24) % 24
        return if (diff == 0) 3 else diff
    }
}
