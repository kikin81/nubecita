package net.kikin.nubecita.core.feedcache.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.feedcache.FakeFeedRepository
import net.kikin.nubecita.core.feedcache.FeedRepository
import javax.inject.Singleton

/**
 * Bench binding of [FeedRepository] to the in-process [FakeFeedRepository] so
 * the feed widget renders offline (nubecita-epe3). Production binds the
 * Room-backed `DefaultFeedRepository` instead (src/production).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FeedRepositoryModule {
    @Binds
    @Singleton
    internal abstract fun bindFeedRepository(impl: FakeFeedRepository): FeedRepository
}
