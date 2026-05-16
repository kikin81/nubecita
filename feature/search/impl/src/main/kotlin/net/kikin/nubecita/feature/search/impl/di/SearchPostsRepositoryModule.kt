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
 *
 * Form mirrors `:feature:feed:impl/di/FeedRepositoryModule` — `internal
 * interface` + `@Binds fun`, not the `internal abstract class` form.
 * Both are sanctioned Hilt @Binds shapes; we pick the interface form
 * to keep every feature-internal repository binding in lockstep.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface SearchPostsRepositoryModule {
    @Binds
    fun bindSearchPostsRepository(impl: DefaultSearchPostsRepository): SearchPostsRepository
}
