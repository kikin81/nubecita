package net.kikin.nubecita.feature.notifications.impl

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.data.models.NotificationItemUi

/**
 * One frame's worth of UI state for the Notifications screen.
 *
 * `items`, `activeFilter`, `cursor`, and `hasMore` are flat fields per the
 * MVI convention's "independent flags stay flat" rule. `loadStatus` is a
 * sealed sum (per the amended convention's "mutually-exclusive view modes"
 * carve-out) so the type system makes invalid combinations like
 * `Refreshing && Appending` unrepresentable. Initial value is
 * [NotificationsLoadStatus.InitialLoading] because the VM fires its first
 * fetch from `init` — the screen sees a loading frame on the very first
 * recomposition without an intermediate Idle blink.
 */
@Immutable
internal data class NotificationsState(
    val items: ImmutableList<NotificationItemUi> = persistentListOf(),
    val activeFilter: NotificationFilter = NotificationFilter.All,
    val loadStatus: NotificationsLoadStatus = NotificationsLoadStatus.InitialLoading,
    val cursor: String? = null,
    val hasMore: Boolean = true,
) : UiState

/**
 * Mutually-exclusive load lifecycle for the notifications surface. At any
 * instant the VM is in exactly one of these states; the type system
 * prevents `Refreshing && Appending` combinations that boolean flags
 * would otherwise allow.
 */
internal sealed interface NotificationsLoadStatus {
    /** No load is in flight. */
    @Immutable
    data object Idle : NotificationsLoadStatus

    /** First load (the screen has no rows yet). */
    @Immutable
    data object InitialLoading : NotificationsLoadStatus

    /** Pull-to-refresh in progress; existing rows are still rendered. */
    @Immutable
    data object Refreshing : NotificationsLoadStatus

    /** Append-on-scroll in progress; existing rows are still rendered. */
    @Immutable
    data object Appending : NotificationsLoadStatus

    /**
     * Initial load failed. Sticky — the screen renders a full-screen
     * retry layout against this state. Refresh / append failures preserve
     * the existing rows and emit [NotificationsEffect.ShowError] instead
     * of flipping the load status.
     */
    @Immutable
    data class InitialError(
        val error: NotificationsError,
    ) : NotificationsLoadStatus
}

/**
 * UI-resolvable error categories surfaced by the notifications VM. The
 * screen maps each variant to a stringResource call when rendering — the
 * VM stays Android-resource-free.
 *
 * TODO(nubecita-1fy.1.8): When the screen lands, swap the screen's
 * `ShowError(message: String)` consumption for a `UiText`-like wrapper if
 * one ships before then. Today the project has no shared `UiText` type, so
 * the effect carries a plain String produced by `errorMessage(throwable)`.
 */
internal enum class NotificationsError {
    /** Underlying network or transport failure (IOException, timeouts). */
    Network,

    /**
     * No authenticated session — typically because the access token
     * couldn't be refreshed or the user signed out from another device.
     */
    Unauthenticated,

    /** Anything else (server 5xx, decode failure, unexpected throwable). */
    Unknown,
}

internal sealed interface NotificationsEvent : UiEvent {
    /** Pull-to-refresh; resets the cursor and re-fetches the head of the list. */
    data object Refresh : NotificationsEvent

    /** Append-on-scroll; fetches the next page using the current cursor. */
    data object LoadMore : NotificationsEvent

    /**
     * User selected a filter chip. Identity-of-filter no-op'd in the
     * reducer; switching filters resets cursor + items and re-fetches.
     */
    data class FilterSelected(
        val filter: NotificationFilter,
    ) : NotificationsEvent

    /**
     * User tapped a notification row. The VM resolves the deep-link
     * target by reason and emits [NotificationsEffect.NavigateTo].
     */
    data class RowTapped(
        val item: NotificationItemUi,
    ) : NotificationsEvent

    /**
     * User tapped the avatar stack (chevron disclosure) on an aggregated
     * row. The VM emits [NotificationsEffect.ShowActorList] so the screen
     * can present the bottom-sheet actor list.
     */
    data class AvatarStackTapped(
        val item: NotificationItemUi.Aggregated,
    ) : NotificationsEvent

    /**
     * The user navigated away from the Notifications tab (top-level key
     * transitioned away from `NotificationsTab`) or the screen left
     * composition. Fire-and-forget `updateSeen(now)` — failures are
     * swallowed because the next 60s `getUnreadCount` poll corrects the
     * count.
     */
    data object TabExited : NotificationsEvent
}

internal sealed interface NotificationsEffect : UiEffect {
    /**
     * Surface-able, non-sticky error (snackbar). Carries a plain String
     * today; the screen owns the user-facing copy via `stringResource`.
     *
     * TODO(nubecita-1fy.1.8): When the screen lands, swap this for a
     * `UiText`-like wrapper if the project ships one. Today the VM hands
     * the screen a pre-rendered English message produced by
     * `errorMessage(throwable)` because no shared `UiText` exists.
     */
    @Immutable
    data class ShowError(
        val message: String,
    ) : NotificationsEffect

    /**
     * Push a sub-route NavKey onto `MainShell`'s inner back stack via
     * `LocalMainShellNavState`. The screen collector resolves the
     * `CompositionLocal` (which the ViewModel can't see) and calls
     * `add(key)`. Matches the cross-tab navigation pattern established
     * by `:feature:feed:impl`'s `FeedEffect.NavigateTo`.
     */
    @Immutable
    data class NavigateTo(
        val target: NavKey,
    ) : NotificationsEffect

    /**
     * Present the multi-actor list for an aggregated row (Bluesky-style
     * bottom-sheet disclosure). The screen owns the sheet itself; the VM
     * just hands over the actors to render.
     */
    @Immutable
    data class ShowActorList(
        val actors: ImmutableList<AuthorUi>,
    ) : NotificationsEffect
}
