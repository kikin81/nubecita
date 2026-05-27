package net.kikin.nubecita.feature.notifications.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.data.models.NotificationItemUi

/**
 * Screen-private projection of [NotificationsState] into the four
 * mutually-exclusive render branches that the host composable's `when`
 * switches over.
 *
 * Lives inside `:feature:notifications:impl` and never crosses the VM/UI
 * boundary — the VM emits `NotificationsState`, the screen converts to
 * `NotificationsScreenViewState`, the rendering composable pattern-matches
 * on the result. This keeps the VM contract free of UI rendering concerns
 * and makes each render branch unit-testable independently.
 *
 * Total over [NotificationsState]: see [toViewState] for the dispatch matrix.
 */
@Immutable
internal sealed interface NotificationsScreenViewState {
    /** Initial load with no items yet — render shimmer rows. */
    @Immutable data object InitialLoading : NotificationsScreenViewState

    /** Idle with no items — render `NotificationsEmptyState`. */
    @Immutable data object Empty : NotificationsScreenViewState

    /** Initial load failed and no items to fall back on — render full-screen retry. */
    @Immutable
    data class InitialError(
        val error: NotificationsError,
    ) : NotificationsScreenViewState

    /**
     * Notification rows are present. Each [NotificationItemUi] is one
     * logical row — single-event or aggregated multi-actor.
     * [isAppending] toggles the tail shimmer; [isRefreshing] drives
     * `PullToRefreshBox`'s indicator. The two are mutually exclusive in
     * practice — [NotificationsLoadStatus] makes `Refreshing` and
     * `Appending` unrepresentable simultaneously, so a
     * `Loaded(isAppending = true, isRefreshing = true)` value never
     * reaches the screen.
     */
    @Immutable
    data class Loaded(
        val items: ImmutableList<NotificationItemUi>,
        val activeFilter: NotificationFilter,
        val isAppending: Boolean,
        val isRefreshing: Boolean,
    ) : NotificationsScreenViewState
}

/**
 * Project [NotificationsState] to its render branch. Total over
 * `(loadStatus, items)`.
 *
 * Dispatch is `items.isEmpty()` first, `loadStatus` second:
 *
 * | `items`     | `loadStatus`        | Result                                                 |
 * |-------------|---------------------|--------------------------------------------------------|
 * | empty       | `InitialLoading`    | [NotificationsScreenViewState.InitialLoading]          |
 * | empty       | `Refreshing`        | [NotificationsScreenViewState.InitialLoading]          |
 * | empty       | `InitialError`      | [NotificationsScreenViewState.InitialError]            |
 * | empty       | `Idle`              | [NotificationsScreenViewState.Empty]                   |
 * | empty       | `Appending`         | [NotificationsScreenViewState.InitialLoading] (VM-impossible; safe fallback) |
 * | non-empty   | `Appending`         | [NotificationsScreenViewState.Loaded] (`isAppending = true`)   |
 * | non-empty   | `Refreshing`        | [NotificationsScreenViewState.Loaded] (`isRefreshing = true`)  |
 * | non-empty   | any other           | [NotificationsScreenViewState.Loaded] (both flags false)       |
 *
 * The `empty` + `Refreshing` row is reachable in practice — the VM permits
 * a retry from `InitialError` (and from `Idle` when the first page came
 * back empty), and that retry transitions to `Refreshing` while items are
 * still empty. Mapping it to [InitialLoading] keeps the user looking at a
 * shimmer instead of an "all caught up" empty screen for the duration of
 * the retry; the alternative (mapping to [Empty]) would flash the empty
 * affordance for a few hundred ms before items arrive. The `empty` +
 * `Appending` row is still VM-impossible (Appending requires Idle, which
 * requires items) but routes to the same loading branch as a safe
 * fallback if a future contract change makes it reachable.
 */
internal fun NotificationsState.toViewState(): NotificationsScreenViewState =
    if (items.isEmpty()) {
        when (loadStatus) {
            NotificationsLoadStatus.InitialLoading,
            NotificationsLoadStatus.Refreshing,
            NotificationsLoadStatus.Appending,
            -> NotificationsScreenViewState.InitialLoading
            is NotificationsLoadStatus.InitialError ->
                NotificationsScreenViewState.InitialError(loadStatus.error)
            NotificationsLoadStatus.Idle -> NotificationsScreenViewState.Empty
        }
    } else {
        NotificationsScreenViewState.Loaded(
            items = items,
            activeFilter = activeFilter,
            isAppending = loadStatus == NotificationsLoadStatus.Appending,
            isRefreshing = loadStatus == NotificationsLoadStatus.Refreshing,
        )
    }
