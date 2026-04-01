package com.example.hanabi.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(entities = [PlaybackProgress::class], version = 1)
abstract class PlaybackDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao

    companion object {
        @Volatile private var instance: PlaybackDatabase? = null

        fun getInstance(context: Context): PlaybackDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlaybackDatabase::class.java,
                    "playback.db"
                ).build().also { instance = it }
            }
    }
}
