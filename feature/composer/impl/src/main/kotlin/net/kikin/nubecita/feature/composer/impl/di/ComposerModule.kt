package net.kikin.nubecita.feature.composer.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.composer.impl.data.ParentFetchSource
import net.kikin.nubecita.feature.composer.impl.data.StubParentFetchSource

/**
 * Hilt bindings for `:feature:composer:impl`.
 *
 * wtq.3 ships only the [ParentFetchSource] binding pointed at the
 * indefinite-loading [StubParentFetchSource]. wtq.6 swaps that
 * binding to an `app.bsky.feed.getPostThread`-backed implementation
 * — pure additive change, no consumer-side migration.
 *
 * Public visibility (with internal binding methods) so downstream
 * instrumentation tests can swap bindings via `@TestInstallIn`.
 * Mirrors the `:core:posting` `PostingModule` convention.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ComposerModule {
    @Binds
    internal abstract fun bindParentFetchSource(impl: StubParentFetchSource): ParentFetchSource
}
