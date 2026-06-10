package net.kikin.nubecita.widget

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.widgetsync.NoOpWidgetUpdater
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import javax.inject.Singleton

/**
 * Bench-flavor [WidgetUpdater] binding (D-C3): always the no-op.
 *
 * The `@HiltWorker` `WidgetRefreshWorker` is part of the graph in every flavor,
 * so even the bench build must resolve [WidgetUpdater] at compile time — but
 * bench never registers the refresh scheduler (production-only initializer), so
 * no widget work runs and this no-op is never actually invoked. Keeping the
 * bind here (rather than in `:core:widget-sync`) lets the production flavor
 * swap in the Glance-backed updater without a duplicate-binding clash, and
 * keeps the bench build free of any `androidx.glance` dependency.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetUpdaterModule {
    @Binds
    @Singleton
    internal abstract fun bindWidgetUpdater(impl: NoOpWidgetUpdater): WidgetUpdater
}
