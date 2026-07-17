package net.kikin.nubecita.feature.widgets.impl.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kikin.nubecita.feature.widgets.impl.di.widgetEntryPoint

/**
 * Forces a full re-render of every placed feed widget after the app is updated.
 *
 * **Why this exists (nubecita-ew77 — the top FATAL, `babbf348`).** A Glance
 * `LazyColumn` renders as a RemoteViews collection whose per-item taps go
 * through a shared `PendingIntent` **template**. glance `1.3.0-alpha01` (shipped
 * in app 1.272.0, where the crash first appeared) built that template to launch
 * `ActionTrampolineActivity` for list-item activity clicks even on Android 11;
 * `1.3.0-alpha02` fixed it to a direct fill-in with no trampoline. But the
 * launcher **caches the collection template**, and Glance never re-renders a
 * widget on app update — its own `MyPackageReplacedReceiver` only calls
 * `cleanReceivers()`. So a widget added under alpha01 keeps firing the stale
 * trampoline template against the current build's fill-in intents, which lack
 * the `ActionIntentKey` the old template expects, and the trampoline crashes.
 *
 * [net.kikin.nubecita.core.widgetsync.WidgetUpdater.updateFeedWidgets] calls
 * `GlanceAppWidget.updateAll`, which re-runs `setPendingIntentTemplate` and
 * replaces the stale template with the current (safe) one — healing the widget
 * the next time the app updates. It cannot reach launchers that ignore
 * `updateAppWidget` (cloned / parallel-space), which is the best achievable from
 * app code for a launcher-cached template.
 *
 * Hilt can't `@Inject` a manifest receiver's constructor, so the updater is
 * pulled from the application graph via [widgetEntryPoint] — the house pattern
 * for non-`@AndroidEntryPoint` entry points (mirrors [DmReplyReceiver]).
 */
internal class WidgetPackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val updater = context.widgetEntryPoint().widgetUpdater()
        val pending = goAsync()
        // goAsync keeps the process alive until finish(); updateFeedWidgets is a
        // bounded AppWidgetManager re-render (well under the ~10s broadcast limit)
        // and already isolates each widget's failure internally.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updater.updateFeedWidgets()
            } finally {
                pending.finish()
            }
        }
    }
}
