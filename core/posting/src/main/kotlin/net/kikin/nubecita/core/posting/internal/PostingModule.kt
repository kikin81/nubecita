package net.kikin.nubecita.core.posting.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posting.JvmLocaleProvider
import net.kikin.nubecita.core.posting.LocaleProvider
import net.kikin.nubecita.core.posting.PostingRepository
import javax.inject.Singleton

/**
 * Hilt bindings for `:core:posting`. The public [PostingRepository] is
 * bound at the singleton scope — a stateless service object with no
 * per-call setup cost. Image byte-source/encoder bindings live in
 * `:core:image`'s `ImageModule`.
 *
 * Public visibility (with internal-only bindings inside) so downstream
 * feature modules' instrumentation tests can swap bindings via
 * `@TestInstallIn(replaces = [PostingModule::class])`. Mirrors the
 * existing `core/auth/.../AuthBindingsModule` convention.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PostingModule {
    @Binds
    @Singleton
    internal abstract fun bindPostingRepository(impl: DefaultPostingRepository): PostingRepository

    @Binds
    @Singleton
    internal abstract fun bindHandleResolver(impl: DefaultHandleResolver): HandleResolver

    @Binds
    @Singleton
    internal abstract fun bindFacetExtractor(impl: DefaultFacetExtractor): FacetExtractor

    @Binds
    @Singleton
    internal abstract fun bindLocaleProvider(impl: JvmLocaleProvider): LocaleProvider
}
