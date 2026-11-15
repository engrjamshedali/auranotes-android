package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aura_preferences")

enum class CloudSyncStatus(val label: String) {
    SYNCED("Synced"),
    BACKING_UP("Backing up"),
    WAITING_FOR_WIFI("Waiting for Wi-Fi"),
    WAITING_FOR_IDLE("Waiting for idle"),
    OFFLINE("Offline"),
    BACKUP_FAILED("Backup failed"),
    AUTH_REQUIRED("Authentication required");

    companion object {
        fun fromName(value: String?): CloudSyncStatus =
            entries.firstOrNull { it.name == value } ?: AUTH_REQUIRED
    }
}

data class AuraUserPreferences(
    val themeMode: String = "LIGHT", // "DARK", "LIGHT", "SYSTEM"
    val accentIndex: Int = 0, // 0=Professional Blue, 1=Soft Violet, 2=Soft Emerald, 3=Muted Coral, 4=Gentle Amber
    val editorFontSize: Int = 14,
    val backupFrequency: String = "DAILY", // "DAILY", "WEEKLY", "MONTHLY", "OFF"
    val backupRetentionCount: Int = 7,
    val reducedMotion: Boolean = false,
    val reducedTransparency: Boolean = false,
    val highContrast: Boolean = false,
    val defaultNoteType: String = "RICH_TEXT",
    // Cloud Auth & Google Drive Sync Preferences (zero tokens or passwords persisted)
    val offlineModeBypassedAuth: Boolean = false,
    val driveBackupEnabled: Boolean = false,
    val driveAuthorizedEmail: String = "",
    val cloudSyncStatus: String = CloudSyncStatus.AUTH_REQUIRED.name,
    val lastCloudBackupAt: Long = 0L,
    val lastCloudBackupSizeBytes: Long = 0L,
    val lastCloudBackupChecksum: String = "",
    val lastCloudBackupError: String = "",
    val localDataRevision: Long = 0L,
    val lastSyncedRevision: Long = 0L,
    val wifiOnlyBackup: Boolean = true,
    val requireChargingForNightBackup: Boolean = true,
    val requireIdleForNightBackup: Boolean = true,
    val nightWindowStartHour: Int = 0, // 12:00 AM local time
    val nightWindowEndHour: Int = 5,   // 5:00 AM local time
    val autoSyncOnChange: Boolean = true
) {
    val hasPendingChanges: Boolean
        get() = localDataRevision > lastSyncedRevision
}

class PreferencesRepository(private val context: Context) {
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyAccent = intPreferencesKey("accent_index")
    private val keyFontSize = intPreferencesKey("editor_font_size")
    private val keyBackupFreq = stringPreferencesKey("backup_freq")
    private val keyBackupRetention = intPreferencesKey("backup_retention")
    private val keyReducedMotion = booleanPreferencesKey("reduced_motion")
    private val keyReducedTransparency = booleanPreferencesKey("reduced_transparency")
    private val keyHighContrast = booleanPreferencesKey("high_contrast")
    private val keyDefaultNoteType = stringPreferencesKey("default_note_type")

    private val keyOfflineBypass = booleanPreferencesKey("offline_bypass_auth")
    private val keyDriveBackupEnabled = booleanPreferencesKey("drive_backup_enabled")
    private val keyDriveAuthorizedEmail = stringPreferencesKey("drive_authorized_email")
    private val keyCloudSyncStatus = stringPreferencesKey("cloud_sync_status")
    private val keyLastCloudBackupAt = longPreferencesKey("last_cloud_backup_at")
    private val keyLastCloudBackupSize = longPreferencesKey("last_cloud_backup_size")
    private val keyLastCloudBackupChecksum = stringPreferencesKey("last_cloud_backup_checksum")
    private val keyLastCloudBackupError = stringPreferencesKey("last_cloud_backup_error")
    private val keyLocalDataRevision = longPreferencesKey("local_data_revision")
    private val keyLastSyncedRevision = longPreferencesKey("last_synced_revision")
    private val keyWifiOnlyBackup = booleanPreferencesKey("wifi_only_backup")
    private val keyRequireCharging = booleanPreferencesKey("require_charging_night_backup")
    private val keyRequireIdle = booleanPreferencesKey("require_idle_night_backup")
    private val keyNightWindowStart = intPreferencesKey("night_window_start_hour")
    private val keyNightWindowEnd = intPreferencesKey("night_window_end_hour")
    private val keyAutoSyncOnChange = booleanPreferencesKey("auto_sync_on_change")

