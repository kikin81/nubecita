package net.kikin.nubecita.feature.postdetail.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.feature.postdetail.impl.data.ThreadItem

/**
 * One frame's worth of UI state for the post-detail screen.
 *
 * `items` is a flat field per the MVI convention's "independent flags
 * stay flat" rule — once a thread loads, refresh lifecycles toggle
 * `loadStatus` while the items list stays put for the user to keep
 * reading. `loadStatus` is a sealed sum (per the "mutually-exclusive
 * view modes" carve-out in CLAUDE.md) so the type system makes invalid
 * combinations like `InitialLoading && Refreshing` unrepresentable.
 *
 * Each [ThreadItem] is one row in the flattened thread (root-most
 * ancestor → focus → replies depth-first); the screen's LazyColumn
 * keys on `ThreadItem.key`.
 */
@Immutable
internal data class PostDetailState(
    val items: ImmutableList<ThreadItem> = persistentListOf(),
    val loadStatus: PostDetailLoadStatus = PostDetailLoadStatus.Idle,
) : UiState

/**
 * Mutually-exclusive load lifecycle for the post-detail screen.
 * Mirrors `:feature:feed:impl`'s `FeedLoadStatus` shape but without
 * `Appending` — getPostThread returns the entire visible thread in
 * one response (the lexicon's `parentHeight` / `depth` parameters
 * cap the tree on the server side, no client-side pagination).
 */
internal sealed interface PostDetailLoadStatus {
    /** No load is in flight. */
    @Immutable
    data object Idle : PostDetailLoadStatus

    /** First load (the screen has no items yet). */
    @Immutable
    data object InitialLoading : PostDetailLoadStatus

    /** Pull-to-refresh in progress; existing items are still rendered. */
    @Immutable
    data object Refreshing : PostDetailLoadStatus

    /**
     * Initial load failed. Sticky — the screen renders a full-screen
     * retry layout against this state. Refresh failures preserve the
     * existing items and emit [PostDetailEffect.ShowError] instead of
     * flipping the load status.
     */
    @Immutable
    data class InitialError(
        val error: PostDetailError,
    ) : PostDetailLoadStatus
}

/**
 * UI-resolvable error categories surfaced by the post-detail screen.
 * The screen maps each variant to a `stringResource` call when
 * rendering — the VM stays Android-resource-free.
 */
internal sealed interface PostDetailError {
    /** Underlying network or transport failure. */
    @Immutable
    data object Network : PostDetailError

    /**
     * No authenticated session. The screen routes to login (same
     * recovery as the feed surface).
     */
    @Immutable
    data object Unauthenticated : PostDetailError

    /**
     * Post is gone — either the lexicon's declared `NotFound` XRPC
     * error (404 from the server), OR the response surfaced
     * `app.bsky.feed.defs#notFoundPost` at the focus position. The
     * screen renders a "Post not found" empty state.
     */
    @Immutable
    data object NotFound : PostDetailError

    /** Anything else (5xx, decode failure, unexpected throwable). */
    @Immutable
    data class Unknown(
        val cause: String?,
    ) : PostDetailError
}

internal sealed interface PostDetailEvent : UiEvent {
    /** First load on screen entry. Idempotent — repeated `Load` while loading is a no-op. */
    data object Load : PostDetailEvent

    /** Pull-to-refresh; re-fetches the thread for the same focus URI. */
    data object Refresh : PostDetailEvent

    /** Re-run the initial load after a [PostDetailLoadStatus.InitialError]. */
    data object Retry : PostDetailEvent

    /**
     * Tap on an ancestor, focus, or reply post body — pushes another
     * post-detail screen onto the stack with that post as the new
     * focus. Tapping the focused post re-enters the same screen
     * (harmless; the state holder picks up at the same scroll position).
     */
    data class OnPostTapped(
        val postUri: String,
    ) : PostDetailEvent

    data class OnAuthorTapped(
        val authorDid: String,
    ) : PostDetailEvent
}

internal sealed interface PostDetailEffect : UiEffect {
    /** Surface-able, non-sticky error (snackbar). */
    @Immutable
    data class ShowError(
        val error: PostDetailError,
    ) : PostDetailEffect

    /** Push another post-detail screen for the tapped post URI. */
    @Immutable
    data class NavigateToPost(
        val postUri: String,
    ) : PostDetailEffect

    /** Push the author's profile screen. */
    @Immutable
    data class NavigateToAuthor(
        val authorDid: String,
    ) : PostDetailEffect
}
