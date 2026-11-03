package net.kikin.nubecita.core.widgetsync

import javax.inject.Inject

/**
 * Framework-free seam the refresh runner asks whether any feed widget is
 * actually on a home screen (nubecita-kvh0).
 *
 * `:core:widget-sync` (B) must not depend on `androidx.glance` nor know the
 * widget receiver classes, so this interface is the only thing the runner
 * knows about; the real `AppWidgetManager`-backed implementation lives in
 * `:feature:widgets:impl` (mirrors [WidgetUpdater] and [WidgetImagePrefetcher]).
 *
 * It exists so a signed-in user with no placed widget stops paying for a
 * networked feed refresh every 15 minutes. The battery cost is the obvious
 * half; the other half is that each run which reaches the network can rotate
 * the OAuth refresh token, and an AT Proto refresh token is single-use — a
 * rotation whose response is lost strands the token and the next attempt is
 * rejected as reuse, which is a permanent logout (epic nubecita-09xt). Every
 * avoidable rotation is a logout risk avoided.
 */
interface PlacedWidgets {
    /** Whether at least one Following / Discover widget is currently placed. */
    suspend fun anyPlaced(): Boolean
}

/**
 * Fallback [PlacedWidgets] that does **not** gate — it answers `true`.
 *
 * Unlike the other no-op seams in this module, the inert answer here is the
 * permissive one: reporting "nothing is placed" would switch the background
 * refresh off, so a build without the real implementation would silently ship
 * a stale widget. Both app flavors bind the real
 * `AppWidgetManager`-backed implementation, so this is reached only by tests
 * and by any future consumer that has no widgets at all.
 */
class UngatedPlacedWidgets
    @Inject
    constructor() : PlacedWidgets {
        override suspend fun anyPlaced(): Boolean = true
    }
