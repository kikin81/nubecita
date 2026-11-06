package net.kikin.nubecita.core.feedcache.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.feedcache.DefaultFeedRepository
import net.kikin.nubecita.core.feedcache.FeedCachePostRemover
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.postinteractions.PostDeletionListener
import javax.inject.Singleton

/**
 * Production binding of [FeedRepository] to the Room-backed
 * [DefaultFeedRepository]. The bench flavor binds an in-process fake instead
 * (src/bench) so the feed widget renders offline (nubecita-epe3).
 *
 * The module class is deliberately NOT `internal`: a `@TestInstallIn(replaces
 * = [FeedRepositoryModule::class])` in another module's androidTest graph has
 * to name it, and an internal module would be invisible there — the same
 * reason `:core:auth`'s `AuthBindingsModule` is public. The bound
 * implementation stays internal; only the module is addressable.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeedRepositoryModule {
    @Binds
    @Singleton
    internal abstract fun bindFeedRepository(impl: DefaultFeedRepository): FeedRepository

    @Binds
    @IntoSet
    internal abstract fun bindFeedCachePostRemover(impl: FeedCachePostRemover): PostDeletionListener
}
