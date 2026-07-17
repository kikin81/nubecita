package net.kikin.nubecita.core.feedcache.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.feedcache.DefaultFeedRepository
import net.kikin.nubecita.core.feedcache.FeedRepository
import javax.inject.Singleton

/**
 * Production binding of [FeedRepository] to the Room-backed
 * [DefaultFeedRepository]. The bench flavor binds an in-process fake instead
 * (src/bench) so the feed widget renders offline (nubecita-epe3).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FeedRepositoryModule {
    @Binds
    @Singleton
    internal abstract fun bindFeedRepository(impl: DefaultFeedRepository): FeedRepository
}
