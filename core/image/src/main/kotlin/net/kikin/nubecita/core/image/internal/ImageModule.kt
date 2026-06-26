package net.kikin.nubecita.core.image.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.image.ImageByteSource
import net.kikin.nubecita.core.image.ImageDimensionDecoder
import net.kikin.nubecita.core.image.ImageEncoder
import javax.inject.Singleton

/**
 * Hilt bindings for `:core:image`. Both the [ImageByteSource] and the
 * [ImageEncoder] are bound at the singleton scope — they're stateless
 * service objects with no per-call setup cost.
 *
 * Public visibility (with internal-only bindings inside) so downstream
 * feature modules' instrumentation tests can swap bindings via
 * `@TestInstallIn(replaces = [ImageModule::class])`. Mirrors the
 * `:core:posting` `PostingModule` convention.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ImageModule {
    @Binds
    @Singleton
    internal abstract fun bindImageByteSource(impl: ContentResolverImageByteSource): ImageByteSource

    @Binds
    @Singleton
    internal abstract fun bindImageEncoder(impl: BitmapImageEncoder): ImageEncoder

    @Binds
    @Singleton
    internal abstract fun bindImageDimensionDecoder(impl: BitmapImageDimensionDecoder): ImageDimensionDecoder
}
