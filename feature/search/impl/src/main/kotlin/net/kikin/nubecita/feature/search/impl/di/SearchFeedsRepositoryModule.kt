package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.DefaultSearchFeedsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchFeedsRepository

/**
 * Hilt binding for [SearchFeedsRepository] → [DefaultSearchFeedsRepository].
 * Mirrors [SearchPostsRepositoryModule] / [SearchActorsRepositoryModule].
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface SearchFeedsRepositoryModule {
    @Binds
    fun bindSearchFeedsRepository(impl: DefaultSearchFeedsRepository): SearchFeedsRepository
}
