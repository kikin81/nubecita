package net.kikin.nubecita.feature.starterpack.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.starterpack.impl.data.BenchFakeStarterPackRepository
import net.kikin.nubecita.feature.starterpack.impl.data.StarterPackRepository

/**
 * Bench-flavor counterpart to the production `StarterPackRepositoryModule`: binds the
 * network-free [BenchFakeStarterPackRepository]. AGP's source-set selection picks exactly one
 * of the two per variant (the shared FQN is why they cannot coexist on a classpath).
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface StarterPackRepositoryModule {
    @Binds
    fun bindStarterPackRepository(impl: BenchFakeStarterPackRepository): StarterPackRepository
}
