package net.kikin.nubecita.feature.notifications.impl

import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile

/**
 * Derive the open post's URI from the Notifications tab segment of the MainShell
 * back stack — the topmost [PostDetailRoute]'s postUri, or `null` when none is open.
 * Feeds the list pane's selected-row highlight in the tablet list-detail layout.
 */
internal fun findSelectedPostUri(backStack: List<NavKey>): String? {
    val tabIndex = backStack.lastIndexOf(NotificationsTab)
    if (tabIndex < 0) return null
    val notifStack = backStack.drop(tabIndex)
    return (notifStack.lastOrNull { it is PostDetailRoute } as? PostDetailRoute)?.postUri
}

/**
 * Derive the open profile handle/did from the Notifications tab segment of the MainShell
 * back stack — the topmost [Profile]'s handle, or `null` when none is open.
 */
internal fun findSelectedProfileHandle(backStack: List<NavKey>): String? {
    val tabIndex = backStack.lastIndexOf(NotificationsTab)
    if (tabIndex < 0) return null
    val notifStack = backStack.drop(tabIndex)
    return (notifStack.lastOrNull { it is Profile } as? Profile)?.handle
}
