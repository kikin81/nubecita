package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.BenchFakeExploreRepository
import net.kikin.nubecita.feature.search.impl.data.ExploreRepository

/**
 * Bench-flavor counterpart to the production [ExploreRepositoryModule] at
 * `feature/search/impl/src/production/.../di/ExploreRepositoryModule.kt`.
 *
 * AGP source-set selection picks exactly one of the two per variant:
 * - `productionDebug` / `productionRelease` → production module
 *   (binds `DefaultExploreRepository` → `ExploreRepository`).
 * - `benchDebug` / `benchRelease` → this module
 *   (binds [BenchFakeExploreRepository] → `ExploreRepository`).
 *
 * Mirrors the [SearchFeedsRepositoryModule] / bench split.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface ExploreRepositoryModule {
    @Binds
    fun bindExploreRepository(impl: BenchFakeExploreRepository): ExploreRepository
}
