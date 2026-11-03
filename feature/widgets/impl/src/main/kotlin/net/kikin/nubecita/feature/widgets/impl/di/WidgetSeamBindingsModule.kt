package net.kikin.nubecita.feature.widgets.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.widgetsync.PlacedWidgets
import net.kikin.nubecita.core.widgetsync.WidgetImagePrefetcher
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import net.kikin.nubecita.feature.widgets.impl.image.GlanceWidgetImagePrefetcher
import net.kikin.nubecita.feature.widgets.impl.widget.AppWidgetManagerPlacedWidgets
import net.kikin.nubecita.feature.widgets.impl.widget.GlanceWidgetUpdater
import javax.inject.Singleton

/**
 * Binds the real `:core:widget-sync` Glance seams to their implementations
 * (D-C3 / task 8): [WidgetUpdater] → [GlanceWidgetUpdater] (live re-render) and
 * [WidgetImagePrefetcher] → [GlanceWidgetImagePrefetcher] (thumbnail decode +
 * eviction), and [PlacedWidgets] → [AppWidgetManagerPlacedWidgets] (the
 * background refresh's consumer gate, nubecita-kvh0).
 *
 * `:app` takes `:feature:widgets:impl` in BOTH flavors (bench renders the
 * widget offline against fakes), so these are the live bindings everywhere and
 * `:core:widget-sync`'s no-op / ungated fallbacks are reached only by tests.
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

    @Binds
    @Singleton
    abstract fun bindPlacedWidgets(impl: AppWidgetManagerPlacedWidgets): PlacedWidgets
}
