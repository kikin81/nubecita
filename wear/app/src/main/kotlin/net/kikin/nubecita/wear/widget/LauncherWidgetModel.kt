package net.kikin.nubecita.wear.widget

import net.kikin.nubecita.wear.complication.unreadDmsLabel

/**
 * What the launcher widget shows. Signed out ([signedIn] false), it prompts to
 * sign in on the phone instead of its tiles (Wear quality WO-V9). Signed in,
 * [countLabel] is null before DMs have ever synced: the card then reads just
 * "Messages".
 */
data class LauncherWidgetModel(
    val countLabel: String?,
    val hasUnread: Boolean,
    val signedIn: Boolean,
)

/**
 * The widget for the phone's session and [unread] (the synced total, null before
 * DMs ever sync); "99+" above 99, like the complication. Anything stored is
 * ignored while signed out.
 */
fun launcherWidgetModel(
    signedIn: Boolean,
    unread: Int?,
): LauncherWidgetModel =
    when {
        !signedIn -> LauncherWidgetModel(countLabel = null, hasUnread = false, signedIn = false)
        unread == null -> LauncherWidgetModel(countLabel = null, hasUnread = false, signedIn = true)
        else -> LauncherWidgetModel(countLabel = unreadDmsLabel(unread), hasUnread = unread > 0, signedIn = true)
    }
