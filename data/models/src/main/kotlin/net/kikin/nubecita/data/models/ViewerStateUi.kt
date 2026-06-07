package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable

/**
 * Current-viewer-specific flags for a [PostUi]. The viewer is the
 * authenticated user; these flags are stripped (or always `false`) for
 * unauthenticated rendering paths.
 *
 * Toggling like / repost in the UI fires a callback to the host VM, which
 * mutates server state and re-emits a new `PostUi` with an updated
 * `ViewerStateUi`. PostCard never flips these locally — see the PostCard
 * design doc, Decision 9 (loaded-state-only).
 *
 * [likeUri] / [repostUri] carry the AT URIs of the records the viewer
 * created when they liked / reposted this post. The host VM passes them
 * back to `LikeRepostRepository.unlike(...)` / `unrepost(...)` to delete
 * the right record. Stored as raw strings (not `AtUri`) to keep this
 * UI-layer model atproto-runtime-light — the VM reconstructs the typed
 * value at the boundary.
 *
 * `*Uri` may be null even when the matching boolean is true during the
 * optimistic-flip → server-response window: the VM flips
 * `isLikedByViewer` / `isRepostedByViewer` immediately on tap but only
 * learns the server-assigned record URI when `like(...)` / `repost(...)`
 * resolves. Callers that need to call unlike / unrepost MUST handle a
 * null URI as "no record to delete yet"; FeedViewModel rolls the
 * optimistic flip back when this happens so the UI doesn't drift into
 * a permanent isLiked-without-likeUri state.
 */
@Stable
public data class ViewerStateUi(
    val isLikedByViewer: Boolean = false,
    val isRepostedByViewer: Boolean = false,
    val isFollowingAuthor: Boolean = false,
    val likeUri: String? = null,
    val repostUri: String? = null,
    /**
     * Whether the viewer has muted the post's author. Sourced from the
     * post's `author.viewer.muted` (AT Protocol `app.bsky.actor.defs#viewerState`),
     * NOT from the post-level viewer block — moderation lives on the
     * actor's viewer state, not the post's.
     */
    val isAuthorMutedByViewer: Boolean = false,
    /**
     * Whether the viewer is blocking the post's author. Sourced from the
     * truthiness of `author.viewer.blocking` (an `AtUri?` on the wire —
     * the AT URI of the viewer's own `app.bsky.graph.block` record). True
     * iff the URI is non-null.
     */
    val isAuthorBlockedByViewer: Boolean = false,
    /**
     * Whether the post's author is blocking the viewer. Sourced from
     * `author.viewer.blockedBy == true`. Independent of
     * [isAuthorBlockedByViewer] — a viewer can be blocked by an author
     * they don't themselves block, and vice versa.
     */
    val isAuthorBlockingViewer: Boolean = false,
    /**
     * Whether the viewer is allowed to reply to this post. Sourced from the
     * INVERSE of the post viewer's `replyDisabled` (AT Protocol
     * `app.bsky.feed.defs#viewerState`), which the appview computes server-side
     * from the post's `app.bsky.feed.threadgate` (mention / following / follower
     * / list rules). We trust the server flag rather than reimplementing the
     * rules client-side. Defaults to `true` (fail open) — most posts carry no
     * threadgate, so `replyDisabled` is absent and replies are allowed.
     */
    val canViewerReply: Boolean = true,
    /**
     * Whether the viewer is allowed to quote this post. Sourced from the INVERSE
     * of the post viewer's `embeddingDisabled` (AT Protocol
     * `app.bsky.feed.defs#viewerState`), which the appview computes server-side
     * from the post's `app.bsky.feed.postgate` embedding rules. We trust the
     * server flag rather than reimplementing the rules client-side. Defaults to
     * `true` (fail open) — most posts carry no postgate, so `embeddingDisabled`
     * is absent and quoting is allowed. Consumed by the repost menu (the "Quote
     * post" item is hidden when false) and the composer's paste-a-link quote
     * detection (a gated post is rejected).
     */
    val canViewerQuote: Boolean = true,
)
