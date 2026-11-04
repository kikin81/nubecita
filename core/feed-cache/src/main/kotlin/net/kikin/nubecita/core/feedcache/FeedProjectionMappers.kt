package net.kikin.nubecita.core.feedcache

import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity
import net.kikin.nubecita.core.feedmapping.applyModeration
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi

/**
 * Rehydrates a persisted projection row back into the [FeedItemUi] it was
 * written from.
 *
 * The projection stores structure and references, never content, so rebuilding
 * an item needs three things joined: the item row (variant, counts, flags), its
 * membership rows in ordinal order, and the stored posts those URIs name.
 *
 * This is the read half of "compute the grouping once at write time". It is
 * needed in three places and written once here so they cannot diverge: the
 * keyset `PagingSource` the screen reads, the `head` surfaces, and — less
 * obviously — the **write** path itself, which has to rehydrate the partition's
 * tail item before it can tell whether a newly appended page extends a chain
 * across the pagination cut.
 *
 * ---
 *
 * One projected item, or `null` when it cannot be rendered.
 *
 * Returns `null` when the variant is unrecognized, when a member post is
 * missing or unreadable, or when the item is a seam marker — a seam is a row in
 * this table but not a `FeedItemUi`, and the consuming feature maps it to its
 * own UI type rather than forcing a variant into the shared sealed surface.
 *
 * Moderation is applied per post on the way out, using the prefs passed in, so
 * a preference change takes effect on the next read of an unchanged partition
 * exactly as it did before the projection was persisted.
 */
fun FeedItemEntity.toFeedItemUi(
    members: List<FeedItemMemberEntity>,
    postsByUri: Map<String, CachedPostEntity>,
    viewerDid: String?,
    prefs: ModerationPrefs,
): FeedItemUi? {
    if (rowType != FeedItemEntity.ROW_TYPE_ITEM) return null

    val ordered = members.sortedBy { it.ordinal }
    val stored = ordered.map { postsByUri[it.uri] }

    return when (variant) {
        VARIANT_SINGLE -> {
            val post = stored.firstOrNull()?.readable(viewerDid, prefs) ?: return null
            FeedItemUi.Single(post = post, suppressedReplyCount = suppressedReplyCount)
        }
        VARIANT_CLUSTER -> {
            // A cluster is root → parent → leaf. Any missing slot makes the
            // item unrenderable as a cluster; a partial cluster would be a
            // card with a hole in it, so the row is skipped instead.
            if (ordered.size < 3) return null
            val root = stored[0]?.readable(viewerDid, prefs) ?: return null
            val parent = stored[1]?.readable(viewerDid, prefs) ?: return null
            val leaf = stored[2]?.readable(viewerDid, prefs) ?: return null
            FeedItemUi.ReplyCluster(
                root = root,
                parent = parent,
                leaf = leaf,
                hasEllipsis = hasEllipsis == true,
                suppressedReplyCount = suppressedReplyCount,
            )
        }
        VARIANT_CHAIN -> {
            val posts = stored.mapNotNull { it?.readable(viewerDid, prefs) }
            // A chain is defined by having more than one post; if moderation or
            // a missing row leaves one, it is a Single, not a one-link chain.
            when (posts.size) {
                0 -> null
                1 -> FeedItemUi.Single(post = posts.single(), suppressedReplyCount = suppressedReplyCount)
                else ->
                    FeedItemUi.SelfThreadChain(
                        posts = posts.toImmutableList(),
                        suppressedReplyCount = suppressedReplyCount,
                    )
            }
        }
        VARIANT_BLOCKED -> {
            val row = stored.firstOrNull() ?: return null
            FeedItemUi.Blocked(uri = row.uri, authorDid = row.authorDid ?: return null)
        }
        VARIANT_NOT_FOUND -> FeedItemUi.NotFound(uri = stored.firstOrNull()?.uri ?: return null)
        else -> null
    }
}

/**
 * The post as the reader may see it, or `null` when there is nothing to render.
 *
 * A tombstone row and an unreadable blob both yield `null`; moderation is
 * applied with `dropFiltered = true`, matching the feed contract — a resolved
 * HIDE removes the post, warned media is covered rather than dropped.
 */
private fun CachedPostEntity.readable(
    viewerDid: String?,
    prefs: ModerationPrefs,
): PostUi? {
    val view = toPostViewOrNull() ?: return null
    val post = view.toPostUiCore(viewerDid) ?: return null
    return post.applyModeration(
        labels = view.labels,
        viewerDid = viewerDid,
        prefs = prefs,
        dropFiltered = true,
    )
}

internal const val VARIANT_SINGLE = "single"
internal const val VARIANT_CLUSTER = "cluster"
internal const val VARIANT_CHAIN = "chain"
internal const val VARIANT_BLOCKED = "blocked"
internal const val VARIANT_NOT_FOUND = "notfound"
