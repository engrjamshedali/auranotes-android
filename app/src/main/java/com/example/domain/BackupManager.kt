package com.example.domain

import android.content.Context
import android.net.Uri
import com.example.data.local.AttachmentDao
import com.example.data.local.AttachmentEntity
import com.example.data.local.BackupDao
import com.example.data.local.BackupEntity
import com.example.data.local.CategoryDao
import com.example.data.local.CategoryEntity
import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class BackupManifest(
    val formatVersion: String = "1.0",
    val appVersion: String = "1.0",
    val exportDate: Long,
    val noteCount: Int,
    val categoryCount: Int,
    val attachmentCount: Int,
    val appName: String = "AuraNotes",
    val checksum: String = ""
)

data class RestorePreview(
    val isValid: Boolean,
    val error: String? = null,
    val manifest: BackupManifest? = null,
    val notes: List<NoteEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val conflictingNoteCount: Int = 0,
    val tempDir: File? = null
)

data class RestoreReport(
    val success: Boolean,
    val notesRestored: Int,
    val categoriesRestored: Int,
    val attachmentsRestored: Int,
    val message: String
)

class BackupManager(
    private val context: Context,
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val attachmentDao: AttachmentDao,
    private val backupDao: BackupDao
) {

    /**
     * Creates a complete portable .auranotes ZIP package containing:
     * - manifest.json
     * - notes.json
     * - categories.json
     * - attachments.json
     * - attachments/<files>
     */
    suspend fun createCompleteBackup(backupType: String = "MANUAL"): File? {
        return withContext(Dispatchers.IO) {
            try {
                val backupsDir = File(context.filesDir, "backups")
                if (!backupsDir.exists()) backupsDir.mkdirs()

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val backupFile = File(backupsDir, "AuraNotes_Backup_$timeStamp.auranotes")

                val allNotes = noteDao.getAllNotesSync()
                val allCategories = categoryDao.getAllCategoriesSync()
                val allAttachments = attachmentDao.getAllAttachmentsSync()

                // Generate notes JSON
                val notesArray = JSONArray()
                allNotes.forEach { note ->
                    val obj = JSONObject().apply {
                        put("id", note.id)
                        put("title", note.title)
                        put("content", note.content)
                        put("noteType", note.noteType)
                        put("categoryId", note.categoryId)
                        put("folder", note.folder)
                        put("tags", note.tags)
                        put("language", note.language)
                        put("isPinned", note.isPinned)
                        put("isFavorite", note.isFavorite)
                        put("isArchived", note.isArchived)
                        put("isTrash", note.isTrash)
                        put("colorIndex", note.colorIndex)
                        put("createdAt", note.createdAt)
                        put("updatedAt", note.updatedAt)
                    }
                    notesArray.put(obj)
                }

                // Generate categories JSON
                val categoriesArray = JSONArray()
                allCategories.forEach { cat ->
                    val obj = JSONObject().apply {
                        put("id", cat.id)
                        put("name", cat.name)
                        put("iconName", cat.iconName)
                        put("colorHex", cat.colorHex)
                        put("sortOrder", cat.sortOrder)
                        put("isDefault", cat.isDefault)
                    }
                    categoriesArray.put(obj)
                }

                // Generate attachments JSON
                val attachmentsArray = JSONArray()
                allAttachments.forEach { att ->
                    val obj = JSONObject().apply {
                        put("id", att.id)
                        put("noteId", att.noteId)
                        put("fileName", att.fileName)
                        put("mimeType", att.mimeType)
                        put("fileSizeBytes", att.fileSizeBytes)
                        put("caption", att.caption)
                        put("createdAt", att.createdAt)
                        put("relativePath", "attachments/" + File(att.localPath).name)
                    }
                    attachmentsArray.put(obj)
                }

                // Calculate sha256 checksum of notes data
                val md = MessageDigest.getInstance("SHA-256")
                val digestBytes = md.digest(notesArray.toString().toByteArray())
                val checksumHex = digestBytes.joinToString("") { "%02x".format(it) }

                val manifestObj = JSONObject().apply {
                    put("formatVersion", "1.0")
                    put("appVersion", "1.0")
                    put("exportDate", System.currentTimeMillis())
                    put("noteCount", allNotes.size)
                    put("categoryCount", allCategories.size)
                    put("attachmentCount", allAttachments.size)
                    put("appName", "AuraNotes")
                    put("checksum", checksumHex)
                }

                // Write ZIP file
                ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zos ->
                    // 1. manifest.json
                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(manifestObj.toString(2).toByteArray())
                    zos.closeEntry()

                    // 2. notes.json
                    zos.putNextEntry(ZipEntry("notes.json"))
                    zos.write(notesArray.toString(2).toByteArray())
                    zos.closeEntry()

                    // 3. categories.json
                    zos.putNextEntry(ZipEntry("categories.json"))
                    zos.write(categoriesArray.toString(2).toByteArray())
                    zos.closeEntry()

                    // 4. attachments.json
                    zos.putNextEntry(ZipEntry("attachments.json"))
                    zos.write(attachmentsArray.toString(2).toByteArray())
                    zos.closeEntry()

                    // 5. Binary attachments
                    allAttachments.forEach { att ->
                        val srcFile = File(att.localPath)
                        if (srcFile.exists() && srcFile.isFile) {
                            zos.putNextEntry(ZipEntry("attachments/" + srcFile.name))
                            FileInputStream(srcFile).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                // Record backup in database
                val backupRecord = BackupEntity(
                    id = "bk_" + UUID.randomUUID().toString().take(8),
                    name = backupFile.name,
                    filePath = backupFile.absolutePath,
                    noteCount = allNotes.size,
                    attachmentCount = allAttachments.size,
                    sizeBytes = backupFile.length(),
                    backupType = backupType,
                    status = "SUCCESS",
                    createdAt = System.currentTimeMillis()
                )
                backupDao.insertBackup(backupRecord)

                backupFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Inspects and validates a selected backup file (.auranotes ZIP or .json)
     */
    suspend fun inspectBackup(uri: Uri): RestorePreview {
        return withContext(Dispatchers.IO) {
            try {
                val tempDir = File(context.cacheDir, "restore_temp_" + UUID.randomUUID().toString())
                tempDir.mkdirs()

                val localBackupFile = File(tempDir, "target_backup")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(localBackupFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext RestorePreview(false, error = "Unable to read selected file.")

                // Check if file is ZIP (.auranotes) or raw JSON
                val isZip = try {
                    ZipFile(localBackupFile).close()
                    true
                } catch (_: Exception) {
                    false
                }

                if (isZip) {
                    // Extract ZIP
                    val extractedDir = File(tempDir, "extracted")
                    extractedDir.mkdirs()

                    ZipFile(localBackupFile).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val destFile = File(extractedDir, entry.name)
                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile?.mkdirs()
                                zip.getInputStream(entry).use { inStream ->
                                    FileOutputStream(destFile).use { outStream ->
                                        inStream.copyTo(outStream)
                                    }
                                }
                            }
                        }
                    }

                    val manifestFile = File(extractedDir, "manifest.json")
                    val notesFile = File(extractedDir, "notes.json")
                    val categoriesFile = File(extractedDir, "categories.json")
                    val attachmentsFile = File(extractedDir, "attachments.json")

                    if (!manifestFile.exists() || !notesFile.exists()) {
                        return@withContext RestorePreview(
                            isValid = false,
                            error = "Invalid AuraNotes backup: missing manifest or notes data."
                        )
                    }

                    val manifestObj = JSONObject(manifestFile.readText())
                    val manifest = BackupManifest(
                        formatVersion = manifestObj.optString("formatVersion", "1.0"),
                        appVersion = manifestObj.optString("appVersion", "1.0"),
                        exportDate = manifestObj.optLong("exportDate", System.currentTimeMillis()),
                        noteCount = manifestObj.optInt("noteCount", 0),
                        categoryCount = manifestObj.optInt("categoryCount", 0),
                        attachmentCount = manifestObj.optInt("attachmentCount", 0),
                        appName = manifestObj.optString("appName", "AuraNotes"),
                        checksum = manifestObj.optString("checksum", "")
                    )

                    val notesList = parseNotesJson(notesFile.readText())
                    val categoriesList = if (categoriesFile.exists()) parseCategoriesJson(categoriesFile.readText()) else emptyList()
                    val attachmentsList = if (attachmentsFile.exists()) parseAttachmentsJson(attachmentsFile.readText(), extractedDir) else emptyList()

                    val existingNotes = noteDao.getAllNotesSync()
                    val existingIds = existingNotes.map { it.id }.toSet()
                    val conflictCount = notesList.count { it.id in existingIds }

                    RestorePreview(
                        isValid = true,
                        manifest = manifest,
                        notes = notesList,
                        categories = categoriesList,
                        attachments = attachmentsList,
                        conflictingNoteCount = conflictCount,
                        tempDir = tempDir
                    )
                } else {
                    // Try parsing as single JSON export
                    val jsonText = localBackupFile.readText()
                    val notesList = parseNotesJson(jsonText)
                    if (notesList.isEmpty()) {
                        return@withContext RestorePreview(
                            isValid = false,
                            error = "File does not contain valid AuraNotes JSON or ZIP data."
                        )
                    }

                    val manifest = BackupManifest(
                        formatVersion = "1.0",
                        appVersion = "1.0",
                        exportDate = System.currentTimeMillis(),
                        noteCount = notesList.size,
                        categoryCount = 0,
                        attachmentCount = 0,
                        appName = "AuraNotes JSON Export"
                    )

                    val existingNotes = noteDao.getAllNotesSync()
                    val existingIds = existingNotes.map { it.id }.toSet()
                    val conflictCount = notesList.count { it.id in existingIds }

                    RestorePreview(
                        isValid = true,
                        manifest = manifest,
                        notes = notesList,
                        categories = emptyList(),
                        attachments = emptyList(),
                        conflictingNoteCount = conflictCount,
                        tempDir = tempDir
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                RestorePreview(
                    isValid = false,
                    error = "Failed to inspect backup file: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Executes the restore process with user chosen mode:
     * - "MERGE": Keep existing, insert non-conflicting, skip existing IDs
     * - "KEEP_BOTH": Insert with newly generated UUIDs so no data is overwritten
     * - "CLEAN_REPLACE": Clean existing notes and restore exact backup state
     */
    suspend fun executeRestore(
        preview: RestorePreview,
        mode: String // "MERGE", "KEEP_BOTH", "CLEAN_REPLACE"
    ): RestoreReport {
        return withContext(Dispatchers.IO) {
            try {
                if (!preview.isValid) {
                    return@withContext RestoreReport(false, 0, 0, 0, preview.error ?: "Invalid backup")
                }

                // If CLEAN_REPLACE, first create an automatic safety backup
                if (mode == "CLEAN_REPLACE") {
                    createCompleteBackup(backupType = "SAFETY_PRE_RESTORE")
                    val existingNotes = noteDao.getAllNotesSync()
                    existingNotes.forEach { noteDao.deleteNotePermanently(it.id) }
                }

                // Restore categories
                preview.categories.forEach { cat ->
                    categoryDao.insertCategory(cat)
                }

                val existingNotes = noteDao.getAllNotesSync()
                val existingIds = existingNotes.map { it.id }.toSet()

                var notesRestored = 0
                val idMap = mutableMapOf<String, String>() // oldNoteId -> newNoteId

                preview.notes.forEach { note ->
                    when (mode) {
                        "MERGE" -> {
                            if (note.id !in existingIds) {
                                noteDao.insertNote(note)
                                idMap[note.id] = note.id
                                notesRestored++
                            }
                        }
                        "KEEP_BOTH" -> {
                            val newId = if (note.id in existingIds) "note_" + UUID.randomUUID().toString().take(8) else note.id
                            idMap[note.id] = newId
                            noteDao.insertNote(note.copy(id = newId, title = if (newId != note.id) "${note.title} (Restored)" else note.title))
                            notesRestored++
                        }
                        "CLEAN_REPLACE" -> {
                            noteDao.insertNote(note)
                            idMap[note.id] = note.id
                            notesRestored++
                        }
                    }
                }

                // Restore binary attachments into media_vault directory
                val vaultDir = File(context.filesDir, "media_vault")
                if (!vaultDir.exists()) vaultDir.mkdirs()

                var attachmentsRestored = 0
                preview.attachments.forEach { att ->
                    val targetNoteId = idMap[att.noteId]
                    if (targetNoteId != null) {
                        val srcFile = File(att.localPath)
                        val newAttId = "att_" + UUID.randomUUID().toString().take(8)
                        val targetFile = File(vaultDir, "${newAttId}_${att.fileName}")

                        if (srcFile.exists()) {
                            srcFile.copyTo(targetFile, overwrite = true)
                        }

                        val restoredAttachment = att.copy(
                            id = newAttId,
                            noteId = targetNoteId,
                            localPath = targetFile.absolutePath,
                            fileSizeBytes = if (targetFile.exists()) targetFile.length() else att.fileSizeBytes
                        )
                        attachmentDao.insertAttachment(restoredAttachment)
                        attachmentsRestored++
                    }
                }

                // Clean up temp directory
                preview.tempDir?.deleteRecursively()

                RestoreReport(
                    success = true,
                    notesRestored = notesRestored,
                    categoriesRestored = preview.categories.size,
                    attachmentsRestored = attachmentsRestored,
                    message = "Successfully restored $notesRestored notes and $attachmentsRestored media attachments."
                )
            } catch (e: Exception) {
                e.printStackTrace()
                preview.tempDir?.deleteRecursively()
                RestoreReport(
                    success = false,
                    notesRestored = 0,
                    categoriesRestored = 0,
                    attachmentsRestored = 0,
                    message = "Restore failed: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    private fun parseNotesJson(jsonString: String): List<NoteEntity> {
        val result = mutableListOf<NoteEntity>()
        try {
            val trimmed = jsonString.trim()
            val array = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("notes") ?: JSONArray()
            }

            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                result.add(
                    NoteEntity(
                        id = o.optString("id", "note_" + UUID.randomUUID().toString().take(8)),
                        title = o.optString("title", "Untitled"),
                        content = o.optString("content", ""),
                        noteType = o.optString("noteType", "TEXT"),
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun parseCategoriesJson(jsonString: String): List<CategoryEntity> {
        val result = mutableListOf<CategoryEntity>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                result.add(
                    CategoryEntity(
                        id = o.optString("id", "cat_" + i),
                        name = o.optString("name", "Category"),
                        iconName = o.optString("iconName", "Folder"),
                        colorHex = o.optString("colorHex", "#6366F1"),
                        sortOrder = o.optInt("sortOrder", i),
                        isDefault = o.optBoolean("isDefault", false)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun parseAttachmentsJson(jsonString: String, baseDir: File): List<AttachmentEntity> {
        val result = mutableListOf<AttachmentEntity>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val relPath = o.optString("relativePath", "")
                val srcFile = File(baseDir, relPath)
                result.add(
                    AttachmentEntity(
                        id = o.optString("id", "att_" + UUID.randomUUID().toString()),
                        noteId = o.optString("noteId", ""),
                        fileName = o.optString("fileName", "attachment"),
                        mimeType = o.optString("mimeType", "application/octet-stream"),
                        localPath = srcFile.absolutePath,
                        fileSizeBytes = o.optLong("fileSizeBytes", 0L),
                        caption = o.optString("caption", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
