package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backups")
data class BackupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val filePath: String,
    val noteCount: Int,
    val attachmentCount: Int,
    val sizeBytes: Long,
    val backupType: String = "MANUAL", // MANUAL or SCHEDULED
    val status: String = "SUCCESS",
    val createdAt: Long = System.currentTimeMillis()
)
