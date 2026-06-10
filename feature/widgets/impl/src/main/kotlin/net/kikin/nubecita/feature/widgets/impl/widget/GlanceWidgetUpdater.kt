package net.kikin.nubecita.feature.widgets.impl.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import javax.inject.Inject

/**
 * Real Glance-backed [WidgetUpdater] (D-C3): re-renders every placed Following /
 * Discover widget by calling `updateAll` on each `GlanceAppWidget`, which
 * re-runs `provideGlance` against the freshly-refreshed cache. Invoked by the
 * background worker (B) after a successful refresh.
 *
 * Bound only in the production flavor — `:app` depends on `:feature:widgets:impl`
 * via `productionImplementation`, so this module (and its bindings) never enter
 * the bench graph, which keeps its `NoOpWidgetUpdater`.
 */
internal class GlanceWidgetUpdater
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WidgetUpdater {
        override suspend fun updateFeedWidgets() {
            FollowingFeedWidget().updateAll(context)
            DiscoverFeedWidget().updateAll(context)
        }
    }
