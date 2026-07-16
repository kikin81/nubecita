package net.kikin.nubecita.core.posting.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posting.JvmLocaleProvider
import net.kikin.nubecita.core.posting.LocaleProvider
import net.kikin.nubecita.core.posting.SharedMediaStore
import javax.inject.Singleton

/**
 * Flavor-agnostic Hilt bindings for `:core:posting` (handle resolver, facet
 * extractor, locale provider) — singleton-scoped stateless services. Image
 * byte-source/encoder bindings live in `:core:image`'s `ImageModule`.
 *
 * The `PostingRepository` binding is **not** here: it's flavor-split across
 * `src/production` (real `DefaultPostingRepository`) and `src/bench`
 * (network-free `BenchFakePostingRepository`) under the shared FQN
 * `…di.PostingRepositoryModule` (nubecita-8g28.8). AGP source-set selection
 * picks the right one per `environment` flavor.
 *
 * Public visibility (with internal-only bindings inside) so downstream
 * feature modules' instrumentation tests can swap these via
 * `@TestInstallIn(replaces = [PostingModule::class])`. Mirrors the
 * existing `core/auth/.../AuthBindingsModule` convention.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PostingModule {
    @Binds
    @Singleton
    internal abstract fun bindHandleResolver(impl: DefaultHandleResolver): HandleResolver

    @Binds
    @Singleton
    internal abstract fun bindFacetExtractor(impl: DefaultFacetExtractor): FacetExtractor

    @Binds
    @Singleton
    internal abstract fun bindLocaleProvider(impl: JvmLocaleProvider): LocaleProvider

    @Binds
    @Singleton
    internal abstract fun bindSharedMediaStore(impl: DefaultSharedMediaStore): SharedMediaStore
}
