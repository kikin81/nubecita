package net.kikin.nubecita.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity
import net.kikin.nubecita.core.database.model.FeedPostEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The regression this epic exists to prevent, against real SQL: a background
 * refresh that lands while the reader is away must leave the row their anchor
 * points at present and resolvable.
 *
 * Two layers cover that claim, and this is the lower one. The repository's unit
 * tests cover the *choice* — which cut-off the trim picks, and that an anchor
 * extends it — against stubbed positions. What they cannot see is whether the
 * SQL composes: `LIMIT 1 OFFSET n` over a gapped position range, `>` versus
 * `>=` in the delete, and the anchor-extended cut applied to rows that a
 * prepend wrote at negative positions. Those are exactly the mistakes a mocked
 * DAO cannot catch, so they are driven here through the real database.
 *
 * The additive write is reproduced at the DAO level rather than by running
 * `FeedCatchUp`, which would need a network. What matters for this property is
 * the shape of what it writes — entries and projection rows above the existing
 * minimum, nothing renumbered — and that shape is asserted in
 * `FeedRemoteMediatorTest` against the real allocation.
 */
@RunWith(AndroidJUnit4::class)
internal class FeedPositionSurvivesRefreshTest : DatabaseTest() {
    private val anchorKey = "single:at://post/5"

    @Test
    fun aBackgroundCatchUpAndTrimLeaveTheAnchorResolvable() =
        runTest {
            seedWarmPartition(entries = 10)
            assertEquals(5, db.feedItemDao().positionOf(ACCOUNT, TYPE, FEED_URI, anchorKey))

            // What the worker's catch-up writes: a slice at the top of the
            // reserved window, leaving the gap up to the existing head empty.
            catchUp(base = -250, count = 3)

            // And what the worker runs next, with a cap small enough that the
            // reader's backlog is outside it. Without the anchor extension this
            // keeps positions -250..-248 and 0 and deletes the rest — the
            // anchor among them, while the app is backgrounded.
            trim(cap = 4, anchorItemPosition = 5)

            assertEquals(
                "the anchored item must still resolve, at the same position",
                5,
                db.feedItemDao().positionOf(ACCOUNT, TYPE, FEED_URI, anchorKey),
            )
            assertNotNull(
                "the anchored item's own entry must survive too, or a re-projection cannot rebuild it",
                db.feedPostDao().entriesIn(ACCOUNT, TYPE, FEED_URI).firstOrNull { it.position == 5 },
            )
            // The whole backlog above the anchor, not just the anchor itself.
            assertEquals(
                listOf(-250, -249, -248, 0, 1, 2, 3, 4, 5),
                db.feedItemDao().itemsIn(ACCOUNT, TYPE, FEED_URI).map { it.position },
            )
        }

    @Test
    fun withNoAnchorTheTrimKeepsExactlyTheCapInRows() =
        runTest {
            // The same gapped range, counted rather than measured in position
            // values. `MIN(position) + cap` would have kept the three prepended
            // rows and one more — four rows for a cap of six.
            seedWarmPartition(entries = 10)
            catchUp(base = -250, count = 3)

            trim(cap = 6, anchorItemPosition = null)

            assertEquals(
                listOf(-250, -249, -248, 0, 1, 2),
                db.feedItemDao().itemsIn(ACCOUNT, TYPE, FEED_URI).map { it.position },
            )
            // Members go with their items, or they pin posts against eviction.
            assertEquals(
                emptyList<Int>(),
                db
                    .feedItemDao()
                    .membersOfAll(ACCOUNT, TYPE, FEED_URI, positions = listOf(3, 4, 5))
                    .map { it.position },
            )
        }

    /**
     * A partition as a reader left it: entries at 0..n-1 with one projection
     * item each, and every post stored, since the read path inner-joins.
     */
    private suspend fun seedWarmPartition(entries: Int) {
        db.cachedPostDao().upsert(
            (0 until entries).map { CachedPostEntity(accountDid = ACCOUNT, uri = "at://post/$it", postBlob = "{}") },
        )
        db.feedPostDao().upsert((0 until entries).map { entry(position = it, uri = "at://post/$it") })
        db.feedItemDao().upsert((0 until entries).map { item(position = it, uri = "at://post/$it") })
        db.feedItemDao().upsertMembers(
            (0 until entries).map { FeedItemMemberEntity(ACCOUNT, TYPE, FEED_URI, it, 0, "at://post/$it") },
        )
    }

    /** The additive write: [count] newer entries from [base], nothing renumbered. */
    private suspend fun catchUp(
        base: Int,
        count: Int,
    ) {
        val uris = (0 until count).map { "at://new/$it" }
        db.cachedPostDao().upsert(uris.map { CachedPostEntity(accountDid = ACCOUNT, uri = it, postBlob = "{}") })
        db.feedPostDao().upsert(uris.mapIndexed { index, uri -> entry(position = base + index, uri = uri) })
        db.feedItemDao().upsert(uris.mapIndexed { index, uri -> item(position = base + index, uri = uri) })
        db.feedItemDao().upsertMembers(
            uris.mapIndexed { index, uri -> FeedItemMemberEntity(ACCOUNT, TYPE, FEED_URI, base + index, 0, uri) },
        )
    }

    /**
     * The repository's trim, as it composes the DAO's primitives: the newest
     * [cap] rows by ordinal, extended to [anchorItemPosition] when the reader
     * is deeper than that.
     */
    private suspend fun trim(
        cap: Int,
        anchorItemPosition: Int?,
    ) {
        val itemDao = db.feedItemDao()
        val capCut = itemDao.positionAtOffsetFromTop(ACCOUNT, TYPE, FEED_URI, offset = cap - 1) ?: return
        val cut = if (anchorItemPosition != null && anchorItemPosition > capCut) anchorItemPosition else capCut
        itemDao.deleteOlderThan(ACCOUNT, TYPE, FEED_URI, cut)

        val postDao = db.feedPostDao()
        val referenced = postDao.oldestReferencedByProjection(ACCOUNT, TYPE, FEED_URI)
        val entryCap = postDao.positionAtOffsetFromTop(ACCOUNT, TYPE, FEED_URI, offset = cap - 1)
        if (entryCap != null) {
            val entryCut = if (referenced != null && referenced > entryCap) referenced else entryCap
            postDao.deleteOlderThan(ACCOUNT, TYPE, FEED_URI, entryCut)
        }
        db.cachedPostDao().deleteUnreferenced(ACCOUNT)
    }

    private fun entry(
        position: Int,
        uri: String,
    ) = FeedPostEntity(
        accountDid = ACCOUNT,
        feedType = TYPE,
        feedUri = FEED_URI,
        position = position,
        uri = uri,
    )

    private fun item(
        position: Int,
        uri: String,
    ) = FeedItemEntity(
        accountDid = ACCOUNT,
        feedType = TYPE,
        feedUri = FEED_URI,
        position = position,
        rowType = FeedItemEntity.ROW_TYPE_ITEM,
        variant = "single",
        itemKey = "single:$uri",
        leafUri = uri,
        suppressedReplyCount = 0,
        threadRootUri = uri,
    )

    private companion object {
        const val ACCOUNT = "did:plc:me"
        const val TYPE = "FOLLOWING"
        const val FEED_URI = ""
    }
}
