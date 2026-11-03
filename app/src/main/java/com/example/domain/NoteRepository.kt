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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class NoteRepository(
    private val context: Context,
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val attachmentDao: AttachmentDao,
    private val backupDao: BackupDao
) {
    val activeNotes: Flow<List<NoteEntity>> = noteDao.getActiveNotes()
    val archivedNotes: Flow<List<NoteEntity>> = noteDao.getArchivedNotes()
    val trashNotes: Flow<List<NoteEntity>> = noteDao.getTrashNotes()
    val favoriteNotes: Flow<List<NoteEntity>> = noteDao.getFavoriteNotes()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val allAttachments: Flow<List<AttachmentEntity>> = attachmentDao.getAllAttachments()
    val allBackups: Flow<List<BackupEntity>> = backupDao.getAllBackups()
    val activeNoteCount: Flow<Int> = noteDao.getActiveNoteCount()

    fun getNotesByCategory(categoryId: String): Flow<List<NoteEntity>> =
        noteDao.getNotesByCategory(categoryId)

    fun getNotesByType(type: String): Flow<List<NoteEntity>> =
        noteDao.getNotesByType(type)

    fun searchNotes(query: String): Flow<List<NoteEntity>> =
        noteDao.searchNotes(query)

    fun observeNote(id: String): Flow<NoteEntity?> =
        noteDao.observeNoteById(id)

    suspend fun getNote(id: String): NoteEntity? =
        noteDao.getNoteById(id)

    fun getAttachmentsForNote(noteId: String): Flow<List<AttachmentEntity>> =
        attachmentDao.getAttachmentsForNote(noteId)

    suspend fun saveNote(note: NoteEntity) {
        withContext(Dispatchers.IO) {
            noteDao.insertNote(note.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun duplicateNote(note: NoteEntity): String {
        return withContext(Dispatchers.IO) {
            val newId = "note_" + UUID.randomUUID().toString().take(8)
            val now = System.currentTimeMillis()
            val duplicated = note.copy(
                id = newId,
                title = "${note.title} (Copy)",
                createdAt = now,
                updatedAt = now
            )
            noteDao.insertNote(duplicated)
            newId
        }
    }

    suspend fun moveToTrash(id: String) {
        withContext(Dispatchers.IO) {
            noteDao.moveToTrash(id)
        }
    }

    suspend fun restoreFromTrash(id: String) {
        withContext(Dispatchers.IO) {
            noteDao.restoreFromTrash(id)
        }
    }

    suspend fun togglePin(id: String, current: Boolean) {
        withContext(Dispatchers.IO) {
            noteDao.setPinned(id, !current)
        }
    }

    suspend fun toggleFavorite(id: String, current: Boolean) {
        withContext(Dispatchers.IO) {
            noteDao.setFavorite(id, !current)
        }
    }

    suspend fun toggleArchive(id: String, current: Boolean) {
        withContext(Dispatchers.IO) {
            noteDao.setArchived(id, !current)
        }
    }

    suspend fun deletePermanently(id: String) {
        withContext(Dispatchers.IO) {
            val attachments = attachmentDao.getAttachmentsForNoteSync(id)
            attachments.forEach { att ->
                try {
                    val file = File(att.localPath)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            attachmentDao.deleteAttachmentsForNote(id)
            noteDao.deleteNotePermanently(id)
        }
    }

    suspend fun emptyTrash() {
        withContext(Dispatchers.IO) {
            noteDao.emptyTrash()
        }
    }

    suspend fun saveCategory(category: CategoryEntity) {
        withContext(Dispatchers.IO) {
            categoryDao.insertCategory(category)
        }
    }

    suspend fun deleteCategory(id: String) {
        withContext(Dispatchers.IO) {
            categoryDao.deleteCategory(id)
        }
    }

    suspend fun saveAttachmentFromUri(noteId: String, uri: Uri, originalName: String?, mimeType: String?): AttachmentEntity? {
        return withContext(Dispatchers.IO) {
            try {
                val vaultDir = File(context.filesDir, "media_vault")
                if (!vaultDir.exists()) vaultDir.mkdirs()

                val ext = originalName?.substringAfterLast('.', "") ?: "bin"
                val attId = "att_" + UUID.randomUUID().toString()
                val targetFile = File(vaultDir, "${attId}_${originalName ?: "file"}")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val attachment = AttachmentEntity(
                    id = attId,
                    noteId = noteId,
                    fileName = originalName ?: targetFile.name,
                    mimeType = mimeType ?: "application/octet-stream",
                    localPath = targetFile.absolutePath,
                    fileSizeBytes = targetFile.length(),
                    caption = "",
                    createdAt = System.currentTimeMillis()
                )
                attachmentDao.insertAttachment(attachment)
                attachment
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun saveAudioAttachment(noteId: String, audioFile: File, durationMs: Long): AttachmentEntity? {
        return withContext(Dispatchers.IO) {
            try {
                val vaultDir = File(context.filesDir, "media_vault")
                if (!vaultDir.exists()) vaultDir.mkdirs()

                val attId = "att_voice_" + UUID.randomUUID().toString().take(8)
                val targetFile = File(vaultDir, "${attId}.m4a")
                audioFile.copyTo(targetFile, overwrite = true)

                val attachment = AttachmentEntity(
                    id = attId,
                    noteId = noteId,
                    fileName = "Voice Note (${durationMs / 1000}s).m4a",
                    mimeType = "audio/mp4",
                    localPath = targetFile.absolutePath,
                    fileSizeBytes = targetFile.length(),
                    caption = "Recorded Voice Note",
                    createdAt = System.currentTimeMillis()
                )
                attachmentDao.insertAttachment(attachment)
                attachment
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun deleteAttachment(id: String) {
        withContext(Dispatchers.IO) {
            val all = attachmentDao.getAllAttachmentsSync()
            val att = all.find { it.id == id }
            if (att != null) {
                try {
                    val file = File(att.localPath)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
                attachmentDao.deleteAttachment(id)
            }
        }
    }
}
