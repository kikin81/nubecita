package net.kikin.nubecita.core.feedcache.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.feedcache.FeedCacheEvictionCoordinator
import net.kikin.nubecita.core.feedcache.FeedCacheTransactionRunner
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feedcache.RoomFeedCacheTransactionRunner
import javax.inject.Singleton

/**
 * Wires the `:core:feed-cache` graph: the [FeedRepository] read/maintenance
 * surface, the [FeedCacheTransactionRunner] seam, and the
 * [FeedCacheEvictionCoordinator]. The [net.kikin.nubecita.core.feedcache.FeedRemoteMediator.Factory]
 * assisted factory is generated + provided by Hilt automatically.
 *
 * The coordinator is plain-constructed (it needs the app-scoped
 * [CoroutineScope]) and is **not** `start()`ed here — that wiring lands at the
 * `:app` layer when the cache is consumed (sub-project E) or by the refresh
 * worker (sub-project B). See `FeedCacheEvictionCoordinator`'s KDoc.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FeedCacheModule {
    // [FeedRepository] is bound per flavor: the production source set binds
    // `DefaultFeedRepository` (Room-backed); the bench source set binds an
    // in-process fake so the feed widget renders offline (nubecita-epe3). See
    // `FeedRepositoryModule` in src/production and src/bench.

    @Binds
    @Singleton
    internal abstract fun bindTransactionRunner(impl: RoomFeedCacheTransactionRunner): FeedCacheTransactionRunner

    companion object {
        @Provides
        @Singleton
        fun provideEvictionCoordinator(
            @ApplicationScope scope: CoroutineScope,
            sessionStateProvider: SessionStateProvider,
            feedRepository: FeedRepository,
        ): FeedCacheEvictionCoordinator =
            FeedCacheEvictionCoordinator(
                scope = scope,
                sessionStateProvider = sessionStateProvider,
                feedRepository = feedRepository,
            )
    }
}
