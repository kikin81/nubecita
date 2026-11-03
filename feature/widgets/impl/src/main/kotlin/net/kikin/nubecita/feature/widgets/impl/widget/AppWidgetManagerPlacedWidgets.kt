package net.kikin.nubecita.feature.widgets.impl.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.widgetsync.PlacedWidgets
import javax.inject.Inject

/**
 * Real [PlacedWidgets] (nubecita-kvh0): asks the framework whether either feed
 * widget's provider currently has instances on a home screen.
 *
 * Deliberately `AppWidgetManager` rather than Glance's `GlanceAppWidgetManager`
 * — `getAppWidgetIds` is a single binder call against the host's own records,
 * while the Glance equivalent goes through Glance's state machinery. The answer
 * gates every background refresh, so it has to be the cheap one.
 *
 * Both app flavors bind this (both ship `:feature:widgets:impl`), so the
 * `UngatedPlacedWidgets` fallback in `:core:widget-sync` is reached only by
 * tests.
 */
internal class AppWidgetManagerPlacedWidgets
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PlacedWidgets {
        override suspend fun anyPlaced(): Boolean =
            withContext(ioDispatcher) {
                // Null on a device with no AppWidget service at all, which is also a
                // device on which nothing can be placed.
                val manager = AppWidgetManager.getInstance(context) ?: return@withContext false
                RECEIVERS.any { receiver ->
                    manager.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty()
                }
            }

        private companion object {
            /** Every `<receiver>` this module declares as an `appwidget-provider`. */
            val RECEIVERS =
                listOf(
                    FollowingFeedWidgetReceiver::class.java,
                    DiscoverFeedWidgetReceiver::class.java,
                )
        }
    }
