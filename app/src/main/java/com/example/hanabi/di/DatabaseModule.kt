package com.example.hanabi.di

import android.content.Context
import com.example.hanabi.data.db.PlaybackDao
import com.example.hanabi.data.db.PlaybackDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlaybackDatabase =
        PlaybackDatabase.getInstance(context)

    @Provides
    fun providePlaybackDao(db: PlaybackDatabase): PlaybackDao = db.playbackDao()
}
