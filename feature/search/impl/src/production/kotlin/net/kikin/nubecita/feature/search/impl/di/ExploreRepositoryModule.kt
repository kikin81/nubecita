package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.DefaultExploreRepository
import net.kikin.nubecita.feature.search.impl.data.ExploreRepository

/**
 * Hilt binding for [ExploreRepository] → [DefaultExploreRepository], a singleton so
 * every presenter of the Explore content shares one. Mirrors [SearchFeedsRepositoryModule].
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface ExploreRepositoryModule {
    @Binds
    fun bindExploreRepository(impl: DefaultExploreRepository): ExploreRepository
}
