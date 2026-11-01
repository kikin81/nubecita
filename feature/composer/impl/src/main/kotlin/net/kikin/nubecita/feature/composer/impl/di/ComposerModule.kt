package net.kikin.nubecita.feature.composer.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.composer.impl.data.DefaultQuotePostFetcher
import net.kikin.nubecita.feature.composer.impl.data.QuotePostFetcher

/**
 * Hilt bindings for `:feature:composer:impl`.
 *
 * Public visibility (with internal binding methods) so downstream
 * instrumentation tests can swap bindings via `@TestInstallIn`.
 * Mirrors the `:core:posting` `PostingModule` convention.
 *
 * The `ParentFetchSource` binding is flavor-split into `ParentFetchSourceModule`
 * (`src/production` binds the network-backed `DefaultParentFetchSource`,
 * `src/bench` binds the offline `BenchParentFetchSource`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ComposerModule {
    @Binds
    internal abstract fun bindQuotePostFetcher(impl: DefaultQuotePostFetcher): QuotePostFetcher
}
