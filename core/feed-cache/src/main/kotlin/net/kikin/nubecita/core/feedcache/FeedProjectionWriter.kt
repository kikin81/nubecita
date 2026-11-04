package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity
import net.kikin.nubecita.core.feedmapping.dedupeByThreadRoot
import net.kikin.nubecita.core.feedmapping.dedupeClusterContext
import net.kikin.nubecita.core.feedmapping.mergeChainBoundary
import net.kikin.nubecita.core.feedmapping.threadRootId
import net.kikin.nubecita.core.feedmapping.toFeedItemsUi
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedItemUi
import javax.inject.Inject

/**
 * Computes the `FeedItemUi` grouping projection for one fetched page and
 * persists it.
 *
 * The projection runs here, on the write path, because it needs a **whole
 * page** in hand: reply-cluster and self-thread-chain detection look across
 * consecutive entries, and the two de-duplication passes are a non-commutative
 * pipeline over the list. A `pagingMap` downstream of a `PagingSource` sees one
 * row at a time, so running it there would break the grouping at exactly the
 * page boundaries the feed spec requires it to survive.
 *
 * The two directions are not symmetric, and the asymmetry is the point
 * (design D7):
 *
 * - [writeRefresh] rebuilds the partition. Nothing is above it, so nothing
 *   seeds the de-duplication and single-occurrence semantics are restored
 *   wholesale.
 * - [writeAppend] lands below what is already rendered, so it seeds from the
 *   partition and a second reply into a thread already on screen is dropped —
 *   and it is the only direction that can extend a chain across the pagination
 *   cut.
 *
 * A PREPEND writer is deliberately absent until the prepend path itself lands;
 * when it does, it seeds **neither** parameter, so a thread resurfaces with its
 * newer reply above the reader rather than being suppressed by an occurrence
 * they already scrolled past.
 *
 * Callers run this inside the same transaction as the entry and post writes, so
 * no reader observes a partition whose projection disagrees with its rows.
 */
