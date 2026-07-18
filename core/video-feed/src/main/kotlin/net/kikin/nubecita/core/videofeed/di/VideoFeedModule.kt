package net.kikin.nubecita.core.videofeed.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.videofeed.DefaultTrendingVideoSource
import net.kikin.nubecita.core.videofeed.VideoFeedSource

/**
 * Binds the MVP [VideoFeedSource] to the trending (`thevids`) source. When the
 * profile-videos entry lands (epic nubecita-zdv8 Slice 6), introduce a
 * qualifier or factory so callers select the source per entry point.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface VideoFeedModule {
    @Binds
    fun bindTrendingVideoSource(impl: DefaultTrendingVideoSource): VideoFeedSource
}
