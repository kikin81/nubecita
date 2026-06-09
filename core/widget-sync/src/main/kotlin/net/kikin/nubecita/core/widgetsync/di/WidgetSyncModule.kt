package net.kikin.nubecita.core.widgetsync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.widgetsync.NoOpWidgetUpdater
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import javax.inject.Singleton

/**
 * Wires the `:core:widget-sync` (B) graph. Binds the no-op [WidgetUpdater]
 * default (D-B5) so B is Glance-free and fully testable; sub-project C
 * (`:feature:widgets`) overrides this with the Glance-backed implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetSyncModule {
    @Binds
    @Singleton
    internal abstract fun bindWidgetUpdater(impl: NoOpWidgetUpdater): WidgetUpdater
}
