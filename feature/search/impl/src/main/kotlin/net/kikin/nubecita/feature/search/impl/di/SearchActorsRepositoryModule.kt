package net.kikin.nubecita.feature.search.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.search.impl.data.DefaultSearchActorsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchActorsRepository

/**
 * Hilt binding for [SearchActorsRepository] → [DefaultSearchActorsRepository].
 * `@InstallIn(SingletonComponent::class)` so the singleton repo is
 * available to any `@HiltViewModel` that eventually injects it
 * (nubecita-vrba.7's `SearchActorsViewModel`).
 *
 * Form mirrors [SearchPostsRepositoryModule] and
 * `:feature:feed:impl/di/FeedRepositoryModule` — `internal interface`
 * + `@Binds fun`, not the `internal abstract class` form. Both are
 * sanctioned Hilt @Binds shapes; we pick the interface form to keep
 * every feature-internal repository binding in lockstep.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface SearchActorsRepositoryModule {
    @Binds
    fun bindSearchActorsRepository(impl: DefaultSearchActorsRepository): SearchActorsRepository
}
