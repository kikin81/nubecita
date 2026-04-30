package net.kikin.nubecita.feature.feed.impl.data

import net.kikin.nubecita.data.models.FeedItemUi

/**
 * Drops `FeedItemUi.Single` entries whose post URI already appears as the
 * `root` or `parent` of a `FeedItemUi.ReplyCluster` elsewhere in the same
 * list — i.e., the same post is being rendered twice (once standalone,
 * once as cluster context).
 *
 * This happens in practice because the Bluesky timeline can surface both
 * the original post AND a reply to it as separate `FeedViewPost` entries
 * (e.g., a self-reply where the user follows the author — they see the
 * original post and the reply both arrive on the timeline). Without this
 * dedup the user sees the original post body twice: once as the Single,
 * once as the cluster's root slot.
 *
 * The cluster is canonical — drop the Single. Mirrors bsky.app's
 * behavior: a reply with thread context is rendered as one cluster, and
 * the original post does NOT appear separately above the cluster.
 *
 * Pure function over `List<FeedItemUi>` so callers (the VM reducers, any
 * future caller) can apply it without coordinating mutable state. O(n) —
 * one pass to collect cluster URIs, one filter pass.
 */
internal fun List<FeedItemUi>.dedupeClusterContext(): List<FeedItemUi> {
    if (isEmpty()) return this
    val contextUris =
        buildSet {
            for (item in this@dedupeClusterContext) {
                if (item is FeedItemUi.ReplyCluster) {
                    add(item.root.id)
                    add(item.parent.id)
                }
            }
        }
    if (contextUris.isEmpty()) return this
    return filter { item ->
        item !is FeedItemUi.Single || item.post.id !in contextUris
    }
}
