package net.kikin.nubecita.feature.notifications.impl

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.data.models.NotificationReason
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsPage
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsRepository
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Clock

/**
 * ViewModel for the Notifications screen.
 *
 * Owns the paginated `listNotifications` stream filtered by
 * [NotificationFilter], deep-link routing per `reason`, the aggregated
 * actor-list disclosure event, and the mark-read-on-tab-exit handshake.
 *
 * Implements the project's MVI conventions: a flat [NotificationsState]
 * with a sealed [NotificationsLoadStatus] lifecycle, errors routed
 * non-stickily via [NotificationsEffect.ShowError], and cross-feature
 * navigation emitted as [NotificationsEffect.NavigateTo]. The base class
 * is `MviViewModel<S, E, F>` — no `Async<T>` wrapper, no `launchSafe`
 * helper. Single-flight gates against concurrent fetches live inline in
 * the reducers (the `loadStatus == X` checks below).
 *
 * The first fetch kicks off from `init` so the screen renders an
 * initial-loading frame on first composition without an intermediate Idle
 * blink. Process-death / re-entry semantics: the VM is per-screen-instance
 * (Hilt-scoped to the destination), so a configuration change preserves
 * `uiState` but a back-stack pop+repush produces a fresh VM whose `init`
 * re-fetches. Matches `FeedViewModel`'s shape.
 */
