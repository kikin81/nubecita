package net.kikin.nubecita.core.feedcache.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.feedcache.BenchFeedPostSource
import net.kikin.nubecita.core.feedcache.FakeFeedRepository
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.posts.BenchPostSource
import javax.inject.Singleton

/**
 * Bench binding of [FeedRepository] to the in-process [FakeFeedRepository] so
 * the feed widget renders offline (nubecita-epe3). Production binds the
 * Room-backed `DefaultFeedRepository` instead (src/production).
 *
 * Also contributes [BenchFeedPostSource] to the bench `PostRepository`'s
 * source set, so the media viewer opened from a feed image re-reads THAT post
 * instead of the bench gallery fallback.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FeedRepositoryModule {
    @Binds
    @Singleton
    internal abstract fun bindFeedRepository(impl: FakeFeedRepository): FeedRepository

    @Binds
    @IntoSet
    internal abstract fun bindFeedPostSource(impl: BenchFeedPostSource): BenchPostSource
}
