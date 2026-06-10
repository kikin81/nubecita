package net.kikin.nubecita.feature.widgets.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.widgetsync.WidgetImagePrefetcher
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import net.kikin.nubecita.feature.widgets.impl.image.GlanceWidgetImagePrefetcher
import net.kikin.nubecita.feature.widgets.impl.widget.GlanceWidgetUpdater
import javax.inject.Singleton

/**
 * Binds the real `:core:widget-sync` Glance seams to their implementations
 * (D-C3 / task 8): [WidgetUpdater] → [GlanceWidgetUpdater] (live re-render) and
 * [WidgetImagePrefetcher] → [GlanceWidgetImagePrefetcher] (thumbnail decode +
 * eviction).
 *
 * This module ships only in the production flavor: `:app` depends on
 * `:feature:widgets:impl` via `productionImplementation`, so these bindings
 * enter only the production graph. The bench flavor binds the no-ops in its own
 * `:app/src/bench` module and never sees this one — no duplicate-binding clash,
 * no `androidx.glance` in bench.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetSeamBindingsModule {
    @Binds
    @Singleton
    abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater

    @Binds
    @Singleton
    abstract fun bindWidgetImagePrefetcher(impl: GlanceWidgetImagePrefetcher): WidgetImagePrefetcher
}
