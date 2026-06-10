package net.kikin.nubecita.feature.widgets.impl.image.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.widgets.impl.image.CoilThumbnailDecoder
import net.kikin.nubecita.feature.widgets.impl.image.ThumbnailDecoder

/**
 * Binds the widget image-prefetch internals (D-C5). Module-internal and
 * flavor-agnostic: [ThumbnailDecoder] → the Coil-backed implementation.
 *
 * The `WidgetImagePrefetcher` *seam* binding (no-op vs the
 * [net.kikin.nubecita.feature.widgets.impl.image.GlanceWidgetImagePrefetcher]
 * real impl) is decided at the `:app` flavor level, not here.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetImageModule {
    @Binds
    internal abstract fun bindThumbnailDecoder(impl: CoilThumbnailDecoder): ThumbnailDecoder
}
