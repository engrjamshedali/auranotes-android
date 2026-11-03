package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false
)
