package net.kikin.nubecita.widget

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.widgetsync.NoOpWidgetImagePrefetcher
import net.kikin.nubecita.core.widgetsync.NoOpWidgetUpdater
import net.kikin.nubecita.core.widgetsync.WidgetImagePrefetcher
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import javax.inject.Singleton

/**
 * Production-flavor bindings for the `:core:widget-sync` Glance seams (D-C3):
 * [WidgetUpdater] (re-render) and [WidgetImagePrefetcher] (head-thumbnail
 * decode).
 *
 * Both bindings live at the `:app` flavor level rather than in
 * `:core:widget-sync` so the production flavor can bind the Glance/Coil-backed
 * implementations while the bench flavor keeps no-ops — neither colliding as a
 * Hilt duplicate binding nor pulling `androidx.glance` into the bench build.
 *
 * **Placeholder:** currently both no-ops, because sub-project C hasn't shipped
 * the Glance-backed `WidgetUpdater` / image-prefetch pipeline yet. Task group 8
 * of `add-glance-feed-widgets` swaps these to the real implementations from
 * `:feature:widgets:impl` — binding changes confined to this production module.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetSeamBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindWidgetUpdater(impl: NoOpWidgetUpdater): WidgetUpdater

    @Binds
    @Singleton
    internal abstract fun bindWidgetImagePrefetcher(impl: NoOpWidgetImagePrefetcher): WidgetImagePrefetcher
}