    val preferencesFlow: Flow<AuraUserPreferences> = context.dataStore.data.map { prefs ->
        AuraUserPreferences(
            themeMode = prefs[keyTheme] ?: "LIGHT",
            accentIndex = prefs[keyAccent] ?: 0,
            editorFontSize = prefs[keyFontSize] ?: 14,
            backupFrequency = prefs[keyBackupFreq] ?: "DAILY",
            backupRetentionCount = prefs[keyBackupRetention] ?: 7,
            reducedMotion = prefs[keyReducedMotion] ?: false,
            reducedTransparency = prefs[keyReducedTransparency] ?: false,
            highContrast = prefs[keyHighContrast] ?: false,
            defaultNoteType = prefs[keyDefaultNoteType] ?: "RICH_TEXT",
            offlineModeBypassedAuth = prefs[keyOfflineBypass] ?: false,
            driveBackupEnabled = prefs[keyDriveBackupEnabled] ?: false,
            driveAuthorizedEmail = prefs[keyDriveAuthorizedEmail] ?: "",
            cloudSyncStatus = prefs[keyCloudSyncStatus] ?: CloudSyncStatus.AUTH_REQUIRED.name,
            lastCloudBackupAt = prefs[keyLastCloudBackupAt] ?: 0L,
            lastCloudBackupSizeBytes = prefs[keyLastCloudBackupSize] ?: 0L,
            lastCloudBackupChecksum = prefs[keyLastCloudBackupChecksum] ?: "",
            lastCloudBackupError = prefs[keyLastCloudBackupError] ?: "",
            localDataRevision = prefs[keyLocalDataRevision] ?: 0L,
            lastSyncedRevision = prefs[keyLastSyncedRevision] ?: 0L,
            wifiOnlyBackup = prefs[keyWifiOnlyBackup] ?: true,
            requireChargingForNightBackup = prefs[keyRequireCharging] ?: true,
            requireIdleForNightBackup = prefs[keyRequireIdle] ?: true,
            nightWindowStartHour = prefs[keyNightWindowStart] ?: 0,
            nightWindowEndHour = prefs[keyNightWindowEnd] ?: 5,
            autoSyncOnChange = prefs[keyAutoSyncOnChange] ?: true
        )
    }

