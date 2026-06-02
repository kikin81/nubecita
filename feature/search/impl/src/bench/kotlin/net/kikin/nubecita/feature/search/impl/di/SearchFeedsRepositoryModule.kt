package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.BenchFakeSearchFeedsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchFeedsRepository

/**
 * Bench-flavor counterpart to the production [SearchFeedsRepositoryModule] at
 * `feature/search/impl/src/production/.../di/SearchFeedsRepositoryModule.kt`.
 *
 * AGP source-set selection picks exactly one of the two per variant:
 * - `productionDebug` / `productionRelease` see the production module
 *   (binds `DefaultSearchFeedsRepository` → `SearchFeedsRepository`).
 * - `benchDebug` / `benchRelease` see this module (binds
 *   [BenchFakeSearchFeedsRepository] → `SearchFeedsRepository`).
 *
 * The shared FQN matters: both modules sit at
 * `net.kikin.nubecita.feature.search.impl.di.SearchFeedsRepositoryModule`, so
 * they cannot coexist on one variant's classpath. Source-set merging takes
 * care of the variant pick automatically — see `:feature:feed:impl`'s
 * parallel production/bench `FeedRepositoryModule` split for the established
 * precedent.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface SearchFeedsRepositoryModule {
    @Binds
    fun bindSearchFeedsRepository(impl: BenchFakeSearchFeedsRepository): SearchFeedsRepository
}
