package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun getAttachmentsForNote(noteId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt DESC")
    suspend fun getAttachmentsForNoteSync(noteId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments ORDER BY createdAt DESC")
    fun getAllAttachments(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments ORDER BY createdAt DESC")
    suspend fun getAllAttachmentsSync(): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: String)

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteAttachmentsForNote(noteId: String)
}
