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
                when (item) {
                    is FeedItemUi.ReplyCluster -> {
                        add(item.root.id)
                        add(item.parent.id)
                    }
                    is FeedItemUi.SelfThreadChain -> {
                        // Non-leaf chain posts are context (the leaf is the
                        // canonical entry, same shape as ReplyCluster.leaf).
                        // A standalone Single whose URI matches a non-leaf
                        // chain post is a duplicate that should be dropped.
                        for (i in 0 until item.posts.lastIndex) {
                            add(item.posts[i].id)
                        }
                    }
                    is FeedItemUi.Single -> Unit
                }
            }
        }
    if (contextUris.isEmpty()) return this
    return filter { item ->
        item !is FeedItemUi.Single || item.post.id !in contextUris
    }
}

/**
 * Drops `FeedItemUi` entries whose `key` has already appeared earlier in
 * the list, keeping the first occurrence. The renderer's `LazyColumn`
 * uses [FeedItemUi.key] as the slot key, and Compose throws
 * `IllegalArgumentException: Key … was already used` on duplicates —
 * which crashes the feed mid-scroll if a duplicate slot scrolls into view.
 *
 * Two scenarios surface duplicates that [dedupeClusterContext] does not catch:
 *
 * - Two `Single` entries for the same post URI. Happens when (a) the user
 *   reposts a post AND (b) someone the user follows also reposts the same
 *   post — the timeline returns both as separate `FeedViewPost` entries
 *   with the same `post.uri`. Both project to `Single(post=samePost)`.
 *
 * - A `ReplyCluster.leaf.id` matching a later `Single.post.id` (the
 *   leaf got reposted further down the timeline). The cluster is canonical
 *   for the same reason as in [dedupeClusterContext]; the Single is the
 *   duplicate.
 *
 * Run AFTER [dedupeClusterContext] so cluster-context drops happen first,
 * then key-collisions are resolved on the surviving items. Pure O(n) —
 * one pass with a `HashSet` of seen keys.
 *
 * Tracked as `nubecita-7p3`.
 */
internal fun List<FeedItemUi>.dedupeByKey(): List<FeedItemUi> {
    if (size < 2) return this
    val seen = HashSet<String>(size)
    return filter { item -> seen.add(item.key) }
}
