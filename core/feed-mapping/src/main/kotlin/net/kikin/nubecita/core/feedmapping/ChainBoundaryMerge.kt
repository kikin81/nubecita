package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi

/**
 * Result of [mergeChainBoundary]. Two `ImmutableList<FeedItemUi>` fields
 * with named slots — the previous `Pair<ImmutableList, ImmutableList>`
 * shape was a destructuring footgun: a future caller writing
 * `val (page, existing) = mergeChainBoundary(...)` (wrong order) would
 * compile cleanly and silently render the feed out of order. Named
 * fields make the intent unambiguous at every call site.
 */
data class ChainBoundaryMerge(
    /**
     * Existing `feedItems` with the tail popped if it was absorbed into
     * a chain at the new page's head; otherwise unchanged.
     */
    val trimmedExisting: ImmutableList<FeedItemUi>,
    /**
     * The new page's `feedItems` with its first entry replaced by an
     * absorbing [FeedItemUi.SelfThreadChain] when the merge fired;
     * otherwise unchanged.
     */
    val pageWithAbsorbedHead: ImmutableList<FeedItemUi>,
)

/**
 * Page-boundary chain merge. Given the existing `feedItems` and a
 * freshly-loaded `TimelinePage`, attempt to absorb the existing tail
 * into the new page's first feed item if the strict link rule holds.
 *
 * Caller appends [ChainBoundaryMerge.trimmedExisting] to
 * [ChainBoundaryMerge.pageWithAbsorbedHead]; subsequent dedupe-by-key
 * handles any server-side overlap independently.
 *
 * Merge is rejected (no-op) when:
 * - `existing` is empty or `newPageItems` / `newPageWirePosts` is empty.
 * - The existing tail is a [FeedItemUi.ReplyCluster] (cross-author
 *   clusters don't extend into same-author chains).
 * - The existing tail's last post has `repostedBy != null` (the wire
 *   entry that produced it was a `ReasonRepost` — chain-incompatible).
 * - The new page's first wire entry doesn't link to the tail's last
 *   post via the strict rule (`reply.parent.uri == tail.id`, same
 *   author, no `ReasonRepost` on either side).
 *
 * Chain extension only ever consumes the existing tail + the new page's
 * FIRST item. If the new page already grouped consecutive same-author
 * replies into a chain at its head (the page-internal projection ran in
 * the mapper), the merge prepends the existing tail's posts to that
 * chain. Subsequent new-page entries are unaffected — the strict link
 * rule's adjacency requirement is preserved by chain construction.
 */
fun mergeChainBoundary(
    existing: ImmutableList<FeedItemUi>,
    newPageItems: ImmutableList<FeedItemUi>,
    newPageWirePosts: ImmutableList<FeedViewPost>,
): ChainBoundaryMerge {
    if (existing.isEmpty() || newPageItems.isEmpty() || newPageWirePosts.isEmpty()) {
        return ChainBoundaryMerge(trimmedExisting = existing, pageWithAbsorbedHead = newPageItems)
    }

    val existingTail = existing.last()
    val tailPosts: List<PostUi> =
        when (existingTail) {
            is FeedItemUi.Single -> listOf(existingTail.post)
            is FeedItemUi.SelfThreadChain -> existingTail.posts
            // Reply clusters and tombstones can never be the head of a
            // self-author chain — clusters carry a different leaf author than
            // root/parent, and tombstones carry no author at all. Bail without
            // an attempted merge.
            is FeedItemUi.ReplyCluster,
            is FeedItemUi.Blocked,
            is FeedItemUi.NotFound,
            ->
                return ChainBoundaryMerge(trimmedExisting = existing, pageWithAbsorbedHead = newPageItems)
        }
    val tailLeafPost = tailPosts.last()

    // Strip a leading cursor-resync overlap before running the link
    // check: if the server replays the existing tail's leaf as the new
    // page's first wire entry, dropping it lets the link rule fire
    // against the actual successor (wirePosts[1]) rather than rejecting
    // and leaving the chain visually split. Both the wire list and the
    // projected feed items list are trimmed in lockstep so they stay
    // index-aligned for the absorption step below.
    val (effectiveWirePosts, effectivePageItems) =
        stripLeadingTailOverlap(
            tailLeafId = tailLeafPost.id,
            wirePosts = newPageWirePosts,
            pageItems = newPageItems,
        )
    if (effectiveWirePosts.isEmpty() || effectivePageItems.isEmpty()) {
        return ChainBoundaryMerge(trimmedExisting = existing, pageWithAbsorbedHead = effectivePageItems)
    }

    val firstWire = effectiveWirePosts.first()
    if (!tailLeafPost.linksToWire(firstWire)) {
        return ChainBoundaryMerge(trimmedExisting = existing, pageWithAbsorbedHead = effectivePageItems)
    }

    // Extract the new page's first-item posts to prepend the existing
    // tail to. The page-internal projection might have produced any of:
    // - a Single (the wire entry didn't link to anything within the page)
    // - a SelfThreadChain (the wire entry was the head of a within-page chain)
    // - a ReplyCluster (the wire entry has a `reply.parent` that's a real
    //   PostView; the per-entry mapper eagerly produces a ReplyCluster
    //   regardless of author DID). For chain purposes, only the leaf
    //   matters — the cluster's root/parent context is exactly the post
    //   at the end of the existing tail (alice/N) which we're already
    //   prepending. Adding root/parent again would duplicate it.
    val newFirstItem = effectivePageItems.first()
    val newFirstPosts: List<PostUi> =
        when (newFirstItem) {
            is FeedItemUi.Single -> listOf(newFirstItem.post)
            is FeedItemUi.ReplyCluster -> listOf(newFirstItem.leaf)
            is FeedItemUi.SelfThreadChain -> newFirstItem.posts
            // Tombstone variants can't link to an existing chain — bail back
            // to the caller with the page untouched. The mapper doesn't emit
            // tombstones at the head of a wire-fetched page today; this guard
            // exists for symmetry with the FeedItemUi sealed surface.
            is FeedItemUi.Blocked, is FeedItemUi.NotFound ->
                return ChainBoundaryMerge(trimmedExisting = existing, pageWithAbsorbedHead = effectivePageItems)
        }
    // Defense in depth: even after the leading-overlap strip above, drop
    // any prefix of newFirstPosts whose URIs match the existing tail's
    // posts. A future server overlap shape we haven't seen yet won't
    // produce a duplicated post inside the merged chain.
    val tailIds = tailPosts.mapTo(HashSet()) { it.id }
    val deduplicatedNewFirstPosts = newFirstPosts.dropWhile { it.id in tailIds }
    if (deduplicatedNewFirstPosts.isEmpty()) {
        return ChainBoundaryMerge(
            trimmedExisting = existing,
            pageWithAbsorbedHead = effectivePageItems.drop(1).toImmutableList(),
        )
    }
    val absorbedFirst =
        FeedItemUi.SelfThreadChain(
            posts = (tailPosts + deduplicatedNewFirstPosts).toImmutableList(),
        )

    return ChainBoundaryMerge(
        trimmedExisting = existing.dropLast(1).toImmutableList(),
        pageWithAbsorbedHead = (listOf<FeedItemUi>(absorbedFirst) + effectivePageItems.drop(1)).toImmutableList(),
    )
}

