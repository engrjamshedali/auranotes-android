package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 0 AND categoryId = :categoryId ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByCategory(categoryId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 0 AND noteType = :noteType ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByType(noteType: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 0 AND isFavorite = 1 ORDER BY isPinned DESC, updatedAt DESC")
    fun getFavoriteNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrash = 1 ORDER BY updatedAt DESC")
    fun getTrashNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeNoteById(id: String): Flow<NoteEntity?>

    @Query("""
        SELECT * FROM notes 
        WHERE isTrash = 0 
        AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET isTrash = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun moveToTrash(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isTrash = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun restoreFromTrash(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isArchived = :isArchived, updatedAt = :timestamp WHERE id = :id")
    suspend fun setArchived(id: String, isArchived: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :timestamp WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isFavorite = :isFavorite, updatedAt = :timestamp WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNotePermanently(id: String)

    @Query("DELETE FROM notes WHERE isTrash = 1")
    suspend fun emptyTrash()

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSync(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE isTrash = 0")
    fun getActiveNoteCount(): Flow<Int>
}
