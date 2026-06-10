package net.kikin.nubecita.feature.widgets.impl.action

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import net.kikin.nubecita.feature.widgets.impl.di.widgetEntryPoint

/**
 * Manual-refresh action (D-C7): enqueues the on-demand widget-refresh worker via
 * the [net.kikin.nubecita.core.widgetsync.WidgetRefreshLauncher] seam. The widget
 * render path itself issues no network — this just schedules a background
 * refresh (KEEP-deduped, network-constrained); the worker writes the cache and
 * (once C's updater is bound) re-renders the widget.
 *
 * Wired with `actionRunCallback<RefreshWidgetAction>()`; the callback runs in
 * the app process with no Hilt scope, so it resolves the launcher through the
 * `@EntryPoint`.
 */
internal class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        context.widgetEntryPoint().widgetRefreshLauncher().refreshNow()
    }
}
