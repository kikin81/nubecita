package net.kikin.nubecita.core.videofeed.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.videofeed.FakeVideoFeedSource
import net.kikin.nubecita.core.videofeed.VideoFeedSource

/**
 * Bench-flavor binding: [VideoFeedSource] → [FakeVideoFeedSource] (bundled clips,
 * fully offline). The production binding lives in `src/production` and binds the
 * real `getFeed(thevids)` source. Only one is in the app graph per flavor.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface VideoFeedModule {
    @Binds
    fun bindVideoFeedSource(impl: FakeVideoFeedSource): VideoFeedSource
}
