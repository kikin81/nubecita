package net.kikin.nubecita.core.widgetsync.di

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
 * Test-only bindings for the `:core:widget-sync` Glance seams in this module's
 * own instrumentation test.
 *
 * The production bindings now live at the `:app` flavor level (D-C3), so the
 * library's *main* graph binds neither [WidgetUpdater] nor
 * [WidgetImagePrefetcher]. This module supplies the no-ops to the
 * `@HiltAndroidTest` graph that constructs
 * [net.kikin.nubecita.core.widgetsync.worker.WidgetRefreshWorker] through the
 * `HiltWorkerFactory` (which transitively needs both seams via
 * [net.kikin.nubecita.core.widgetsync.worker.WidgetRefreshRunner]).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TestWidgetSeamBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindWidgetUpdater(impl: NoOpWidgetUpdater): WidgetUpdater

    @Binds
    @Singleton
    internal abstract fun bindWidgetImagePrefetcher(impl: NoOpWidgetImagePrefetcher): WidgetImagePrefetcher
}
