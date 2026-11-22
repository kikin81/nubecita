package net.kikin.nubecita.feature.starterpack.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.starterpack.impl.data.DefaultStarterPackRepository
import net.kikin.nubecita.feature.starterpack.impl.data.StarterPackRepository

/** Hilt binding for [StarterPackRepository] → [DefaultStarterPackRepository]. */
@Module
@InstallIn(SingletonComponent::class)
internal interface StarterPackRepositoryModule {
    @Binds
    fun bindStarterPackRepository(impl: DefaultStarterPackRepository): StarterPackRepository
}
