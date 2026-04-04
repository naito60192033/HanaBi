package com.example.hanabi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback_progress WHERE smbPath = :path")
    suspend fun getProgress(path: String): PlaybackProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgress)

    @Query("DELETE FROM playback_progress WHERE smbPath = :path")
    suspend fun deleteProgress(path: String)

    @Query("DELETE FROM playback_progress")
    suspend fun deleteAllProgress()
}
