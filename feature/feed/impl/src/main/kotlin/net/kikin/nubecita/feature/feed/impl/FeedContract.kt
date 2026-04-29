package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi

/**
 * One frame's worth of UI state for the Following timeline screen.
 *
 * `feedItems`, `nextCursor`, and `endReached` are flat fields per the MVI
 * convention's "independent flags stay flat" rule. `loadStatus` is a
 * sealed sum (per the amended convention's "mutually-exclusive view
 * modes" carve-out) so the type system makes invalid combinations like
 * `isInitialLoading=true && isRefreshing=true` unrepresentable.
 *
 * Each [FeedItemUi] is one logical feed entry — either a
 * [FeedItemUi.Single] standalone post or a [FeedItemUi.ReplyCluster]
 * carrying root + parent + leaf for cross-author reply rendering.
 * Pagination + scroll plumbing keys on the leaf's URI in either case.
 */
@Immutable
data class FeedState(
    val feedItems: ImmutableList<FeedItemUi> = persistentListOf(),
    val nextCursor: String? = null,
    val endReached: Boolean = false,
    val loadStatus: FeedLoadStatus = FeedLoadStatus.Idle,
) : UiState

/**
 * Mutually-exclusive load lifecycle for the feed. At any instant the VM
 * is in exactly one of these states; the type system prevents
 * `Refreshing && Appending` combinations that boolean flags would
 * otherwise allow.
 */
sealed interface FeedLoadStatus {
    /** No load is in flight. */
    @Immutable
    data object Idle : FeedLoadStatus

    /** First load (the screen has no posts yet). */
    @Immutable
    data object InitialLoading : FeedLoadStatus

    /** Pull-to-refresh in progress; existing posts are still rendered. */
    @Immutable
    data object Refreshing : FeedLoadStatus

    /** Append-on-scroll in progress; existing posts are still rendered. */
    @Immutable
    data object Appending : FeedLoadStatus

    /**
     * Initial load failed. Sticky — the screen renders a full-screen
     * retry layout against this state. Refresh / append failures
     * preserve the existing posts and emit [FeedEffect.ShowError]
     * instead of flipping the load status.
     */
    @Immutable
    data class InitialError(
        val error: FeedError,
    ) : FeedLoadStatus
}

/**
 * UI-resolvable error categories surfaced by the feed. The screen maps
 * each variant to a stringResource call when rendering — the VM stays
 * Android-resource-free.
 */
sealed interface FeedError {
    /** Underlying network or transport failure. */
    @Immutable
    data object Network : FeedError

    /**
     * No authenticated session — typically because the access token
     * couldn't be refreshed or the user signed out from another device.
     * The screen should route to login.
     */
    @Immutable
    data object Unauthenticated : FeedError

    /** Anything else (server 5xx, decode failure, unexpected throwable). */
    @Immutable
    data class Unknown(
        val cause: String?,
    ) : FeedError
}

sealed interface FeedEvent : UiEvent {
    /** First load on screen entry. Idempotent — repeated `Load` while loading is a no-op. */
    data object Load : FeedEvent

    /** Pull-to-refresh; resets the cursor and re-fetches the head of the timeline. */
    data object Refresh : FeedEvent

    /** Append-on-scroll; fetches the next page using the current cursor. */
    data object LoadMore : FeedEvent

    /** Re-run the initial load after an [FeedLoadStatus.InitialError]. */
    data object Retry : FeedEvent

    /**
     * Dismiss any current error indicator. No-op today (errors flow
     * through effects, not sticky state) — kept on the surface for the
     * screen task to wire if a future sticky error indicator is needed.
     */
    data object ClearError : FeedEvent

    data class OnPostTapped(
        val post: PostUi,
    ) : FeedEvent

    data class OnAuthorTapped(
        val authorDid: String,
    ) : FeedEvent

    data class OnLikeClicked(
        val post: PostUi,
    ) : FeedEvent

    data class OnRepostClicked(
        val post: PostUi,
    ) : FeedEvent

    data class OnReplyClicked(
        val post: PostUi,
    ) : FeedEvent

    data class OnShareClicked(
        val post: PostUi,
    ) : FeedEvent
}

sealed interface FeedEffect : UiEffect {
    /** Surface-able, non-sticky error (snackbar). */
    @Immutable
    data class ShowError(
        val error: FeedError,
    ) : FeedEffect

    /** Navigate to the post detail screen. */
    @Immutable
    data class NavigateToPost(
        val post: PostUi,
    ) : FeedEffect

    /** Navigate to the author's profile screen. */
    @Immutable
    data class NavigateToAuthor(
        val authorDid: String,
    ) : FeedEffect
}