@HiltViewModel
internal class NotificationsViewModel
    @Inject
    constructor(
        private val repository: NotificationsRepository,
    ) : MviViewModel<NotificationsState, NotificationsEvent, NotificationsEffect>(NotificationsState()) {
        init {
            // First page on construction. Mirrors FeedViewModel: emits an
            // initial-loading frame immediately and the page result lands
            // through the same code path as a manual Refresh, but with the
            // empty-items branch of failure recovery (InitialError instead
            // of ShowError).
            fetchInitial(filter = NotificationFilter.All)
        }

        override fun handleEvent(event: NotificationsEvent) {
            when (event) {
                NotificationsEvent.Refresh -> refresh()
                NotificationsEvent.LoadMore -> loadMore()
                is NotificationsEvent.FilterSelected -> onFilterSelected(event.filter)
                is NotificationsEvent.RowTapped -> onRowTapped(event.item)
                is NotificationsEvent.AvatarStackTapped ->
                    sendEffect(NotificationsEffect.ShowActorList(event.item.actors))
                NotificationsEvent.TabExited -> onTabExited()
            }
        }

        private fun refresh() {
            // Mutually exclusive with every other in-flight fetch — including
            // `InitialLoading`. The original guard only dropped Refresh during
            // `Refreshing` / `Appending`, but allowing Refresh on top of
            // `InitialLoading` lets two head-fetches race on `items` / `cursor`
            // with last-writer-wins semantics (the older response can land
            // after the newer and overwrite). FeedViewModel's invariant — only
            // one head fetch in flight — is what we mirror.
            val status = uiState.value.loadStatus
            if (status == NotificationsLoadStatus.InitialLoading ||
                status == NotificationsLoadStatus.Refreshing ||
                status == NotificationsLoadStatus.Appending
            ) {
                return
            }
            // Refresh while in `InitialError` / `Idle` is allowed — it's the
            // same "fetch head of the list" intent. Transition to
            // `Refreshing` so the screen swaps the initial shimmer (or the
            // full-screen retry) for the pull-to-refresh indicator on
            // subsequent fetches.
            val filter = uiState.value.activeFilter
            setState { copy(loadStatus = NotificationsLoadStatus.Refreshing) }
            viewModelScope.launch {
                repository
                    .fetchPage(filter = filter, cursor = null)
                    .onSuccess { page -> applyRefreshSuccess(page, requestFilter = filter) }
                    .onFailure { throwable -> applyRefreshFailure(throwable, requestFilter = filter) }
            }
        }

        private fun loadMore() {
            val current = uiState.value
            // No more pages? Idempotent — repeat LoadMore once hasMore
            // flipped to false is a no-op.
            if (!current.hasMore) return
            // Only allow append from Idle. Refresh / InitialLoading /
            // Appending all drop the event so two getNotifications calls
            // don't race on items + cursor with last-writer-wins.
            if (current.loadStatus != NotificationsLoadStatus.Idle) return
            val filter = current.activeFilter
            val cursor = current.cursor
            setState { copy(loadStatus = NotificationsLoadStatus.Appending) }
            viewModelScope.launch {
                repository
                    .fetchPage(filter = filter, cursor = cursor)
                    .onSuccess { page -> applyAppendSuccess(page, requestFilter = filter) }
                    .onFailure { throwable -> applyAppendFailure(throwable, requestFilter = filter) }
            }
        }

        private fun onFilterSelected(filter: NotificationFilter) {
            val current = uiState.value
            // No-op when the user retaps the active chip. Without this guard
            // a re-tap would wipe the loaded slice and refetch, which is
            // user-hostile and burns rate-limit budget on a no-intent gesture.
            if (filter == current.activeFilter) return
            // Switching filters maps to a different `reasons[]` array on
            // the lexicon and a different result set — reset cursor / items
            // and re-enter the InitialLoading branch so the screen renders
            // a shimmer instead of stale rows under a new filter.
            //
            // Any fetch from the previous filter that's still in flight gets
            // invalidated at completion time via the `requestFilter` check
            // in the `apply*` helpers — a stale response under the old
            // filter never overwrites the new filter's state. We don't
            // try to cancel the in-flight HTTP request itself; the
            // wasted-bytes cost is dominated by the user-perceived latency
            // win of starting the new fetch immediately.
            setState {
                copy(
                    activeFilter = filter,
                    items = emptyItems(),
                    cursor = null,
                    hasMore = true,
                    loadStatus = NotificationsLoadStatus.InitialLoading,
                )
            }
            fetchInitial(filter = filter)
        }

        private fun fetchInitial(filter: NotificationFilter) {
            viewModelScope.launch {
                repository
                    .fetchPage(filter = filter, cursor = null)
                    .onSuccess { page -> applyInitialSuccess(page, requestFilter = filter) }
                    .onFailure { throwable -> applyInitialFailure(throwable, requestFilter = filter) }
            }
        }

        /**
         * Drop a completion whose [requestFilter] no longer matches the
         * VM's [activeFilter]. Returns `true` when the completion should
         * be applied. The single-flight head gate in [refresh] +
         * [loadMore] guarantees only one same-filter fetch is in flight;
         * this guard handles the cross-filter race that [onFilterSelected]
         * intentionally leaves open (it doesn't wait for the old fetch).
         */
        private fun isCurrent(requestFilter: NotificationFilter): Boolean {
            val current = uiState.value.activeFilter
            if (current == requestFilter) return true
            Timber.tag(TAG).d(
                "Dropping stale completion for filter=%s (current=%s)",
                requestFilter,
                current,
            )
            return false
        }

        private fun applyInitialSuccess(
            page: NotificationsPage,
            requestFilter: NotificationFilter,
        ) {
            if (!isCurrent(requestFilter)) return
            setState {
                copy(
                    items = page.items,
                    cursor = page.nextCursor,
                    hasMore = page.nextCursor != null,
                    loadStatus = NotificationsLoadStatus.Idle,
                )
            }
        }

        private fun applyInitialFailure(
            throwable: Throwable,
            requestFilter: NotificationFilter,
        ) {
            if (!isCurrent(requestFilter)) return
            // Initial load with no fallback rows → sticky InitialError so
            // the screen renders a full-screen retry layout. If items are
            // somehow non-empty at this point (would imply a race between
            // a filter switch and an in-flight fetch we didn't gate),
            // degrade gracefully to a snackbar.
            if (uiState.value.items.isEmpty()) {
                setState { copy(loadStatus = NotificationsLoadStatus.InitialError(throwable.toError())) }
            } else {
                setState { copy(loadStatus = NotificationsLoadStatus.Idle) }
                sendEffect(NotificationsEffect.ShowError(throwable.errorMessage()))
            }
        }

        private fun applyRefreshSuccess(
            page: NotificationsPage,
            requestFilter: NotificationFilter,
        ) {
            if (!isCurrent(requestFilter)) return
            setState {
                copy(
                    items = page.items,
                    cursor = page.nextCursor,
                    hasMore = page.nextCursor != null,
                    loadStatus = NotificationsLoadStatus.Idle,
                )
            }
        }

        private fun applyRefreshFailure(
            throwable: Throwable,
            requestFilter: NotificationFilter,
        ) {
            if (!isCurrent(requestFilter)) return
            // Refresh failure: preserve items if we have them (snackbar);
            // otherwise this is effectively still an initial load and we
            // promote to InitialError so the screen can render the retry
            // layout. Mirrors FeedViewModel's refresh failure handling.
            if (uiState.value.items.isEmpty()) {
                setState { copy(loadStatus = NotificationsLoadStatus.InitialError(throwable.toError())) }
            } else {
                setState { copy(loadStatus = NotificationsLoadStatus.Idle) }
                sendEffect(NotificationsEffect.ShowError(throwable.errorMessage()))
            }
        }

        private fun applyAppendSuccess(
            page: NotificationsPage,
            requestFilter: NotificationFilter,
        ) {
            if (!isCurrent(requestFilter)) return
            // Append: concatenate the new page after the existing items.
            // Slice 1 does no cross-page aggregation merging (see design
            // D3), so two same-reason groups straddling a page boundary
            // render as adjacent multi-actor rows. Acceptable per the
            // design's listed trade-offs.
            val merged = (uiState.value.items + page.items).toImmutableList()
            setState {
                copy(
                    items = merged,
                    cursor = page.nextCursor,
                    hasMore = page.nextCursor != null,
                    loadStatus = NotificationsLoadStatus.Idle,
                )
            }
        }

        private fun applyAppendFailure(
            throwable: Throwable,
            requestFilter: NotificationFilter,
        ) {
            if (!isCurrent(requestFilter)) return
            // Preserve items AND cursor on append failure so the user can
            // retry from the same page boundary. Surface as snackbar; the
            // load status returns to Idle to re-arm the gate for another
            // LoadMore.
            setState { copy(loadStatus = NotificationsLoadStatus.Idle) }
            sendEffect(NotificationsEffect.ShowError(throwable.errorMessage()))
        }

        private fun onRowTapped(item: NotificationItemUi) {
            val target = item.deepLinkTarget()
            if (target != null) {
                sendEffect(NotificationsEffect.NavigateTo(target))
            } else {
                // Forward-compat fallback: Unknown reason has no defined
                // deep-link. Log to Timber for diagnostics so a new reason
                // value at the AppView is observable in `adb logcat`
                // without crashing the user. Per design D8.
                Timber.tag(TAG).d("RowTapped with no deep-link target: reason=%s", item.reason)
            }
        }

        private fun onTabExited() {
            // Fire-and-forget. Failures are intentionally NOT surfaced as
            // an effect — the user is exiting the tab and a snackbar on
            // the next tab would be confusing. The next 60-second
            // `getUnreadCount` poll (lands under `nubecita-1fy.1.7`)
            // corrects the count if `updateSeen` failed.
            //
            // TODO(nubecita-1fy.1.9): coordinate with
            // `NotificationsUnreadCountStore` to optimistically zero the
            // badge so the bottom-nav `BadgedBox` clears immediately
            // instead of waiting for the next poll. The store lives in
            // `nubecita-1fy.1.7` and the coordination wires through the
            // MainShell in `nubecita-1fy.1.9`.
            viewModelScope.launch {
                repository
                    .markSeen(Clock.System.now())
                    .onFailure { throwable ->
                        Timber.tag(TAG).w(throwable, "markSeen failed; next poll will correct the count")
                    }
            }
        }

        /**
         * Resolve a tap on this row to its target [NavKey] per design D8.
         * Returns null when the reason has no defined deep-link
         * (currently [NotificationReason.Unknown] — the forward-compat
         * fallback for reason values the AppView grows beyond this
         * client's known set).
         */
        private fun NotificationItemUi.deepLinkTarget(): NavKey? =
            when (reason) {
                // Engagement reasons — the row is *about* the user's own
                // post that someone interacted with. `subjectPost` is the
                // hydrated form; `subjectPost.id` is the AT URI from
                // `reasonSubject` (engagement) or `uri` (content-bearing).
                NotificationReason.Like,
                NotificationReason.LikeViaRepost,
                NotificationReason.Repost,
                NotificationReason.RepostViaRepost,
                NotificationReason.Reply,
                NotificationReason.Quote,
                NotificationReason.Mention,
                NotificationReason.SubscribedPost,
                -> subjectPost?.id?.let(::PostDetailRoute)

                // Actor-shaped reasons — open the actor's profile. For
                // aggregated follow rows, the newest follower is the head
                // of the actor list (the mapper sorts by `indexedAt`
                // descending), so `actors.first()` is the most recent
                // contributor.
                NotificationReason.Follow,
                NotificationReason.ContactMatch,
                NotificationReason.StarterpackJoined,
                -> Profile(handle = actors.first().did)

                // Self-shaped reasons — verified / unverified rows are
                // about the current authenticated user's own profile.
                // `Profile(handle = null)` is the canonical "self" key in
                // the shell's nav contract.
                NotificationReason.Verified,
                NotificationReason.Unverified,
                -> Profile(handle = null)

                // Forward-compat fallback: a new reason landed at the
                // AppView that this client doesn't know about. No
                // deep-link to emit; the row remains tappable but the
                // VM logs and drops the event.
                NotificationReason.Unknown -> null
            }

        private fun Throwable.toError(): NotificationsError =
            when (this) {
                is NoSessionException -> NotificationsError.Unauthenticated
                is IOException -> NotificationsError.Network
                else -> NotificationsError.Unknown
            }

        /**
         * Pre-rendered English error message for the snackbar effect. The
         * project has no shared `UiText` wrapper today, so the VM hands
         * the screen the literal text it should display. When `UiText`
         * lands (tracked under `nubecita-1fy.1.8`'s screen task), swap
         * this for the wrapper without touching the reducer logic.
         */
        private fun Throwable.errorMessage(): String =
            when (this) {
                is NoSessionException -> "Your session expired. Please sign in again."
                is IOException -> "Network unavailable. Please try again."
                else -> "Something went wrong. Please try again."
            }

        private companion object {
            const val TAG = "NotificationsVM"
        }
    }

/**
 * Empty `ImmutableList<NotificationItemUi>` placeholder. Extracted so
 * the `copy(items = ...)` call in the filter-switch reducer doesn't have
 * to spell out the generic type, and so a future change to use a
 * `persistentListOf<NotificationItemUi>()` literal (vs. `emptyList`-ish)
 * only touches one site.
 */
private fun emptyItems(): kotlinx.collections.immutable.ImmutableList<NotificationItemUi> = kotlinx.collections.immutable.persistentListOf()
