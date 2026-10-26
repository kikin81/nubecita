package net.kikin.nubecita.core.widgetsync.worker

import net.kikin.nubecita.core.wearsync.WearFeedRefreshTrigger
import javax.inject.Inject

/**
 * The watch's "refresh now" (nubecita-jh9c.5) is the widget's existing
 * one-time refresh: network-constrained, `KEEP` (an in-flight refresh isn't
 * duplicated), tagged `RefreshCaller.Widget`, and it publishes to the watch
 * when it finishes. No new worker, no new token-refresh caller.
 */
internal class WidgetWearFeedRefreshTrigger
    @Inject
    constructor(
        private val scheduler: WidgetWorkScheduler,
    ) : WearFeedRefreshTrigger {
        override suspend fun refreshFeedsNow() = scheduler.refreshNow()
    }
