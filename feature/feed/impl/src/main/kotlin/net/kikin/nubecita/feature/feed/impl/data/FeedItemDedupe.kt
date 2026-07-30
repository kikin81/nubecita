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
 * **Reposts are exempt.** A reposted Single survives even when its post is
 * cluster context, because a repost is an explicit endorsement by someone the
 * viewer follows and is a distinct event from the original post. Dropping it
 * would silently remove that endorsement from the feed.
 *
 * This exemption also resolves decision D5 of
 * `openspec/changes/fix-feed-thread-root-dedupe`: "the cluster is canonical"
 * and the thread-root pass's "first wins" disagree only when a Single sits
 * ABOVE a cluster sharing its root. Chronologically that is impossible — a
 * reply is newer than its parent, so on a newest-first timeline the cluster
 * comes first. Measured over 216 live timeline entries, 17 of 18 such pairs had
 * the cluster first; the single inversion was a repost, whose feed position
 * reflects the repost time rather than the original post's. With reposts
 * exempt here, the two rules now agree in every reachable case, so
 * `dedupeByThreadRoot` can subsume this function as a safe refactor rather than
 * a behaviour change.
 *
 * Pure function over `List<FeedItemUi>` so callers (the VM reducers, any
 * future caller) can apply it without coordinating mutable state. O(n) —
 * one pass to collect cluster URIs, one filter pass.
 */
