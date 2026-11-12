package net.kikin.nubecita.wear.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.wear.data.PhoneSessionSource
import net.kikin.nubecita.wear.data.WatchDmRepository
import net.kikin.nubecita.wear.data.sync.DmSyncObserver
import timber.log.Timber
import javax.inject.Inject

/** Asks the system to re-render every placed launcher widget. */
fun interface LauncherWidgetRefresh {
    suspend fun refresh()
}

/**
 * Re-renders placed launcher widgets through Glance Wear. On Wear OS 7 the update
 * is pushed with a document rendered by [widget] itself, so it reads the real
 * session and count (a stand-in would push its own data to every placed widget).
 */
internal class GlanceLauncherWidgetRefresh
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        session: PhoneSessionSource,
        repository: WatchDmRepository,
    ) : LauncherWidgetRefresh {
        internal val widget = liveLauncherWidget(session = { session }, repository = { repository })

        override suspend fun refresh() = widget.triggerUpdateAll(context)
    }

/**
 * Refreshes the launcher widget whenever synced DMs change — a new snapshot,
 * or the DMs being cleared on sign-out or an account switch. Push-only: this
 * is the widget's only refresh path.
 */
internal class LauncherWidgetUpdater
    @Inject
    constructor(
        @param:ApplicationScope private val scope: CoroutineScope,
        private val widget: LauncherWidgetRefresh,
    ) : DmSyncObserver {
        override fun onDmsChanged() {
            scope.launch {
                // The app scope has no handler: a failed refresh must not crash the app.
                try {
                    widget.refresh()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.w(error, "Launcher widget refresh failed")
                }
            }
        }
    }
