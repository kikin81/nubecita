package net.kikin.nubecita.feature.widgets.impl.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import timber.log.Timber
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
            // Isolate each widget: one widget's updateAll failure (Glance state /
            // AppWidgetManager IPC / render error) must not skip the other. The
            // worker's call site already swallows a thrown updater (no retry), but
            // isolating here means both widgets always get a chance to re-render.
            updateSafely(FollowingFeedWidget())
            updateSafely(DiscoverFeedWidget())
        }

        private suspend fun updateSafely(widget: GlanceAppWidget) {
            try {
                widget.updateAll(context)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Timber.tag(TAG).w(throwable, "widget updateAll failed: %s", widget::class.simpleName)
            }
        }

        private companion object {
            const val TAG = "GlanceWidgetUpdater"
        }
    }