fun List<FeedItemUi>.dedupeClusterContext(): List<FeedItemUi> {
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
                    // Tombstones carry no PostUi, so they can't contribute
                    // to context-URI dedupe and they can't shadow a Single.
                    is FeedItemUi.Blocked, is FeedItemUi.NotFound -> Unit
                }
            }
        }
    if (contextUris.isEmpty()) return this
    return filter { item ->
        item !is FeedItemUi.Single || item.post.id !in contextUris || item.post.repostedBy != null
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
fun List<FeedItemUi>.dedupeByKey(): List<FeedItemUi> {
    if (size < 2) return this
    val seen = HashSet<String>(size)
    return filter { item -> seen.add(item.key) }
}

/**
 * Keeps at most one item per thread root, retaining the FIRST occurrence.
 *
 * The timeline returns entries in post time order, so several replies into the
 * same thread arrive as separate entries. Each becomes its own
 * [FeedItemUi.ReplyCluster] and re-renders that thread's root as context, so the
 * same post is drawn once per reply. Measured on a production account, 6 of 180
 * thread roots duplicated this way — the worst with seven replies spread across
 * three pages.
 *
 * Port of `FeedTuner.dedupThreads` from bluesky-social/social-app: keyed on the
 * thread root rather than the leaf, and dropping the whole item rather than just
 * the repeated context.
 *
 * **Assumes a newest-first list.** "First wins" is only chronologically
 * meaningful because the timeline is reverse-chronological, which makes the
 * retained item the newest reply and every dropped sibling strictly older. A
 * feed surface that ever presents items in another order would silently get an
 * arbitrary winner instead. Applied today only to follow-scoped feeds.
 *
 * Thread root per variant:
 * - [FeedItemUi.ReplyCluster] — the root post's id.
 * - [FeedItemUi.SelfThreadChain] — the first chained post's id. An
 *   approximation: chains do not retain the wire thread root, so if that post is
 *   itself a reply the true root is higher and this under-matches. Accepted —
 *   the observed duplication is cluster-driven.
 * - [FeedItemUi.Single] — the post's own id, so a standalone reserves its
 *   thread. This is what subsumes [dedupeClusterContext]'s case.
 * - Tombstones carry no post, so they have no thread root and are never dropped.
 *
 * Reposts are exempt from the drop but still register their root, matching the
 * official rule: a repost is an explicit endorsement by someone the viewer
 * follows and carries its own signal even when the thread has already been seen.
 *
 * Pure and O(n) — one pass with a `HashSet` of seen roots. Applied to the
 * ACCUMULATED list by the VM, which makes it span pagination without a stateful
 * tuner and reset naturally on refresh.
 *
 * Design: `openspec/changes/fix-feed-thread-root-dedupe`. Tracked as
 * `nubecita-w9of`.
 */
fun List<FeedItemUi>.dedupeByThreadRoot(): List<FeedItemUi> {
    if (size < 2) return this

    val survivors = ArrayList<FeedItemUi>(size)
    // Which survivor owns each thread, so a count lands on exactly one item
    // even when a repost survives alongside the first entry for that root.
    val ownerIndexByRoot = HashMap<String, Int>(size)
    val droppedByRoot = HashMap<String, MutableList<FeedItemUi>>()

    for (item in this) {
        val root = item.threadRootId()
        if (root == null) {
            survivors += item
            continue
        }
        // Register the root either way; a repost must not let later plain
        // replies into the same thread stack on top of it.
        val firstTimeSeen = !ownerIndexByRoot.containsKey(root)
        if (firstTimeSeen || item.isRepost()) {
            if (firstTimeSeen) ownerIndexByRoot[root] = survivors.size
            survivors += item
        } else {
            droppedByRoot.getOrPut(root) { mutableListOf() } += item
        }
    }

    if (droppedByRoot.isEmpty()) return survivors

    // A suppressed post the viewer can already see is not "more replies" —
    // most often the thread root, which the surviving item renders as context,
    // and in the reported reproduction the dropped cluster's leaf, which is the
    // survivor's parent.
    val visible = HashSet<String>(survivors.size * 3)
    for (survivor in survivors) survivor.addRenderedPostIdsTo(visible)
    droppedByRoot.forEach { (root, dropped) ->
        val index = ownerIndexByRoot[root] ?: return@forEach
        val count = dropped.sumOf { d -> d.countSuppressedRepliesNotIn(visible) }
        if (count > 0) survivors[index] = survivors[index].withSuppressedReplyCount(count)
    }
    return survivors
}

/**
 * Adds every post this item puts on screen — what the viewer can already see.
 *
 * Writes into [destination] rather than returning a list: this runs once per
 * survivor, and the intermediate lists were pure garbage.
 */
private fun FeedItemUi.addRenderedPostIdsTo(destination: MutableCollection<String>) {
    when (this) {
        is FeedItemUi.ReplyCluster -> {
            destination.add(root.id)
            destination.add(parent.id)
            destination.add(leaf.id)
        }
        is FeedItemUi.SelfThreadChain -> for (post in posts) destination.add(post.id)
        is FeedItemUi.Single -> destination.add(post.id)
        is FeedItemUi.Blocked, is FeedItemUi.NotFound -> Unit
    }
}

/**
 * How many posts a dropped item would have contributed as REPLIES that are not
 * already on screen — the unit the affordance is counted in. A cluster's root
 * and parent are ancestors shown for context, so only its leaf counts; a chain
 * contributes every post in it.
 */
private fun FeedItemUi.countSuppressedRepliesNotIn(visible: Set<String>): Int =
    when (this) {
        is FeedItemUi.ReplyCluster -> if (leaf.id !in visible) 1 else 0
        is FeedItemUi.SelfThreadChain -> posts.count { it.id !in visible }
        is FeedItemUi.Single -> if (post.id !in visible) 1 else 0
        is FeedItemUi.Blocked, is FeedItemUi.NotFound -> 0
    }

private fun FeedItemUi.withSuppressedReplyCount(count: Int): FeedItemUi =
    when (this) {
        is FeedItemUi.ReplyCluster -> copy(suppressedReplyCount = count)
        is FeedItemUi.SelfThreadChain -> copy(suppressedReplyCount = count)
        is FeedItemUi.Single -> copy(suppressedReplyCount = count)
        is FeedItemUi.Blocked, is FeedItemUi.NotFound -> this
    }

/** The thread this item belongs to, or null for tombstones (see [dedupeByThreadRoot]). */
private fun FeedItemUi.threadRootId(): String? =
    when (this) {
        is FeedItemUi.ReplyCluster -> root.id
        is FeedItemUi.SelfThreadChain -> posts.first().id
        is FeedItemUi.Single -> post.id
        is FeedItemUi.Blocked, is FeedItemUi.NotFound -> null
    }

/** Whether this item reached the feed as a repost, which exempts it from the drop. */
private fun FeedItemUi.isRepost(): Boolean =
    when (this) {
        is FeedItemUi.ReplyCluster -> leaf.repostedBy != null
        is FeedItemUi.SelfThreadChain -> posts.last().repostedBy != null
        is FeedItemUi.Single -> post.repostedBy != null
        is FeedItemUi.Blocked, is FeedItemUi.NotFound -> false
    }
