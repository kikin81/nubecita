package net.kikin.nubecita.core.posting.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posting.PostingRepository
import javax.inject.Singleton

/**
 * Hilt bindings for `:core:posting`. Both the public
 * [PostingRepository] and the internal [AttachmentByteSource] are
 * bound at the singleton scope — they're stateless service objects
 * with no per-call setup cost.
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
    internal abstract fun bindAttachmentByteSource(impl: ContentResolverAttachmentByteSource): AttachmentByteSource

    @Binds
    @Singleton
    internal abstract fun bindAttachmentEncoder(impl: BitmapAttachmentEncoder): AttachmentEncoder
}
