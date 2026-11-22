package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.DefaultSearchStarterPacksRepository
import net.kikin.nubecita.feature.search.impl.data.SearchStarterPacksRepository

/** Hilt binding for [SearchStarterPacksRepository] → [DefaultSearchStarterPacksRepository]. */
@Module
@InstallIn(SingletonComponent::class)
internal interface SearchStarterPacksRepositoryModule {
    @Binds
    fun bindSearchStarterPacksRepository(impl: DefaultSearchStarterPacksRepository): SearchStarterPacksRepository
}
