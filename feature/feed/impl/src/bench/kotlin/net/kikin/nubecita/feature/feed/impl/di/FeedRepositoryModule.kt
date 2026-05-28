package net.kikin.nubecita.feature.feed.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.feed.impl.data.BenchFakeFeedRepository
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository

/**
 * Bench-flavor counterpart to the production [FeedRepositoryModule] at
 * `feature/feed/impl/src/production/.../di/FeedRepositoryModule.kt`.
 *
 * AGP source-set selection picks exactly one of the two per variant:
 * - `productionDebug` / `productionRelease` see the production module
 *   (binds `DefaultFeedRepository` → `FeedRepository`).
 * - `benchDebug` / `benchRelease` see this module (binds
 *   [BenchFakeFeedRepository] → `FeedRepository`).
 *
 * The shared FQN matters: both modules sit at
 * `net.kikin.nubecita.feature.feed.impl.di.FeedRepositoryModule`, so they
 * cannot coexist on one variant's classpath. Source-set merging takes
 * care of the variant pick automatically — see `:core:auth`'s parallel
 * production/bench `AuthBindingsModule` split landed in Section A1 (#330)
 * for the established precedent.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface FeedRepositoryModule {
    @Binds
    fun bindFeedRepository(impl: BenchFakeFeedRepository): FeedRepository
}
