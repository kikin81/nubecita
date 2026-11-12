package net.kikin.nubecita.wear.widget

import net.kikin.nubecita.wear.complication.unreadDmsLabel

/**
 * What the launcher widget's Messages card shows. [countLabel] is null before
 * DMs have ever synced (or after sign-out): the card then reads just "Messages".
 */
data class LauncherWidgetModel(
    val countLabel: String?,
    val hasUnread: Boolean,
)

/** The Messages card for [unread] (the synced total, null before DMs ever sync); "99+" above 99, like the complication. */
fun launcherWidgetModel(unread: Int?): LauncherWidgetModel =
    if (unread == null) {
        LauncherWidgetModel(countLabel = null, hasUnread = false)
    } else {
        LauncherWidgetModel(countLabel = unreadDmsLabel(unread), hasUnread = unread > 0)
    }
