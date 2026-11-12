package net.kikin.nubecita.wear.widget

import androidx.glance.wear.AssociateWithGlanceWearWidget
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.GlanceWearWidgetService

/**
 * Serves [LauncherWidget] over both the widget and tile protocols (see the
 * manifest), so partial-height Wear OS 7 watches show the widget card and every
 * other watch shows it as a full-screen tile.
 */
@AssociateWithGlanceWearWidget(LauncherWidget::class)
class LauncherWidgetService : GlanceWearWidgetService() {
    override val widget: GlanceWearWidget = LauncherWidget(unreadCount = { null })
}