/**
 * Drops a leading new-page entry whose post URI matches the existing
 * tail's leaf URI — the cursor-resync overlap pattern. Trims both the
 * wire-level and projected-feed-items lists in lockstep so the
 * boundary-merge link check runs against the next NEW wire entry.
 *
 * Three projection shapes the leading wire entry might take:
 * - [FeedItemUi.Single] / [FeedItemUi.ReplyCluster]: drop both lists' first
 *   element together (the projection consumed exactly one wire entry).
 * - [FeedItemUi.SelfThreadChain]: the chain's leading post is the
 *   overlap; drop the chain's first post (collapse to a Single if the
 *   remaining size is 1, or a smaller chain if ≥ 2). Drop the matching
 *   wire entry. The chain's body is preserved so post-overlap entries
 *   still flow into the boundary merge.
 *
 * Repeats while the leading wire entry's URI keeps matching the tail
 * leaf — handles the rare multi-entry overlap pattern transparently.
 */
private fun stripLeadingTailOverlap(
    tailLeafId: String,
    wirePosts: ImmutableList<FeedViewPost>,
    pageItems: ImmutableList<FeedItemUi>,
): Pair<ImmutableList<FeedViewPost>, ImmutableList<FeedItemUi>> {
    var w: List<FeedViewPost> = wirePosts
    var p: List<FeedItemUi> = pageItems
    while (true) {
        if (w.isEmpty() || p.isEmpty()) break
        if (w
                .first()
                .post.uri.raw != tailLeafId
        ) {
            break
        }
        val firstItem = p.first()
        // Tombstones never duplicate against a wire post URI on the
        // cursor-resync boundary — they carry no PostUi, so the outer
        // URI guard could only have matched against a different wire
        // entry. Short-circuit BEFORE any list mutation: stripping
        // here would silently delete the tombstone from the projection
        // and desync it from the wire list. Today's mapper never emits
        // tombstones at a page head, so this branch protects future
        // client-side optimism (oftc.4) rather than current behavior.
        if (firstItem is FeedItemUi.Blocked || firstItem is FeedItemUi.NotFound) break
        w = w.drop(1)
        val replacementHead: List<FeedItemUi> =
            when (firstItem) {
                is FeedItemUi.Single, is FeedItemUi.ReplyCluster -> emptyList()
                is FeedItemUi.SelfThreadChain -> {
                    val chainTail = firstItem.posts.drop(1)
                    when (chainTail.size) {
                        0 -> emptyList() // unreachable: SelfThreadChain invariant size >= 2
                        1 -> listOf(FeedItemUi.Single(chainTail.first()))
                        else -> listOf(FeedItemUi.SelfThreadChain(chainTail.toImmutableList()))
                    }
                }
                // Short-circuited above; this branch keeps the sealed
                // match exhaustive without contributing reachable code.
                is FeedItemUi.Blocked, is FeedItemUi.NotFound ->
                    error("Tombstone short-circuit above prevents this branch")
            }
        p = replacementHead + p.drop(1)
    }
    return w.toImmutableList() to p.toImmutableList()
}
