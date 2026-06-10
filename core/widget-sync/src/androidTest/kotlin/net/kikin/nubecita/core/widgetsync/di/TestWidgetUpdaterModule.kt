package net.kikin.nubecita.core.widgetsync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.widgetsync.NoOpWidgetUpdater
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import javax.inject.Singleton

/**
 * Test-only [WidgetUpdater] binding for `:core:widget-sync`'s own
 * instrumentation test.
 *
 * The production binding now lives at the `:app` flavor level (D-C3) so the
 * production flavor can bind the Glance-backed updater while bench keeps the
 * no-op — without a Hilt duplicate-binding clash. As a consequence the
 * library's *main* graph no longer binds [WidgetUpdater], so this module
 * supplies the no-op to the `@HiltAndroidTest` graph that constructs
 * [net.kikin.nubecita.core.widgetsync.worker.WidgetRefreshWorker] through the
 * `HiltWorkerFactory`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TestWidgetUpdaterModule {
    @Binds
    @Singleton
    internal abstract fun bindWidgetUpdater(impl: NoOpWidgetUpdater): WidgetUpdater
}
