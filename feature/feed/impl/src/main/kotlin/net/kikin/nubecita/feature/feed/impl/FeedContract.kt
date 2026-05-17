package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.feature.feed.impl.share.PostShareIntent

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
    /**
     * URI of the post whose like was most recently toggled by a user
     * tap in this session, or `null` if none. The screen reads this
     * to decide whether a particular PostCard's like-count change
     * should animate (user-initiated ±1) or snap (background sync).
     * Sticky — kept until the next user tap or a screen relaunch;
     * AnimatedCompactCount's internal rule handles "doesn't re-fire
     * when the count hasn't changed."
     */
    val lastLikeTapPostUri: String? = null,
    /** Mirror of [lastLikeTapPostUri] for the repost toggle. */
    val lastRepostTapPostUri: String? = null,
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

    /**
     * User tapped a specific image inside a PostCard's image embed.
     * Routes directly to the fullscreen MediaViewer with [imageIndex]
     * as the carousel's starting page — skipping the PostDetail
     * detour the outer-card tap takes.
     */
    data class OnImageTapped(
        val post: PostUi,
        val imageIndex: Int,
    ) : FeedEvent

    /**
     * User tapped the inner quoted-post region of a PostCard's record
     * embed. Routes to PostDetail for the quoted post — distinct from
     * [OnPostTapped] which routes to the outer (parent) post.
     */
    data class OnQuotedPostTapped(
        val quotedPostUri: String,
    ) : FeedEvent

    /**
     * User tapped a video embed (parent or quoted) in a PostCard.
     * Routes directly to the fullscreen video player route, skipping
     * the PostDetail detour the outer-card tap takes. [postUri] is the
     * AT URI of the post that owns the video — for a quoted video this
     * is the quoted post's URI, NOT the parent's, so the resolver looks
     * up the right embed.
     */
    data class OnVideoTapped(
        val postUri: String,
    ) : FeedEvent

    data class OnLikeClicked(
        val post: PostUi,
    ) : FeedEvent

    data class OnRepostClicked(
        val post: PostUi,
    ) : FeedEvent

    data class OnShareClicked(
        val post: PostUi,
    ) : FeedEvent

    /** Long-press on the share button — copy the post permalink to the clipboard. */
    data class OnShareLongPressed(
        val post: PostUi,
    ) : FeedEvent

    /**
     * The user just submitted a reply with parent post URI [parentUri]
     * — emitted from `LocalComposerSubmitEvents` after the composer
     * overlay closes successfully. The handler runs an optimistic
     * `replyCount + 1` on the parent post in [FeedState.feedItems] so
     * the user doesn't see a stale "0 comments" on the post they just
     * replied to. No-op when the parent isn't in the loaded slice
     * (replied from a non-feed surface, or pagination evicted the
     * parent before the submit landed).
     */
    data class OnReplySubmittedToParent(
        val parentUri: String,
    ) : FeedEvent
}

sealed interface FeedEffect : UiEffect {
    /** Surface-able, non-sticky error (snackbar). */
    @Immutable
    data class ShowError(
        val error: FeedError,
    ) : FeedEffect

    /**
     * Navigate to the post detail screen for the tapped post.
     *
     * Carries just the AT URI (not the full [PostUi]) — the detail
     * screen's VM re-fetches the canonical thread via getPostThread
     * keyed on the URI, so the feed-side projection of the post is
     * load-bearing here only for its identity.
     */
    @Immutable
    data class NavigateToPost(
        val postUri: String,
    ) : FeedEffect

    /** Navigate to the author's profile screen. */
    @Immutable
    data class NavigateToAuthor(
        val authorDid: String,
    ) : FeedEffect

    /**
     * Open the fullscreen MediaViewer directly for the tapped image,
     * skipping the PostDetail screen the outer-card tap routes to.
     * [imageIndex] is the zero-based start page for the carousel.
     */
    @Immutable
    data class NavigateToMediaViewer(
        val postUri: String,
        val imageIndex: Int,
    ) : FeedEffect

    /**
     * Open the fullscreen video player for the tapped video embed,
     * skipping the PostDetail screen the outer-card tap routes to.
     * Carries just the post AT URI — the video player VM resolves the
     * playlist URL via `app.bsky.feed.getPosts`. Same instance-transfer
     * contract as `nubecita-zak.1`: if the feed's `SharedVideoPlayer`
     * is already bound to this post's playlist, the fullscreen route
     * picks up the existing ExoPlayer with no restart.
     */
    @Immutable
    data class NavigateToVideoPlayer(
        val postUri: String,
    ) : FeedEffect

    /**
     * Hand a pre-computed share payload to the screen so it can fire
     * `Intent.ACTION_SEND` via the system share sheet. The VM produces
     * the payload (so unit tests can assert it) and the screen owns
     * the platform Intent dispatch.
     */
    @Immutable
    data class SharePost(
        val intent: PostShareIntent,
    ) : FeedEffect

    /**
     * Copy a post permalink to the system clipboard. Fired on long-press
     * of the share button (Threads-style). The screen handles the
     * platform `ClipboardManager` write and surfaces a confirmation
     * snackbar.
     */
    @Immutable
    data class CopyPermalink(
        val permalink: String,
    ) : FeedEffect
}
