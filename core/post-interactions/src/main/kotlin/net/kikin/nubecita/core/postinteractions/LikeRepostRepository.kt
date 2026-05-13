package net.kikin.nubecita.core.postinteractions

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri

/**
 * Like / unlike / repost / unrepost write surface against the
 * authenticated user's PDS.
 *
 * Headless — the interface returns plain [Result] outcomes and never
 * touches UI state. Optimistic toggling and error surfacing are the
 * caller's job; sibling issues under nubecita-8f6 own those.
 *
 * Lives in `:core/post-interactions`; consumed by [PostInteractionsCache]'s
 * implementation. Direct VM dependence on this interface is deprecated —
 * VMs should subscribe to [PostInteractionsCache.state] and dispatch
 * through [PostInteractionsCache.toggleLike] / [PostInteractionsCache.toggleRepost].
 *
 * The four operations map onto AT Protocol primitives:
 *
 * - [like] / [repost] — `com.atproto.repo.createRecord` with a freshly
 *   built `app.bsky.feed.like` / `app.bsky.feed.repost` record whose
 *   `subject` is the supplied [StrongRef]. The returned [AtUri] is the
 *   AT-URI of the newly-created record; the caller persists it so a
 *   later [unlike] / [unrepost] can target the right record.
 * - [unlike] / [unrepost] — `com.atproto.repo.deleteRecord` against
 *   the supplied AT-URI. The repo (DID) and rkey are parsed from the
 *   URI; the collection is fixed.
 */
interface LikeRepostRepository {
    /** Create an `app.bsky.feed.like` record pointing at [post]. */
    suspend fun like(post: StrongRef): Result<AtUri>

    /** Delete the `app.bsky.feed.like` record at [likeUri]. */
    suspend fun unlike(likeUri: AtUri): Result<Unit>

    /** Create an `app.bsky.feed.repost` record pointing at [post]. */
    suspend fun repost(post: StrongRef): Result<AtUri>

    /** Delete the `app.bsky.feed.repost` record at [repostUri]. */
    suspend fun unrepost(repostUri: AtUri): Result<Unit>
}
