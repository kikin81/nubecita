package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.BenchFakeSearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsRepository

/**
 * Bench-flavor counterpart to the production [SearchPostsRepositoryModule] at
 * `feature/search/impl/src/production/.../di/SearchPostsRepositoryModule.kt`.
 *
 * AGP source-set selection picks exactly one of the two per variant:
 * - `productionDebug` / `productionRelease` see the production module
 *   (binds `DefaultSearchPostsRepository` → `SearchPostsRepository`).
 * - `benchDebug` / `benchRelease` see this module (binds
 *   [BenchFakeSearchPostsRepository] → `SearchPostsRepository`).
 *
 * The shared FQN matters: both modules sit at
 * `net.kikin.nubecita.feature.search.impl.di.SearchPostsRepositoryModule`, so
 * they cannot coexist on one variant's classpath. Source-set merging takes
 * care of the variant pick automatically — see `:feature:feed:impl`'s
 * parallel production/bench `FeedRepositoryModule` split for the established
 * precedent.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface SearchPostsRepositoryModule {
    @Binds
    fun bindSearchPostsRepository(impl: BenchFakeSearchPostsRepository): SearchPostsRepository
}
