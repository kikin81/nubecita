package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.OutgoingPostUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction

/**
 * One frame's worth of UI state for the Following timeline screen.
 *
 * The feed items are **not** here. They arrive as `PagingData<FeedItemUi>`
 * from `FeedViewModel.feed`, because a durable cache and an immutable
 * `UiState` list cannot both own the list: the cache windows what is loaded,
 * and copying that window into state would reintroduce the unbounded
 * in-memory list the cache exists to replace (D1). What is left here is state
 * Paging has no opinion about.
 *
 * The load lifecycle is not here either — it is derived from the items'
 * `CombinedLoadStates` by [toFeedLoadStatus], since Paging owns when a load is
 * in flight. [FeedLoadStatus] survives as the vocabulary that mapping produces.
 */
@Immutable
data class FeedState(
    /**
     * Whether an inline video may start on its own right now — the resolved
     * answer from `AutoplayPolicy`, folding the stored preference together with
     * whether the connection is metered, not the raw preference.
     *
     * Defaults to `true`, matching the policy's own default and the app's
     * behaviour before the setting existed, so the frame before the first
     * emission does not flash a non-autoplay feed at everyone.
     *
     * `false` means **on demand**, not unavailable: the card renders its poster
     * with a play badge and a tap opens the full-screen player.
     */
    val videoAutoplayEnabled: Boolean = true,
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
    /**
     * Top-level posts the user just submitted, shown above [feedItems] on the
     * Following timeline until a page includes them. Always empty on other feeds.
     */
    val outgoingPosts: ImmutableList<OutgoingPostUi> = persistentListOf(),
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

    /**
     * The feed generator backing this feed is unreachable — the appview
     * answered `UpstreamFailure` or `UpstreamTimeout`. Transient and not
     * our fault: the viewer should retry later or tell the feed's owner.
     *
     * [serverMessage] is the upstream's own `message` field, rendered
     * verbatim under the body copy (the way bsky.app does) so a viewer
     * reporting the outage can quote what the server actually said. It is
     * server-supplied English and deliberately NOT translated; null when
     * the upstream sent no message.
     */
    @Immutable
    data class FeedOffline(
        val serverMessage: String?,
    ) : FeedError

    /**
     * The feed generator record does not exist — the appview answered
     * `UnknownFeed`. Permanent, unlike [FeedOffline]: retrying will not
     * bring back a deleted generator, so the copy points at removing the
     * feed instead of waiting it out.
     */
    @Immutable
    data object FeedNotFound : FeedError

    /** Anything else (server 5xx, decode failure, unexpected throwable). */
    @Immutable
    data class Unknown(
        val cause: String?,
    ) : FeedError
}

sealed interface FeedEvent : UiEvent {
    /**
     * One-shot bind of the feed this VM renders. Carries the feed's AT
     * URI and its [FeedKind] so the VM's load / refresh / append paths
     * dispatch on kind: `Following → getTimeline`,
     * `Generator → getFeed(feedUri, …)`, `List → getListFeed(feedUri, …)`.
     *
     * Emitted once from the host (a `LaunchedEffect(feedUri)` in the
     * `FeedHost` task) before the first [Load]: the host binds a
     * per-`feedUri`-keyed VM once. Re-binding to a different
     * `(feedUri, kind)` cancels any in-flight load from the prior binding
     * and resets the loaded slice so the new feed loads fresh — a stale
     * page from the old feed can never land in the rebound pane.
     * Re-binding to the same pair is a no-op. The Following pane keeps
     * working without an explicit bind — the VM defaults to
     * [FeedKind.Following] (its `feedUri` is unused for that kind).
     */
    data class Bind(
        val feedUri: String,
        val kind: FeedKind,
        /**
         * The analytics surface this binding represents — drives the
         * `source_surface` on post-interaction events (like/repost/share).
         * Defaults to [PostSurface.Feed] (the home feed); [FeedViewScreen]
         * binds with [PostSurface.FeedView] so its post interactions are
         * attributed distinctly even though it reuses this same VM.
         */
        val surface: PostSurface = PostSurface.Feed,
    ) : FeedEvent

    /**
     * Pull-to-refresh, the one destructive path (D8).
     *
     * Paging performs the fetch — the screen calls `refresh()` on the items,
     * which drives the mediator's `REFRESH` and replaces the partition. This
     * event carries what Paging does not know about: the reader asked for a
     * clean timeline, so the optimistic sets are dropped.
     *
     * There is deliberately no `Load`, `LoadMore` or `Retry`. A `Pager`
     * initializes itself, appends on scroll, and retries through
     * `LazyPagingItems.retry()`; events for those would be a second, racing
     * source of truth for the same fetches.
     */
    data object Refresh : FeedEvent

    /**
     * The reader stopped here: the key of the item at the top of the viewport
     * and its pixel offset within that row.
     *
     * Dispatched from the screen when a scroll settles, never per frame. A key
     * rather than an index, because an index means nothing across sessions —
     * moderation toggles, a mute and the chain re-projection all change how
     * many rows the same entries become (D6).
     */
    data class AnchorChanged(
        val itemKey: String,
        val offset: Int,
    ) : FeedEvent

    /** The screen has applied the pending restore and does not want it again. */
    data object RestoreConsumed : FeedEvent

    /**
     * The reader has seen what arrived above them — they tapped the pill, or
     * scrolled up past it.
     *
     * Recorded rather than just hidden: the pill is derived from what is
     * stored above the reader (D20), so without a persisted acknowledgement it
     * would be back on the next composition.
     */
    data object DismissNewPosts : FeedEvent

    /**
     * The loaded pages changed: every [PostUi] they now hold.
     *
     * Dispatched by the screen because the signal lives in `LazyPagingItems`.
     * The VM reconciles the interactions cache with it — wire data that is
     * newer than a cached overlay has to be able to replace it — and logs
     * `view_feed` the first time a binding shows content.
     */
    data class ItemsLoaded(
        val posts: List<PostUi>,
    ) : FeedEvent

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
     * User tapped a `#tag` / `$cashtag` in a post. [author] is the handle of
     * the account whose text holds the tag (Hashtag's author filter).
     */
    data class OnTagTapped(
        val tag: String,
        val author: String?,
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
     * User tapped an image of a quoted post embedded in a PostCard.
     * Routes directly to the fullscreen MediaViewer for the QUOTED post
     * — distinct from [OnQuotedPostTapped], which opens the quoted post's
     * detail. [quotedPostUri] is the quoted post's uri (from the embed).
     */
    data class OnQuotedImageTapped(
        val quotedPostUri: String,
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

    /** Re-send an outgoing post that failed. */
    data class RetryOutgoing(
        val localId: String,
    ) : FeedEvent

    /** Drop an outgoing post that failed. */
    data class DiscardOutgoing(
        val localId: String,
    ) : FeedEvent

    /**
     * User selected an overflow-menu entry on a PostCard. Routed through
     * the VM so it can decide the appropriate effect — oftc.2 wires
     * every action to a coming-soon snackbar via
     * [FeedEffect.ShowComingSoon]; oftc.3 / .4 / .5 swap each variant
     * for its real moderation RPC.
     */
    data class OnOverflowAction(
        val post: PostUi,
        val action: PostOverflowAction,
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
     * Push an arbitrary sub-route onto the MainShell stack — today the
     * Hashtag screen for a tapped `#tag`. The screen forwards [key] to its
     * host's `onNavigateTo`.
     */
    @Immutable
    data class NavigateTo(
        val key: NavKey,
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
}
