package net.kikin.nubecita.core.videofeed.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.videofeed.DefaultVideoFeedSourceFactory
import net.kikin.nubecita.core.videofeed.VideoFeedSourceFactory

/**
 * Binds the [VideoFeedSourceFactory] that selects per entry point: trending
 * (`thevids`) when no author, else an author's `posts_with_video` feed.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface VideoFeedModule {
    @Binds
    fun bindVideoFeedSourceFactory(impl: DefaultVideoFeedSourceFactory): VideoFeedSourceFactory
}
