package net.kikin.nubecita.feature.composer.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.composer.impl.data.DefaultParentFetchSource
import net.kikin.nubecita.feature.composer.impl.data.ParentFetchSource

/**
 * Hilt bindings for `:feature:composer:impl`.
 *
 * Public visibility (with internal binding methods) so downstream
 * instrumentation tests can swap bindings via `@TestInstallIn`.
 * Mirrors the `:core:posting` `PostingModule` convention.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ComposerModule {
    @Binds
    internal abstract fun bindParentFetchSource(impl: DefaultParentFetchSource): ParentFetchSource
}
