package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {
    @Query("SELECT * FROM backups ORDER BY createdAt DESC")
    fun getAllBackups(): Flow<List<BackupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: BackupEntity)

    @Query("DELETE FROM backups WHERE id = :id")
    suspend fun deleteBackup(id: String)
}
