package net.kikin.nubecita.core.widgetsync.worker

import net.kikin.nubecita.core.widgetsync.WidgetRefreshLauncher
import javax.inject.Inject

/**
 * Delegates [WidgetRefreshLauncher.refreshNow] to the internal
 * [WidgetWorkScheduler] (which enqueues the unique one-time refresh with
 * `KEEP`). Keeps the scheduler seam module-internal while giving C a public
 * trigger.
 */
internal class DefaultWidgetRefreshLauncher
    @Inject
    constructor(
        private val scheduler: WidgetWorkScheduler,
    ) : WidgetRefreshLauncher {
        override suspend fun refreshNow() = scheduler.refreshNow()
    }
