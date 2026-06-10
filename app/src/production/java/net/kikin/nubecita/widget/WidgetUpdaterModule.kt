package net.kikin.nubecita.widget

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.widgetsync.NoOpWidgetUpdater
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import javax.inject.Singleton

/**
 * Production-flavor [WidgetUpdater] binding (D-C3).
 *
 * The binding lives at the `:app` flavor level rather than in
 * `:core:widget-sync` so the production flavor can bind the Glance-backed
 * updater while the bench flavor keeps the no-op — neither colliding as a Hilt
 * duplicate binding nor pulling `androidx.glance` into the bench build.
 *
 * **Placeholder:** currently the no-op, because sub-project C hasn't shipped
 * the Glance-backed `WidgetUpdater` yet. Task group 8 of `add-glance-feed-widgets`
 * swaps this to the real `GlanceWidgetUpdater` from `:feature:widgets:impl` —
 * a single-line binding change confined to this production module.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetUpdaterModule {
    @Binds
    @Singleton
    internal abstract fun bindWidgetUpdater(impl: NoOpWidgetUpdater): WidgetUpdater
}
