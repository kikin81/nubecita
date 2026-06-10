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
 * Bench-flavor bindings for the `:core:widget-sync` Glance seams (D-C3): always
 * no-ops.
 *
 * The `@HiltWorker` `WidgetRefreshWorker` is part of the graph in every flavor,
 * so even the bench build must resolve [WidgetUpdater] and [WidgetImagePrefetcher]
 * at compile time — but bench never registers the refresh scheduler
 * (production-only initializer), so no widget work runs and these no-ops are
 * never actually invoked. Keeping the binds here (rather than in
 * `:core:widget-sync`) lets the production flavor swap in the Glance/Coil-backed
 * implementations without a duplicate-binding clash, and keeps the bench build
 * free of any `androidx.glance` dependency.
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
