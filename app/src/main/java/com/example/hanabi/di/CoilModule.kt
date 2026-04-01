package com.example.hanabi.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import com.example.hanabi.data.smb.SmbImageFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        smbFetcherFactory: SmbImageFetcher.Factory
    ): ImageLoader = ImageLoader.Builder(context)
        .components { add(smbFetcherFactory) }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("thumbnail_cache"))
                .maxSizePercent(0.05)
                .build()
        }
        .crossfade(true)
        .build()
}
