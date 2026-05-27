package net.kikin.nubecita.feature.notifications.impl

import dagger.hilt.android.lifecycle.HiltViewModel
import net.kikin.nubecita.core.common.mvi.MviViewModel
import javax.inject.Inject

/**
 * Skeleton — real VM lands in `nubecita-1fy.1.6`.
 *
 * The full implementation will own the paginated `listNotifications`
 * stream, filter-chip state, `RowTapped` deep-link routing, and the
 * `TabExited` mark-read handshake. This scaffold exists today so the
 * Hilt `EntryProviderInstaller` in `NotificationsNavigationModule` has
 * a class to point `hiltViewModel<NotificationsViewModel>()` at when
 * the real screen swaps in.
 */
@HiltViewModel
internal class NotificationsViewModel
    @Inject
    constructor() : MviViewModel<NotificationsState, NotificationsEvent, NotificationsEffect>(NotificationsState) {
        override fun handleEvent(event: NotificationsEvent) {
            // No events defined yet — real reducer lands in nubecita-1fy.1.6.
        }
    }
