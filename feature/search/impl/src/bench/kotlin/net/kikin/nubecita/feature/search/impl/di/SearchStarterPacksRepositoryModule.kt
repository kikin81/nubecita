package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.BenchFakeSearchStarterPacksRepository
import net.kikin.nubecita.feature.search.impl.data.SearchStarterPacksRepository

/**
 * Bench-flavor counterpart to the production `SearchStarterPacksRepositoryModule`: binds the
 * network-free [BenchFakeSearchStarterPacksRepository]. The shared FQN means exactly one of the
 * two is on a variant's classpath.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface SearchStarterPacksRepositoryModule {
    @Binds
    fun bindSearchStarterPacksRepository(impl: BenchFakeSearchStarterPacksRepository): SearchStarterPacksRepository
}
