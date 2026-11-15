package com.example.cloud

import android.os.Build
import com.example.data.local.AttachmentEntity
import com.example.data.local.AuraUserPreferences
import com.example.data.local.CategoryEntity
import com.example.data.local.NoteEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CloudBackupFileInfo(
    val fileId: String,
    val name: String,
    val modifiedTimeMillis: Long,
    val sizeBytes: Long,
    val userId: String = "",
    val userEmail: String = "",
    val schemaVersion: Int = CloudBackupSerializer.CURRENT_SCHEMA_VERSION,
    val checksumSha256: String = "",
    val noteCount: Int = 0,
    val kind: String = "history", // "latest" or "history"
    val appVersion: String = "2.0"
)

data class CloudSettingsPayload(
    val themeMode: String,
    val accentIndex: Int,
    val editorFontSize: Int,
    val backupFrequency: String,
    val defaultNoteType: String,
    val wifiOnlyBackup: Boolean,
    val requireChargingForNightBackup: Boolean,
    val requireIdleForNightBackup: Boolean,
    val nightWindowStartHour: Int,
    val nightWindowEndHour: Int,
    val autoSyncOnChange: Boolean
)

enum class CloudNoteResolutionChoice {
    USE_CLOUD,
    KEEP_LOCAL,
    KEEP_BOTH
}

data class CloudNoteConflict(
    val noteId: String,
    val localNote: NoteEntity,
    val cloudNote: NoteEntity,
    val cloudIsNewer: Boolean,
    val defaultChoice: CloudNoteResolutionChoice = if (cloudIsNewer) CloudNoteResolutionChoice.USE_CLOUD else CloudNoteResolutionChoice.KEEP_BOTH
)

enum class CloudRestoreMode {
    SMART_MERGE,
    KEEP_BOTH,
    CLEAN_REPLACE
}

data class CloudRestorePreview(
    val isValid: Boolean,
    val error: String? = null,
    val fileInfo: CloudBackupFileInfo? = null,
    val checksumValid: Boolean = false,
    val schemaVersion: Int = 0,
    val exportedAtMillis: Long = 0L,
    val notes: List<NoteEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val settings: CloudSettingsPayload? = null,
    val newFromCloudCount: Int = 0,
    val identicalCount: Int = 0,
    val conflicts: List<CloudNoteConflict> = emptyList()
)

data class CloudRestoreReport(
    val success: Boolean,
    val notesRestored: Int,
    val conflictsResolved: Int,
    val categoriesRestored: Int,
    val settingsRestored: Boolean,
    val safetyBackupName: String? = null,
    val message: String
)

data class CloudBackupPayloadResult(
    val jsonString: String,
    val contentChecksumSha256: String,
    val noteCount: Int,
    val categoryCount: Int
)

object CloudBackupSerializer {
    const val CURRENT_SCHEMA_VERSION = 2
    const val APP_VERSION = "2.0"
    const val LATEST_BACKUP_FILENAME = "backup-latest.json"

    fun historyFilenameFor(timestampMillis: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
        return "backup-${fmt.format(Date(timestampMillis))}.json"
    }

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Deterministic content hash across notes, categories, and backed-up settings
     * so we avoid redundant uploads when local data has not changed.
     */
    fun computeDataChecksum(
        notes: List<NoteEntity>,
        categories: List<CategoryEntity>,
        preferences: AuraUserPreferences
    ): String {
        val sortedNotes = notes.sortedBy { it.id }
        val sortedCats = categories.sortedBy { it.id }
        val canonical = buildString {
            sortedNotes.forEach { n ->
                append(n.id).append('|')
                append(n.title).append('|')
                append(n.content).append('|')
                append(n.noteType).append('|')
                append(n.categoryId).append('|')
                append(n.tags).append('|')
                append(n.language).append('|')
                append(n.isPinned).append('|')
                append(n.isFavorite).append('|')
                append(n.isArchived).append('|')
                append(n.isTrash).append('|')
                append(n.updatedAt).append('\n')
            }
            sortedCats.forEach { c ->
                append(c.id).append('|').append(c.name).append('|').append(c.colorHex).append('|').append(c.sortOrder).append('\n')
            }
            append(preferences.themeMode).append('|')
            append(preferences.accentIndex).append('|')
            append(preferences.editorFontSize).append('|')
            append(preferences.backupFrequency).append('|')
            append(preferences.defaultNoteType)
        }
        return sha256(canonical)
    }

    fun buildBackupJson(
        userId: String,
        userEmail: String,
        notes: List<NoteEntity>,
        categories: List<CategoryEntity>,
        attachments: List<AttachmentEntity>,
        preferences: AuraUserPreferences,
        timestampMillis: Long = System.currentTimeMillis()
    ): CloudBackupPayloadResult {
        val notesArray = JSONArray()
        notes.sortedBy { it.id }.forEach { n ->
            notesArray.put(
                JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("content", n.content)
                    put("noteType", n.noteType)
                    put("categoryId", n.categoryId)
                    put("folder", n.folder)
                    put("tags", n.tags)
                    put("language", n.language)
                    put("isPinned", n.isPinned)
                    put("isFavorite", n.isFavorite)
                    put("isArchived", n.isArchived)
                    put("isTrash", n.isTrash)
                    put("colorIndex", n.colorIndex)
                    put("createdAt", n.createdAt)
                    put("updatedAt", n.updatedAt)
                    put("contentHash", sha256("${n.title}\n${n.content}\n${n.tags}\n${n.isArchived}\n${n.isTrash}"))
                }
            )
        }

