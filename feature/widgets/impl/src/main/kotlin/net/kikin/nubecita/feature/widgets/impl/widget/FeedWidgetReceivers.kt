package net.kikin.nubecita.feature.widgets.impl.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * `AppWidgetProvider` receivers the framework instantiates from the manifest
 * (must be public). Each just names its [GlanceAppWidget]; the manifest
 * `<receiver>` entries (in this module's manifest, merged into the production
 * `:app`) point the `APPWIDGET_UPDATE` action + `<appwidget-provider>` metadata
 * at these.
 */
class FollowingFeedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FollowingFeedWidget()
}

class DiscoverFeedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DiscoverFeedWidget()
}
