package net.kikin.nubecita.core.widgetsync

/**
 * Public trigger for an **on-demand** widget-feed refresh (widget add / manual
 * refresh), used by the widgets sub-project. The scheduling seam
 * (`WidgetWorkScheduler`) is module-internal, so this narrow interface is the
 * only thing C reaches for — it enqueues the unique one-time refresh
 * (`ExistingWorkPolicy.KEEP`, network-constrained) without exposing the
 * periodic schedule/cancel surface.
 */
interface WidgetRefreshLauncher {
    /** Enqueue a one-time refresh now (deduplicated against an in-flight one). */
    suspend fun refreshNow()
}
