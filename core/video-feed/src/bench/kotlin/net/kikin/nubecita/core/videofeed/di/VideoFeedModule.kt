package net.kikin.nubecita.core.videofeed.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.videofeed.BenchVideoFeedSourceFactory
import net.kikin.nubecita.core.videofeed.VideoFeedSourceFactory

/**
 * Bench-flavor binding: [VideoFeedSourceFactory] → [BenchVideoFeedSourceFactory]
 * (bundled clips, fully offline for every entry point).
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface VideoFeedModule {
    @Binds
    fun bindVideoFeedSourceFactory(impl: BenchVideoFeedSourceFactory): VideoFeedSourceFactory
}
