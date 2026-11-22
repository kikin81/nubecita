package net.kikin.nubecita.core.posts

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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

    /**
     * Post detail's read: the thread in the server's own order, with the
     * author's thread first under each post, thread positions on its posts and
     * read-more rows where the response was cut
     * (`app.bsky.unspecced.getPostThreadV2`).
     *
     * That endpoint is experimental upstream, so an implementation falls back
     * to [getPostThread] when it stops answering in a shape this app can read;
     * the thread then opens without positions. Defaults to [getPostThread] for
     * implementations that only have that read.
     */
    public suspend fun getOrderedThread(uri: String): Result<ImmutableList<ThreadItem>> = getPostThread(uri)

    /**
     * The replies [getOrderedThread] leaves out of the thread for [uri] —
     * muted accounts, replies hidden by the author — as [ThreadItem.Reply]
     * rows. Asked for only when the thread ends in [ThreadItem.OtherReplies].
     * Never cached.
     */
    public suspend fun getOtherReplies(uri: String): Result<ImmutableList<ThreadItem>> = Result.success(persistentListOf())
}
