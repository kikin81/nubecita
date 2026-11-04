package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPartitionStateDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity
import net.kikin.nubecita.core.database.model.FeedPartitionStateEntity
import net.kikin.nubecita.core.feedmapping.dedupeByLeafPost
import net.kikin.nubecita.core.feedmapping.dedupeByThreadRoot
import net.kikin.nubecita.core.feedmapping.dedupeClusterContext
import net.kikin.nubecita.core.feedmapping.mergeChainBoundary
import net.kikin.nubecita.core.feedmapping.shouldDisplayInFollowingFeed
import net.kikin.nubecita.core.feedmapping.threadRootId
import net.kikin.nubecita.core.feedmapping.toFeedItemsUi
import net.kikin.nubecita.core.feeds.FeedViewPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.FeedViewPrefs
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
 * - [writePrepend] lands above what is already rendered and seeds **neither**
 *   parameter, so a thread resurfaces with its newer reply above the reader
 *   rather than being suppressed by an occurrence they already scrolled past.
 *   It also does not merge across the seam: the entries below it are older
 *   than the reader's last session, not the continuation of this page.
 *
 * Callers run this inside the same transaction as the entry and post writes, so
 * no reader observes a partition whose projection disagrees with its rows.
 */
internal class FeedProjectionWriter
    @Inject
    constructor(
        private val feedItemDao: FeedItemDao,
        private val partitionStateDao: FeedPartitionStateDao,
        private val cachedPostDao: CachedPostDao,
        private val feedPostDao: FeedPostDao,
        private val sessionStateProvider: SessionStateProvider,
        private val moderationPreferences: ModerationPreferencesRepository,
        private val feedViewPreferences: FeedViewPreferencesRepository,
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

        private fun feedViewPrefs(): FeedViewPrefs = feedViewPreferences.prefs.value

        /**
         * The page as the viewer's feed-view preferences leave it.
         *
         * Applied to the **wire entries, before projection**, because the
         * predicate reads `reply.root` / `reply.parent` and the repost reason —
         * context the projection consumes and does not carry forward. Filtering
         * after projection would have nothing left to decide on.
         *
         * Follow-scoped feeds only. A generator already curates its own output,
         * so applying reply tuners to Discover or a custom feed would strip out
         * most of what the algorithm deliberately selected — which is what the
         * official client does, and what [shouldDisplayInFollowingFeed]'s own
         * documentation requires.
         */
        private fun List<FeedViewPost>.filteredForFeed(
            feedKey: FeedKey,
            viewerDid: String?,
        ): List<FeedViewPost> {
            if (!feedKey.feedType.isFollowScoped) return this
            val viewPrefs = feedViewPrefs()
            return filter { it.shouldDisplayInFollowingFeed(viewPrefs, viewerDid) }
        }

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

        /**
         * Rebuilds [feedKey]'s projection from a REFRESH page.
         *
         * Returns the number of items written, which is **not** the page size:
         * filtering and de-duplication can reduce a full page to nothing, and
         * the caller needs to know that to decide whether to fetch further.
         */
        suspend fun writeRefresh(
            feedKey: FeedKey,
            page: List<FeedViewPost>,
            viewerDid: String?,
            prefs: ModerationPrefs,
        ): Int {
            feedItemDao.clearPartition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
            // The dismissal mark is a position, and this path restarts
            // positions at 0 — so a mark left from before the rebuild points
            // nowhere. Left in place it silently outranks every later prepend:
            // a reader who dismissed a pill at -250 and then pulled to refresh
            // would never see another, because nothing is written below -250
            // again (nubecita-3zol). `reproject` keeps its mark on purpose: it
            // rebuilds from the partition's existing lowest position.
            partitionStateDao.updatePillDismissed(
                accountDid = feedKey.accountDid,
                feedType = feedKey.feedType.name,
                feedUri = feedKey.feedUri,
                position = null,
            )
            val items =
                page
                    .filteredForFeed(feedKey, viewerDid)
                    .toFeedItemsUi(prefs, viewerDid)
                    .dedupeByThreadRoot()
                    .dedupeClusterContext()
                    // Nothing above a refresh to seed from — it rebuilt the
                    // partition — but the pass still runs: two reposts of one
                    // post arrive in a single page, and the thread-root pass
                    // exempts reposts from its drop.
                    .dedupeByLeafPost()
            write(feedKey, items, basePosition = 0)
            recordFingerprint(feedKey)
            return items.size
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
        ): Int {
            val projected = page.filteredForFeed(feedKey, viewerDid).toFeedItemsUi(prefs, viewerDid)
            if (projected.isEmpty()) return 0

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
            // toHashSet, not toSet: these are membership tests, and toSet
            // builds a LinkedHashSet to preserve an insertion order nothing
            // here reads.
            val seenRoots =
                feedItemDao
                    .seenThreadRoots(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                    .toHashSet()
            val visible =
                feedItemDao
                    .renderedPostUris(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                    .toHashSet()

            val items =
                merged
                    .dedupeByThreadRoot(seenRoots = seenRoots, visibleFromAbove = visible)
                    .dedupeClusterContext()
                    .dedupeByLeafPost(visibleFromAbove = renderedLeaves(feedKey))

            val base = (feedItemDao.maxPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) ?: -1) + 1
            write(feedKey, items, basePosition = base)
            recordFingerprint(feedKey)
            return items.size
        }

        /** [writePrepend] with the current viewer and preferences. */
        suspend fun writePrepend(
            feedKey: FeedKey,
            page: List<FeedViewPost>,
            basePosition: Int,
        ) = writePrepend(feedKey, page, basePosition, viewerDid(), prefs())

        /**
         * Writes a catch-up page's projection above what is already there, at
         * positions the caller allocated.
         *
         * [basePosition] is passed in rather than derived because the prepend's
         * allocation is a partition-wide decision: the entry rows and these
         * projection rows share one reserved window, so only the mediator —
         * which knows how much of the window the fill has already used — can
         * say where this page starts. A block of items is never longer than the
         * block of entries it was projected from, so it always fits inside the
         * slice the entries took.
         *
         * No seeding and no chain merge (D7). The partition below the seam was
         * fetched in an earlier session, so an occurrence down there is
         * something the reader has already scrolled past — suppressing the new
         * reply on its account would mean the thread never resurfaces, which is
         * the regression persisting the projection would otherwise introduce.
         * De-duplication within the page still applies.
         */
        suspend fun writePrepend(
            feedKey: FeedKey,
            page: List<FeedViewPost>,
            basePosition: Int,
            viewerDid: String?,
            prefs: ModerationPrefs,
        ): Int {
            val items =
                page
                    .filteredForFeed(feedKey, viewerDid)
                    .toFeedItemsUi(prefs, viewerDid)
                    .dedupeByThreadRoot()
                    .dedupeClusterContext()
                    // Seeded even here, where the thread-root pass is not.
                    // D7's prepend exemption is about a thread resurfacing with
                    // a newer reply, not about the same post being drawn twice
                    // — and a `since`-bounded catch-up overlaps the cached head
                    // by design, so the overlap would render again and, where
                    // the key matches too, throw (D29).
                    .dedupeByLeafPost(visibleFromAbove = renderedLeaves(feedKey))
            if (items.isEmpty()) return 0
            write(feedKey, items, basePosition = basePosition)
            recordFingerprint(feedKey)
            return items.size
        }

        /**
         * Recomputes the whole partition's projection from stored rows, with no
         * network.
         *
         * This is what a preference change costs: one local pass, and the
         * reader keeps their place. The alternative — refetching — applies the
         * preference just as correctly and throws away the position this cache
         * exists to preserve, which is the opposite of the point.
         *
         * The wire entries are rebuilt from the envelope's context blob plus
         * the stored posts, so the grouping rules see what the server actually
         * sent rather than a reconstruction from denormalized columns.
         *
         * Positions restart from the partition's lowest existing position, so a
         * prepend window above zero is preserved and ordering is unchanged.
         * Items are keyed by content, so an anchor pointing at a surviving item
         * still resolves even though its position may have shifted.
         */
        suspend fun reproject(feedKey: FeedKey) {
            val envelopes = feedPostDao.entriesIn(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
            if (envelopes.isEmpty()) return

            val postsByUri =
                cachedPostDao
                    .byUris(feedKey.accountDid, envelopes.map { it.uri })
                    .associateBy { it.uri }
            val page = envelopes.mapNotNull { it.toFeedViewPostOrNull(postsByUri[it.uri]) }
            if (page.isEmpty()) return

            val viewerDid = viewerDid()
            val items =
                page
                    .filteredForFeed(feedKey, viewerDid)
                    .toFeedItemsUi(prefs(), viewerDid)
                    .dedupeByThreadRoot()
                    .dedupeClusterContext()
                    // Rebuilt from the whole partition at once, so there is
                    // nothing above it to seed from.
                    .dedupeByLeafPost()

            feedItemDao.clearPartition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
            write(feedKey, items, basePosition = envelopes.first().position)
            recordFingerprint(feedKey)
        }

        /**
         * Stores the preferences this projection was computed with.
         *
         * Written on every projection, including an append: the partition's
         * projection is only coherent if every row in it was produced under the
         * same preferences, so the fingerprint describes the partition rather
         * than the page.
         */
        private suspend fun recordFingerprint(feedKey: FeedKey) {
            val fingerprint = projectionPrefsFingerprint(prefs(), feedViewPrefs())
            partitionStateDao.upsert(
                FeedPartitionStateEntity(
                    accountDid = feedKey.accountDid,
                    feedType = feedKey.feedType.name,
                    feedUri = feedKey.feedUri,
                    prefsFingerprint = fingerprint,
                ),
            )
        }

        /**
         * The leaf posts already rendered in this partition.
         *
         * Leaves, not every rendered post: a `Single` whose post appears above
         * only as a cluster's context belongs to the cluster-context pass,
         * which keeps it when it is a repost.
         */
        private suspend fun renderedLeaves(feedKey: FeedKey): Set<String> =
            feedItemDao
                .renderedLeafUris(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                .toHashSet()

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
