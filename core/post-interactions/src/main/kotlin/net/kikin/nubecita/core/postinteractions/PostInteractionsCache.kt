package net.kikin.nubecita.core.postinteractions

import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.StateFlow
import net.kikin.nubecita.data.models.PostUi

/**
 * Singleton cache + broadcast layer for post interactions (like / repost).
 *
 * Owns the canonical "is post X liked/reposted right now" state across the
 * app session. Every screen that renders PostCards subscribes to [state]
 * and projects the cached interaction onto its own `PostUi` list. A like
 * on PostDetail mutates the cache; Feed's subscriber receives the
 * emission and re-renders without re-fetching.
 *
 * # Subscriber pattern
 *
 * ```kotlin
 * viewModelScope.launch {
 *     cache.state
 *         .map { interactionMap -> currentItems().applyInteractions(interactionMap) }
 *         .distinctUntilChanged()
 *         .collect { setState { copy(items = it) } }
 * }
 * ```
 *
 * # Seed contract
 *
 * Every screen that LOADS posts (initial fetch, refresh, pagination)
 * MUST call [seed] with the loaded wire posts. The cache merges wire
 * data with any in-flight optimistic state, preserving pending writes
 * against the appview's eventual consistency lag.
 *
 * # Toggle contract
 *
 * [toggleLike] and [toggleRepost] are suspending and single-flight per
 * postUri. Double-taps during in-flight return [Result.success] without
 * re-firing the network call. On failure, the cache rolls back state
 * internally and returns [Result.failure] so the calling VM can route
 * the error to its own effect channel.
 *
 * # Sign-out
 *
 * [clear] resets the cache. Wired into `:core/auth/DefaultAuthRepository`
 * to fire before session revocation.
 */
interface PostInteractionsCache {
    /**
     * Canonical interaction state per postUri. Emits on every mutation
     * ([toggleLike] / [toggleRepost] / [seed] / [clear]).
     *
     * Keyed by `PostUi.id` (the post's AtUri).
     */
    val state: StateFlow<PersistentMap<String, PostInteractionState>>

    /**
     * Seed / refresh the cache from freshly-loaded wire posts. Idempotent;
     * safe to call on every wire fetch (initial load, refresh, tab switch,
     * pagination page).
     *
     * Merger rules:
     * - If `cache[postUri].pendingLikeWrite == Pending` OR
     *   `pendingRepostWrite == Pending`: preserve existing state entirely.
     *   Wire data is presumed stale during in-flight writes.
     * - Else if `cache[postUri].viewerLikeUri != null` AND wire's
     *   `viewer.likeUri == null`: preserve cache state for this post
     *   (atproto appview lags; user's recent like not yet indexed).
     * - Else: seed from wire (counts, viewerLikeUri/viewerRepostUri).
     *
     * Same shape for repost.
     */
    fun seed(posts: List<PostUi>)

    /**
     * Toggle like for [postUri]. Suspending: returns when the network call
     * resolves (success or failure).
     *
     * Behavior:
     * 1. Single-flight: if a like call is already in flight for this
     *    postUri, returns `Result.success(Unit)` synthetically without
     *    dispatching anything.
     * 2. Optimistic flip: writes a new state with `pendingLikeWrite =
     *    Pending`, flipped viewerLikeUri (null → PENDING_LIKE_SENTINEL or
     *    vice-versa), and ±1 likeCount delta. Emits.
     * 3. Fires the underlying repository call.
     * 4. Success: promotes pending to real (wire-returned AtUri on like;
     *    null on unlike); clears pendingLikeWrite. Emits.
     * 5. Failure: rolls back to the pre-tap snapshot; clears
     *    pendingLikeWrite. Emits. Returns `Result.failure(throwable)`.
     *
     * @param postUri The post's AtUri (`PostUi.id`).
     * @param postCid The post's CID (`PostUi.cid`), required for the
     *   `StrongRef` that `app.bsky.feed.like` records.
     */
    suspend fun toggleLike(
        postUri: String,
        postCid: String,
    ): Result<Unit>

    /**
     * Symmetric for repost. Same single-flight semantics, same rollback
     * shape, same return contract.
     */
    suspend fun toggleRepost(
        postUri: String,
        postCid: String,
    ): Result<Unit>

    /**
     * Reset the cache. Called by `:core/auth/DefaultAuthRepository.signOut`
     * before session revocation so a re-login starts with a fresh
     * canonical state.
     */
    fun clear()
}