        val categoriesArray = JSONArray()
        categories.sortedBy { it.sortOrder }.forEach { c ->
            categoriesArray.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("iconName", c.iconName)
                    put("colorHex", c.colorHex)
                    put("sortOrder", c.sortOrder)
                    put("isDefault", c.isDefault)
                }
            )
        }

        val attachmentsArray = JSONArray()
        attachments.forEach { a ->
            attachmentsArray.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("noteId", a.noteId)
                    put("fileName", a.fileName)
                    put("mimeType", a.mimeType)
                    put("fileSizeBytes", a.fileSizeBytes)
                    put("caption", a.caption)
                    put("createdAt", a.createdAt)
                }
            )
        }

        val settingsObj = JSONObject().apply {
            put("themeMode", preferences.themeMode)
            put("accentIndex", preferences.accentIndex)
            put("editorFontSize", preferences.editorFontSize)
            put("backupFrequency", preferences.backupFrequency)
            put("defaultNoteType", preferences.defaultNoteType)
        }

        val preferencesObj = JSONObject().apply {
            put("wifiOnlyBackup", preferences.wifiOnlyBackup)
            put("requireChargingForNightBackup", preferences.requireChargingForNightBackup)
            put("requireIdleForNightBackup", preferences.requireIdleForNightBackup)
            put("nightWindowStartHour", preferences.nightWindowStartHour)
            put("nightWindowEndHour", preferences.nightWindowEndHour)
            put("autoSyncOnChange", preferences.autoSyncOnChange)
            put("reducedMotion", preferences.reducedMotion)
            put("reducedTransparency", preferences.reducedTransparency)
            put("highContrast", preferences.highContrast)
        }

        val userDataObj = JSONObject().apply {
            put("notes", notesArray)
            put("categories", categoriesArray)
            put("attachments", attachmentsArray)
            put("settings", settingsObj)
            put("preferences", preferencesObj)
        }

        val dataChecksum = computeDataChecksum(notes, categories, preferences)
        val payloadIntegrity = sha256(userDataObj.toString())

        val metadataObj = JSONObject().apply {
            put("appName", "AuraNotes")
            put("appVersion", APP_VERSION)
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("userId", userId)
            put("userEmail", userEmail)
            put("backupTimestamp", timestampMillis)
            put("syncRevision", preferences.localDataRevision)
            put("noteCount", notes.size)
            put("categoryCount", categories.size)
            put("attachmentCount", attachments.size)
            put("dataChecksum", dataChecksum)
            put("payloadChecksumSha256", payloadIntegrity)
            put("status", "COMPLETED")
            put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
        }

        val root = JSONObject().apply {
            put("metadata", metadataObj)
            put("userData", userDataObj)
        }

        return CloudBackupPayloadResult(
            jsonString = root.toString(2),
            contentChecksumSha256 = dataChecksum,
            noteCount = notes.size,
            categoryCount = categories.size
        )
    }

    /**
     * Validates schema, verifies SHA-256 payload integrity, and detects note-level conflicts
     * against the current local Room state.
     */
    fun parseAndAnalyzeBackup(
        rawJson: String,
        fileInfo: CloudBackupFileInfo?,
        localNotes: List<NoteEntity>,
        expectedUserId: String? = null
    ): CloudRestorePreview {
        return try {
            val root = JSONObject(rawJson)
            val metadata = root.optJSONObject("metadata")
                ?: return CloudRestorePreview(isValid = false, error = "Backup is missing required metadata.")
            val userData = root.optJSONObject("userData")
                ?: return CloudRestorePreview(isValid = false, error = "Backup is missing userData payload.")

            val schemaVersion = metadata.optInt("schemaVersion", 0)
            if (schemaVersion <= 0 || schemaVersion > CURRENT_SCHEMA_VERSION) {
                return CloudRestorePreview(
                    isValid = false,
                    error = "Unsupported backup schema version ($schemaVersion)."
                )
            }

            val backupUserId = metadata.optString("userId", "")
            if (!expectedUserId.isNullOrBlank() && backupUserId.isNotBlank() && backupUserId != expectedUserId) {
                return CloudRestorePreview(
                    isValid = false,
                    error = "This backup belongs to a different Google account."
                )
            }

            val expectedPayloadHash = metadata.optString("payloadChecksumSha256", "")
            val actualPayloadHash = sha256(userData.toString())
            val checksumValid = expectedPayloadHash.isBlank() || expectedPayloadHash == actualPayloadHash
            if (!checksumValid) {
                return CloudRestorePreview(
                    isValid = false,
                    checksumValid = false,
                    error = "Backup integrity verification failed (SHA-256 mismatch). File may be corrupted."
                )
            }

            val notesArray = userData.optJSONArray("notes") ?: JSONArray()
            val cloudNotes = mutableListOf<NoteEntity>()
            for (i in 0 until notesArray.length()) {
                val o = notesArray.getJSONObject(i)
                cloudNotes.add(
                    NoteEntity(
                        id = o.optString("id"),
                        title = o.optString("title", "Untitled"),
                        content = o.optString("content", ""),
                        noteType = o.optString("noteType", "RICH_TEXT"),
                        categoryId = o.optString("categoryId", "cat_personal"),
                        folder = o.optString("folder", "Default"),
                        tags = o.optString("tags", ""),
                        language = o.optString("language", "python"),
                        isPinned = o.optBoolean("isPinned", false),
                        isFavorite = o.optBoolean("isFavorite", false),
                        isArchived = o.optBoolean("isArchived", false),
                        isTrash = o.optBoolean("isTrash", false),
                        colorIndex = o.optInt("colorIndex", 0),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val categoriesArray = userData.optJSONArray("categories") ?: JSONArray()
            val cloudCategories = mutableListOf<CategoryEntity>()
            for (i in 0 until categoriesArray.length()) {
                val o = categoriesArray.getJSONObject(i)
                cloudCategories.add(
                    CategoryEntity(
                        id = o.optString("id"),
                        name = o.optString("name", "Space"),
                        iconName = o.optString("iconName", "Folder"),
                        colorHex = o.optString("colorHex", "#3B82F6"),
                        sortOrder = o.optInt("sortOrder", i),
                        isDefault = o.optBoolean("isDefault", false)
                    )
                )
            }

            val settingsObj = userData.optJSONObject("settings")
            val prefsObj = userData.optJSONObject("preferences")
            val settingsPayload = if (settingsObj != null) {
                CloudSettingsPayload(
                    themeMode = settingsObj.optString("themeMode", "LIGHT"),
                    accentIndex = settingsObj.optInt("accentIndex", 0),
                    editorFontSize = settingsObj.optInt("editorFontSize", 14),
                    backupFrequency = settingsObj.optString("backupFrequency", "DAILY"),
                    defaultNoteType = settingsObj.optString("defaultNoteType", "RICH_TEXT"),
                    wifiOnlyBackup = prefsObj?.optBoolean("wifiOnlyBackup", true) ?: true,
                    requireChargingForNightBackup = prefsObj?.optBoolean("requireChargingForNightBackup", true) ?: true,
                    requireIdleForNightBackup = prefsObj?.optBoolean("requireIdleForNightBackup", true) ?: true,
                    nightWindowStartHour = prefsObj?.optInt("nightWindowStartHour", 0) ?: 0,
                    nightWindowEndHour = prefsObj?.optInt("nightWindowEndHour", 5) ?: 5,
                    autoSyncOnChange = prefsObj?.optBoolean("autoSyncOnChange", true) ?: true
                )
            } else {
                null
            }

            // Detect per-note conflicts using content equivalence + updatedAt
            val localById = localNotes.associateBy { it.id }
            val conflicts = mutableListOf<CloudNoteConflict>()
            var newFromCloud = 0
            var identical = 0

            cloudNotes.forEach { cloudNote ->
                val localNote = localById[cloudNote.id]
                if (localNote == null) {
                    newFromCloud++
                } else {
                    val isSameContent =
                        localNote.title == cloudNote.title &&
                            localNote.content == cloudNote.content &&
                            localNote.tags == cloudNote.tags &&
                            localNote.categoryId == cloudNote.categoryId &&
                            localNote.isPinned == cloudNote.isPinned &&
                            localNote.isFavorite == cloudNote.isFavorite &&
                            localNote.isArchived == cloudNote.isArchived &&
                            localNote.isTrash == cloudNote.isTrash
                    if (isSameContent) {
                        identical++
                    } else {
                        conflicts.add(
                            CloudNoteConflict(
                                noteId = cloudNote.id,
                                localNote = localNote,
                                cloudNote = cloudNote,
                                cloudIsNewer = cloudNote.updatedAt > localNote.updatedAt
                            )
                        )
                    }
                }
            }

            CloudRestorePreview(
                isValid = true,
                fileInfo = fileInfo,
                checksumValid = checksumValid,
                schemaVersion = schemaVersion,
                exportedAtMillis = metadata.optLong("backupTimestamp", fileInfo?.modifiedTimeMillis ?: 0L),
                notes = cloudNotes,
                categories = cloudCategories,
                settings = settingsPayload,
                newFromCloudCount = newFromCloud,
                identicalCount = identical,
                conflicts = conflicts
            )
        } catch (e: Exception) {
            CloudRestorePreview(
                isValid = false,
                error = "Invalid cloud backup format: ${e.localizedMessage ?: "Parse error"}"
            )
        }
    }

    fun parseRfc3339Millis(iso: String): Long {
        if (iso.isBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(iso)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return 0L
    }
}