internal class FeedProjectionWriter
    @Inject
    constructor(
        private val feedItemDao: FeedItemDao,
        private val cachedPostDao: CachedPostDao,
        private val sessionStateProvider: SessionStateProvider,
        private val moderationPreferences: ModerationPreferencesRepository,
    ) {
        /**
         * The viewer and preferences the projection is computed with.
         *
         * Read here so the two call sites stay simple, and read **live** for
         * now. Design D23a moves this to a per-partition preference
         * fingerprint, so a background write projects with the preferences the
         * reader last saw applied rather than whatever is current; that lands
         * with the re-projection work, and this is the single place it changes.
         */
        private fun viewerDid(): String? = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did

        private fun prefs(): ModerationPrefs = moderationPreferences.prefs.value

        /** [writeRefresh] with the current viewer and preferences. */
        suspend fun writeRefresh(
            feedKey: FeedKey,
            page: List<FeedViewPost>,
        ) = writeRefresh(feedKey, page, viewerDid(), prefs())

        /** [writeAppend] with the current viewer and preferences. */
        suspend fun writeAppend(
            feedKey: FeedKey,
            page: List<FeedViewPost>,
        ) = writeAppend(feedKey, page, viewerDid(), prefs())

        /** Rebuilds [feedKey]'s projection from a REFRESH page. */
        suspend fun writeRefresh(
            feedKey: FeedKey,
            page: List<FeedViewPost>,
            viewerDid: String?,
            prefs: ModerationPrefs,
        ) {
            feedItemDao.clearPartition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
            val items =
                page
                    .toFeedItemsUi(prefs, viewerDid)
                    .dedupeByThreadRoot()
                    .dedupeClusterContext()
            write(feedKey, items, basePosition = 0)
        }

        /**
         * Appends an APPEND page's projection below what is already there.
         *
         * Order matters. The chain merge runs **before** de-duplication,
         * because absorbing the tail changes what the page's first item *is* —
         * a `Single` can become a chain, which changes its thread root and so
         * changes whether de-duplication drops it.
         */
        suspend fun writeAppend(
            feedKey: FeedKey,
            page: List<FeedViewPost>,
            viewerDid: String?,
            prefs: ModerationPrefs,
        ) {
            val projected = page.toFeedItemsUi(prefs, viewerDid)
            if (projected.isEmpty()) return

            val tail = rehydrateTail(feedKey, viewerDid, prefs)
            val merged =
                if (tail == null) {
                    projected
                } else {
                    val merge =
                        mergeChainBoundary(
                            existing = listOf(tail.item).toImmutableList(),
                            newPageItems = projected,
                            newPageWirePosts = page.toImmutableList(),
                        )
                    // An empty `trimmedExisting` means the tail was absorbed
                    // into the page's head, so its row has to go — leaving it
                    // would render the same posts twice, once as the old tail
                    // and once inside the chain that swallowed it.
                    if (merge.trimmedExisting.isEmpty()) {
                        feedItemDao.deleteItemAt(
                            feedKey.accountDid,
                            feedKey.feedType.name,
                            feedKey.feedUri,
                            tail.position,
                        )
                    }
                    merge.pageWithAbsorbedHead
                }

            // Seeded AFTER the possible tail deletion, so an absorbed tail's
            // root is not counted as "already rendered above" and used to drop
            // the very chain that absorbed it.
            val seenRoots =
                feedItemDao
                    .seenThreadRoots(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                    .toSet()
            val visible =
                feedItemDao
                    .renderedPostUris(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                    .toSet()

            val items =
                merged
                    .dedupeByThreadRoot(seenRoots = seenRoots, visibleFromAbove = visible)
                    .dedupeClusterContext()

            val base = (feedItemDao.maxPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) ?: -1) + 1
            write(feedKey, items, basePosition = base)
        }

        private data class Tail(
            val item: FeedItemUi,
            val position: Int,
        )

        /**
         * The partition's last item as a [FeedItemUi], or `null` when there is
         * nothing to extend.
         *
         * The merge needs the tail's posts, not just its row, which is why the
         * projection's read mapper is a prerequisite of the write path.
         */
        private suspend fun rehydrateTail(
            feedKey: FeedKey,
            viewerDid: String?,
            prefs: ModerationPrefs,
        ): Tail? {
            val row =
                feedItemDao.tailItem(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) ?: return null
            val members =
                feedItemDao.membersOf(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri, row.position)
            val posts = cachedPostDao.byUris(feedKey.accountDid, members.map { it.uri })
            val item =
                row.toFeedItemUi(
                    members = members,
                    postsByUri = posts.associateBy { it.uri },
                    viewerDid = viewerDid,
                    prefs = prefs,
                ) ?: return null
            return Tail(item = item, position = row.position)
        }

        private suspend fun write(
            feedKey: FeedKey,
            items: List<FeedItemUi>,
            basePosition: Int,
        ) {
            if (items.isEmpty()) return
            val rows = ArrayList<FeedItemEntity>(items.size)
            val members = ArrayList<FeedItemMemberEntity>(items.size * 2)
            items.forEachIndexed { index, item ->
                val position = basePosition + index
                val uris = item.memberUris()
                rows +=
                    FeedItemEntity(
                        accountDid = feedKey.accountDid,
                        feedType = feedKey.feedType.name,
                        feedUri = feedKey.feedUri,
                        position = position,
                        rowType = FeedItemEntity.ROW_TYPE_ITEM,
                        variant = item.variantName(),
                        itemKey = item.key,
                        leafUri = uris.lastOrNull(),
                        suppressedReplyCount = item.suppressedReplyCount,
                        // The same derivation the de-duplication pass uses, by
                        // construction rather than by agreement.
                        threadRootUri = item.threadRootId(),
                        hasEllipsis = (item as? FeedItemUi.ReplyCluster)?.hasEllipsis,
                    )
                uris.forEachIndexed { ordinal, uri ->
                    members +=
                        FeedItemMemberEntity(
                            accountDid = feedKey.accountDid,
                            feedType = feedKey.feedType.name,
                            feedUri = feedKey.feedUri,
                            position = position,
                            ordinal = ordinal,
                            uri = uri,
                        )
                }
            }
            feedItemDao.upsert(rows)
            feedItemDao.upsertMembers(members)
        }
    }

/** The item's posts in render order — what [FeedItemMemberEntity] rows record. */
private fun FeedItemUi.memberUris(): List<String> =
    when (this) {
        is FeedItemUi.Single -> listOf(post.id)
        is FeedItemUi.ReplyCluster -> listOf(root.id, parent.id, leaf.id)
        is FeedItemUi.SelfThreadChain -> posts.map { it.id }
        is FeedItemUi.Blocked -> listOf(uri)
        is FeedItemUi.NotFound -> listOf(uri)
    }

private fun FeedItemUi.variantName(): String =
    when (this) {
        is FeedItemUi.Single -> VARIANT_SINGLE
        is FeedItemUi.ReplyCluster -> VARIANT_CLUSTER
        is FeedItemUi.SelfThreadChain -> VARIANT_CHAIN
        is FeedItemUi.Blocked -> VARIANT_BLOCKED
        is FeedItemUi.NotFound -> VARIANT_NOT_FOUND
    }
