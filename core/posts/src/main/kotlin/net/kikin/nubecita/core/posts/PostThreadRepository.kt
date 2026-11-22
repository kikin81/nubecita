package net.kikin.nubecita.core.posts

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.ThreadItem

/**
 * `app.bsky.feed.getPostThread` read surface, exposed by `:core:posts`.
 *
 * Sibling to [PostRepository] (single-post `getPosts` read): this is the
 * thread read (focus post + ancestors + flattened replies). Lifted out of
 * `:feature:postdetail:impl` (nubecita-6rdb.3) so both post-detail and the
 * fullscreen player's comments sheet consume one interface rather than
 * duplicating the fetch or coupling feature `:impl` modules together.
 *
 * The default implementation is the only file in the project that imports
 * the atproto-kotlin client surface for `getPostThread`; consumers MUST go
 * through this interface. Mirrors the single-import discipline
 * [PostRepository] enforces for `getPosts`.
 */
public interface PostThreadRepository {
    public suspend fun getPostThread(uri: String): Result<ImmutableList<ThreadItem>>

    /**
     * The last-seen thread for [uri] from the local cache — the full snapshot,
     * or the focus alone when only the post itself is cached (e.g. from the
     * feed) — mapped with the viewer's current moderation prefs. Null when
     * nothing is cached. No network: callers render it, then [getPostThread].
     *
     * Defaults to null so implementations without a cache (the bench fake)
     * always load from their source.
     */
    public suspend fun cachedPostThread(uri: String): ImmutableList<ThreadItem>? = null
}
