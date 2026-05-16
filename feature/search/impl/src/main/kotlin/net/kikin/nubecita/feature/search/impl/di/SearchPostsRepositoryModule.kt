package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.DefaultSearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsRepository

/**
 * Hilt binding for [SearchPostsRepository] → [DefaultSearchPostsRepository].
 * `@InstallIn(SingletonComponent::class)` so the singleton repo is
 * available to any `@HiltViewModel` that eventually injects it
 * (nubecita-vrba.6's `SearchPostsViewModel`).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SearchPostsRepositoryModule {
    @Binds
    internal abstract fun bindsSearchPostsRepository(impl: DefaultSearchPostsRepository): SearchPostsRepository
}
