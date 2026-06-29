package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.DefaultSuggestionsRepository
import net.kikin.nubecita.feature.search.impl.data.SuggestionsRepository

/**
 * Hilt binding for [SuggestionsRepository] → [DefaultSuggestionsRepository].
 * Mirrors [SearchFeedsRepositoryModule].
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface SuggestionsRepositoryModule {
    @Binds
    fun bindSuggestionsRepository(impl: DefaultSuggestionsRepository): SuggestionsRepository
}
