package net.kikin.nubecita.wear.widget

import androidx.glance.wear.AssociateWithGlanceWearWidget
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.GlanceWearWidgetService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import net.kikin.nubecita.wear.data.PhoneSession
import net.kikin.nubecita.wear.data.PhoneSessionSource
import net.kikin.nubecita.wear.data.WatchDmRepository
import javax.inject.Inject

/**
 * Serves [LauncherWidget] over both the widget and tile protocols (see the
 * manifest), so partial-height Wear OS 7 watches show the widget card and every
 * other watch shows it inside a tile. The session and unread count are read on
 * each render; [LauncherWidgetUpdater] asks for a render when they change.
 */
@AndroidEntryPoint
@AssociateWithGlanceWearWidget(LauncherWidget::class)
class LauncherWidgetService : GlanceWearWidgetService() {
    @Inject
    lateinit var repository: WatchDmRepository

    @Inject
    lateinit var session: PhoneSessionSource

    // Read at render time, after injection: the service is constructed before onCreate.
    // The stored session answers offline; a missing one (no phone app yet) reads as signed out.
    override val widget: GlanceWearWidget =
        LauncherWidget(
            signedIn = { session.session.first() is PhoneSession.SignedIn },
            unreadCount = { repository.latestUnreadCount() },
        )
}
