package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    indices = [Index(value = ["noteId"])]
)
data class AttachmentEntity(
    @PrimaryKey
    val id: String,
    val noteId: String,
    val fileName: String,
    val mimeType: String,
    val localPath: String,
    val fileSizeBytes: Long = 0L,
    val caption: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
