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
    val viewerLikeUri: String? = null,
    val viewerRepostUri: String? = null,
    val likeCount: Long = 0,
    val repostCount: Long = 0,
    val pendingLikeWrite: PendingState = PendingState.None,
    val pendingRepostWrite: PendingState = PendingState.None,
)

/**
 * Sealed binary flag for in-flight network calls per interaction-type.
 *
 * Modeled as an enum (not Boolean) so future expansion to "Pending with
 * retry" or "PendingDelete" is a non-breaking variant addition rather
 * than a contract change.
 */
enum class PendingState { None, Pending }
