package net.kikin.nubecita.core.postinteractions

/**
 * Per-post interaction state held by [PostInteractionsCache]. Subscribers
 * project this onto their own [net.kikin.nubecita.data.models.PostUi] list at
 * consumption time via [PostUi.mergeInteractionState].
 *
 * `viewerLikeUri` is the AtUri of the user's like RECORD (needed by
 * `deleteRecord` to unlike). While a like is in flight, this carries an
 * internal sentinel (see `internal/PendingSentinels.kt`). Null = not liked.
 *
 * `viewerRepostUri` is the AtUri of the user's repost RECORD; same shape.
 *
 * `pendingLikeWrite` / `pendingRepostWrite` is set to [PendingState.Pending]
 * while the network call is in flight, signaling [PostInteractionsCache.seed]
 * to FREEZE this post's interaction state against stale wire data
 * (atproto's appview lags `createRecord` writes by seconds to minutes).
 * Cleared by the cache itself when the network call resolves
 * (success → promote pending AtUri to real; failure → restore prior state).
 *
 * **The pending fields MUST NOT be projected onto `PostUi` via
 * [PostUi.mergeInteractionState]** — see design Decision 7. Exposing them
 * to UI invites spinners that defeat the optimistic-UI illusion. The
 * cache's single-flight guard already absorbs double-taps silently.
 */
data class PostInteractionState(
    /**
     * The post was deleted by its author and must not be rendered.
     *
     * Terminal and one-way: nothing clears it short of [PostInteractionsCache.clear].
     * That matters because the appview keeps returning a deleted post for a
     * while, so a refresh re-seeds it from the wire — without this flag winning
     * over wire data, a deleted post would reappear on the next page load.
     */
    val isDeleted: Boolean = false,
    val viewerLikeUri: String? = null,
    val viewerRepostUri: String? = null,
    val likeCount: Long = 0,
    val repostCount: Long = 0,
    val pendingLikeWrite: PendingState = PendingState.None,
    val pendingRepostWrite: PendingState = PendingState.None,
    // Bookmark has no viewer record URI (delete keys off the post's own AT URI),
    // so a boolean suffices — no sentinel. `pendingBookmarkWrite` freezes seeding
    // (prevents seed/merge from overwriting optimistic bookmark state) the same
    // way the like/repost pending flags do.
    val isBookmarked: Boolean = false,
    val bookmarkCount: Long = 0,
    val pendingBookmarkWrite: PendingState = PendingState.None,
    /** The reply count from the last wire seed; the base for [replyCountFloor]. */
    val replyCount: Long = 0,
    /**
     * At least this many replies, after the viewer replied in this session.
     * The appview counts a new reply a second or more after `createRecord`, so
     * a refresh in that window would otherwise snap the count back. Merged as
     * `max(wire, floor)`, so it stops mattering once the wire catches up.
     */
    val replyCountFloor: Long? = null,
    /**
     * The thread this post belongs to (`PostUi.threadRootUri`), refreshed on
     * every seed. A thread is keyed by `threadRootUri ?: postUri`.
     */
    val threadRootUri: String? = null,
    /** Effective thread-muted flag — wire value unless [threadMuteOverride] holds. */
    val isThreadMuted: Boolean = false,
    /**
     * A local thread-mute/unmute write's value, kept in BOTH directions until a
     * seed reports the same value from the wire (then cleared). Unlike likes,
     * the "off" direction needs protecting too: an unmute must survive a stale
     * refresh that still says muted.
     */
    val threadMuteOverride: Boolean? = null,
)

/**
 * Sealed binary flag for in-flight network calls per interaction-type.
 *
 * Modeled as an enum (not Boolean) so future expansion to "Pending with
 * retry" or "PendingDelete" is a non-breaking variant addition rather
 * than a contract change.
 */
enum class PendingState { None, Pending }
