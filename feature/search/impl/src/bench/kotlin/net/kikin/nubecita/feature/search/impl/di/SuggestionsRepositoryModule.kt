package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.BenchFakeSuggestionsRepository
import net.kikin.nubecita.feature.search.impl.data.SuggestionsRepository

/**
 * Bench-flavor counterpart to the production [SuggestionsRepositoryModule] at
 * `feature/search/impl/src/production/.../di/SuggestionsRepositoryModule.kt`.
 *
 * AGP source-set selection picks exactly one of the two per variant:
 * - `productionDebug` / `productionRelease` → production module
 *   (binds `DefaultSuggestionsRepository` → `SuggestionsRepository`).
 * - `benchDebug` / `benchRelease` → this module
 *   (binds [BenchFakeSuggestionsRepository] → `SuggestionsRepository`).
 *
 * Mirrors the [SearchFeedsRepositoryModule] / bench split.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface SuggestionsRepositoryModule {
    @Binds
    fun bindSuggestionsRepository(impl: BenchFakeSuggestionsRepository): SuggestionsRepository
}
