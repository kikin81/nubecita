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
 * | empty       | `InitialError`      | [NotificationsScreenViewState.InitialError]            |
 * | empty       | `Idle`              | [NotificationsScreenViewState.Empty]                   |
 * | empty       | `Refreshing`/`Appending` | [NotificationsScreenViewState.Empty] (VM-impossible; safe fallback) |
 * | non-empty   | `Appending`         | [NotificationsScreenViewState.Loaded] (`isAppending = true`)   |
 * | non-empty   | `Refreshing`        | [NotificationsScreenViewState.Loaded] (`isRefreshing = true`)  |
 * | non-empty   | any other           | [NotificationsScreenViewState.Loaded] (both flags false)       |
 *
 * The `Refreshing` / `Appending` cases with `items.isEmpty()` and the
 * `InitialLoading` / `InitialError` cases with `items.isNotEmpty()` are
 * never produced by the VM (see `NotificationsViewModel`'s reducers — the
 * `Loaded` branches only run when items are present and `InitialError` is
 * a sticky empty-state). They're handled here for total coverage so a
 * future contract change can't introduce a silent unhandled-state crash.
 */
internal fun NotificationsState.toViewState(): NotificationsScreenViewState =
    if (items.isEmpty()) {
        when (loadStatus) {
            NotificationsLoadStatus.InitialLoading -> NotificationsScreenViewState.InitialLoading
            is NotificationsLoadStatus.InitialError ->
                NotificationsScreenViewState.InitialError(loadStatus.error)
            NotificationsLoadStatus.Idle,
            NotificationsLoadStatus.Refreshing,
            NotificationsLoadStatus.Appending,
            -> NotificationsScreenViewState.Empty
        }
    } else {
        NotificationsScreenViewState.Loaded(
            items = items,
            activeFilter = activeFilter,
            isAppending = loadStatus == NotificationsLoadStatus.Appending,
            isRefreshing = loadStatus == NotificationsLoadStatus.Refreshing,
        )
    }
