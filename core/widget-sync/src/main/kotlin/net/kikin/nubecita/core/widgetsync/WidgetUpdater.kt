package net.kikin.nubecita.core.widgetsync

import javax.inject.Inject

/**
 * Glance-free seam the refresh runner calls after a successful background
 * refresh, to trigger a widget re-render (D-B5). `:core:widget-sync` (B) must
 * not depend on `androidx.glance`, so this interface is the only thing the
 * runner knows about; the real Glance-backed implementation (calls
 * `GlanceAppWidget.updateAll(context)`) is provided by sub-project C
 * (`:feature:widgets`), which overrides the default no-op binding below.
 */
interface WidgetUpdater {
    /** Re-render every placed feed widget from the freshly-refreshed cache. */
    suspend fun updateFeedWidgets()
}

/**
 * Default [WidgetUpdater] that does nothing. Lets B ship + test fully without a
 * widget (C doesn't exist yet) — the worker refreshes the cache and the update
 * call is a harmless no-op until C swaps in the Glance implementation.
 */
class NoOpWidgetUpdater
    @Inject
    constructor() : WidgetUpdater {
        override suspend fun updateFeedWidgets() = Unit
    }
