package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NoteType {
    TEXT,
    RICH_TEXT,
    CODE,
    CHECKLIST,
    TABLE,
    MEDIA_VAULT
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String, // UUID
    val title: String,
    val content: String,
    val noteType: String = NoteType.TEXT.name,
    val categoryId: String = "cat_personal",
    val folder: String = "Default",
    val tags: String = "", // Comma-separated tags
    val language: String = "python", // For CODE notes
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrash: Boolean = false,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