    suspend fun getSnapshot(): AuraUserPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit {
            it[keyTheme] = mode
            it[keyLocalDataRevision] = (it[keyLocalDataRevision] ?: 0L) + 1L
        }
    }

    suspend fun setAccentIndex(index: Int) {
        context.dataStore.edit {
            it[keyAccent] = index
            it[keyLocalDataRevision] = (it[keyLocalDataRevision] ?: 0L) + 1L
        }
    }

    suspend fun setEditorFontSize(size: Int) {
        context.dataStore.edit {
            it[keyFontSize] = size
            it[keyLocalDataRevision] = (it[keyLocalDataRevision] ?: 0L) + 1L
        }
    }

    suspend fun setBackupFrequency(freq: String) {
        context.dataStore.edit {
            it[keyBackupFreq] = freq
            it[keyLocalDataRevision] = (it[keyLocalDataRevision] ?: 0L) + 1L
        }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.dataStore.edit {
            it[keyReducedMotion] = enabled
            it[keyLocalDataRevision] = (it[keyLocalDataRevision] ?: 0L) + 1L
        }
    }

    suspend fun setReducedTransparency(enabled: Boolean) {
        context.dataStore.edit {
            it[keyReducedTransparency] = enabled
            it[keyLocalDataRevision] = (it[keyLocalDataRevision] ?: 0L) + 1L
        }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit {
            it[keyHighContrast] = enabled
            it[keyLocalDataRevision] = (it[keyLocalDataRevision] ?: 0L) + 1L
        }
    }

    suspend fun setDefaultNoteType(type: String) {
        context.dataStore.edit {
            it[keyDefaultNoteType] = type
            it[keyLocalDataRevision] = (it[keyLocalDataRevision] ?: 0L) + 1L
        }
    }

    suspend fun setOfflineModeBypassedAuth(bypassed: Boolean) {
        context.dataStore.edit { it[keyOfflineBypass] = bypassed }
    }

    suspend fun setDriveBackupConfig(enabled: Boolean, authorizedEmail: String) {
        context.dataStore.edit {
            it[keyDriveBackupEnabled] = enabled
            it[keyDriveAuthorizedEmail] = authorizedEmail
            if (!enabled) {
                it[keyCloudSyncStatus] = CloudSyncStatus.AUTH_REQUIRED.name
                it[keyLastCloudBackupError] = ""
            }
        }
    }

    suspend fun updateCloudSyncStatus(status: CloudSyncStatus, errorMessage: String = "") {
        context.dataStore.edit {
            it[keyCloudSyncStatus] = status.name
            it[keyLastCloudBackupError] = errorMessage
        }
    }

    suspend fun recordCloudBackupSuccess(
        timestamp: Long,
        sizeBytes: Long,
        checksum: String,
        syncedRevision: Long
    ) {
        context.dataStore.edit {
            it[keyCloudSyncStatus] = CloudSyncStatus.SYNCED.name
            it[keyLastCloudBackupAt] = timestamp
            it[keyLastCloudBackupSize] = sizeBytes
            it[keyLastCloudBackupChecksum] = checksum
            it[keyLastSyncedRevision] = maxOf(it[keyLastSyncedRevision] ?: 0L, syncedRevision)
            it[keyLastCloudBackupError] = ""
        }
    }

    suspend fun bumpLocalDataRevision(): Long {
        var newRev = 1L
        context.dataStore.edit {
            newRev = (it[keyLocalDataRevision] ?: 0L) + 1L
            it[keyLocalDataRevision] = newRev
        }
        return newRev
    }

    suspend fun setWifiOnlyBackup(wifiOnly: Boolean) {
        context.dataStore.edit { it[keyWifiOnlyBackup] = wifiOnly }
    }

    suspend fun setRequireChargingForNightBackup(requireCharging: Boolean) {
        context.dataStore.edit { it[keyRequireCharging] = requireCharging }
    }

    suspend fun setRequireIdleForNightBackup(requireIdle: Boolean) {
        context.dataStore.edit { it[keyRequireIdle] = requireIdle }
    }

    suspend fun setNightWindow(startHour: Int, endHour: Int) {
        context.dataStore.edit {
            it[keyNightWindowStart] = startHour.coerceIn(0, 23)
            it[keyNightWindowEnd] = endHour.coerceIn(0, 23)
        }
    }

    suspend fun setAutoSyncOnChange(enabled: Boolean) {
        context.dataStore.edit { it[keyAutoSyncOnChange] = enabled }
    }

    suspend fun applyRestoredSettings(
        themeMode: String?,
        accentIndex: Int?,
        editorFontSize: Int?,
        backupFrequency: String?,
        defaultNoteType: String?
    ) {
        context.dataStore.edit { prefs ->
            themeMode?.let { prefs[keyTheme] = it }
            accentIndex?.let { prefs[keyAccent] = it }
            editorFontSize?.let { prefs[keyFontSize] = it }
            backupFrequency?.let { prefs[keyBackupFreq] = it }
            defaultNoteType?.let { prefs[keyDefaultNoteType] = it }
        }
    }

    suspend fun clearCloudSessionOnSignOut() {
        context.dataStore.edit {
            it[keyDriveBackupEnabled] = false
            it[keyDriveAuthorizedEmail] = ""
            it[keyCloudSyncStatus] = CloudSyncStatus.AUTH_REQUIRED.name
            it[keyLastCloudBackupError] = ""
            it[keyOfflineBypass] = false
        }
    }
}
