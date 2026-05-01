package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri

/**
 * Like / unlike / repost / unrepost write surface against the
 * authenticated user's PDS, scoped to `:feature:feed:impl`.
 *
 * Headless — the interface returns plain [Result] outcomes and never
 * touches UI state. Optimistic toggling and error surfacing are the
 * caller's job; sibling issues under nubecita-8f6 own those.
 *
 * The interface is package-internal: no other module imports it. If a
 * second consumer (post detail, notifications) later needs the same
 * write path, the change that adds the consumer also promotes this
 * interface to a new `:core:feed` module.
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
internal interface LikeRepostRepository {
    /** Create an `app.bsky.feed.like` record pointing at [post]. */
    suspend fun like(post: StrongRef): Result<AtUri>

    /** Delete the `app.bsky.feed.like` record at [likeUri]. */
    suspend fun unlike(likeUri: AtUri): Result<Unit>

    /** Create an `app.bsky.feed.repost` record pointing at [post]. */
    suspend fun repost(post: StrongRef): Result<AtUri>

    /** Delete the `app.bsky.feed.repost` record at [repostUri]. */
    suspend fun unrepost(repostUri: AtUri): Result<Unit>
}
