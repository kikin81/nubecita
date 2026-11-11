package net.kikin.nubecita.core.database.dao

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity
import net.kikin.nubecita.core.database.model.FeedPostEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FeedPostDaoTest : DatabaseTest() {
    private lateinit var dao: FeedPostDao
    private lateinit var cachedDao: CachedPostDao

    @Before
    fun resolveDao() {
        dao = db.feedPostDao()
        cachedDao = db.cachedPostDao()
    }

    /**
     * Writes the entries **and** the posts they reference.
     *
     * The read path inner-joins `cached_post`, so an envelope on its own is
     * invisible — seeding both is what production does in one transaction, and
     * a test that wrote only envelopes would assert against an empty read and
     * look like a query bug.
     */
    private suspend fun upsertEntries(entries: List<FeedPostEntity>) {
        cachedDao.upsert(
            entries
                .distinctBy { it.accountDid to it.uri }
                .map { CachedPostEntity(accountDid = it.accountDid, uri = it.uri, postBlob = "{}") },
        )
        dao.upsert(entries)
    }

    private fun post(
        account: String = ACCOUNT,
        type: String = TYPE,
        uri: String = FEED_URI,
        position: Int,
        postUri: String = "at://post/$position",
        repostedBy: String? = null,
        repostedLabel: String? = null,
        replyRoot: String? = null,
        replyParent: String? = null,
    ) = FeedPostEntity(
        accountDid = account,
        feedType = type,
        feedUri = uri,
        position = position,
        uri = postUri,
        repostedByDid = repostedBy,
        repostedByLabel = repostedLabel,
        replyRootUri = replyRoot,
        replyParentUri = replyParent,
    )

    @Test
    fun entryColumns_roundTripThroughRoom() =
        runTest {
            upsertEntries(
                listOf(
                    post(
                        position = 0,
                        repostedBy = "did:reposter",
                        repostedLabel = "Reposter",
                        replyRoot = "at://post/root",
                        replyParent = "at://post/parent",
                    ),
                ),
            )
            val row = dao.head(ACCOUNT, TYPE, FEED_URI, 1).first().single()
            assertEquals("Reposter", row.repostedByLabel)
            assertEquals("at://post/root", row.replyRootUri)
            assertEquals("at://post/parent", row.replyParentUri)
            assertEquals("{}", row.post.postBlob)
        }

    @Test
    fun entryColumns_defaultToNull() =
        runTest {
            upsertEntries(listOf(post(position = 0)))
            val row = dao.head(ACCOUNT, TYPE, FEED_URI, 1).first().single()
            assertNull(row.repostedByLabel)
            assertNull(row.replyRootUri)
            assertNull(row.replyParentUri)
        }

    @Test
    fun anEnvelopeWithNoStoredPostIsAbsentFromTheRead() =
        runTest {
            // The join is INNER on purpose: a half-row has nothing renderable,
            // and production writes both grains in one transaction so this state
            // should not arise. Better absent than surfaced as a blank card.
            dao.upsert(listOf(post(position = 0, postUri = "at://post/orphan")))
            assertEquals(0, dao.head(ACCOUNT, TYPE, FEED_URI, 10).first().size)
            // The envelope is still there — only the joined read skips it.
            assertEquals(0, dao.maxPosition(ACCOUNT, TYPE, FEED_URI))
        }

    @Test
    fun entryColumns_scopedToPartition() =
        runTest {
            upsertEntries(
                listOf(
                    post(position = 0, repostedBy = "did:mine"),
                    post(
                        type = "DISCOVER",
                        uri = "at://feed/x",
                        position = 0,
                        postUri = "at://other/0",
                        repostedBy = "did:theirs",
                    ),
                ),
            )
            assertEquals(
                "did:mine",
                dao
                    .head(ACCOUNT, TYPE, FEED_URI, 10)
                    .first()
                    .single()
                    .repostedByDid,
            )
            assertEquals(
                "did:theirs",
                dao
                    .head(ACCOUNT, "DISCOVER", "at://feed/x", 10)
                    .first()
                    .single()
                    .repostedByDid,
            )
        }

    @Test
    fun negativePositions_sortNewestFirst() =
        runTest {
            // A prepend writes above the existing rows without renumbering them,
            // so ascending position (newest-first) must still hold across zero.
            upsertEntries(listOf(post(position = 1), post(position = -2), post(position = 0), post(position = -1)))
            assertEquals(
                listOf(-2, -1, 0, 1),
                dao.head(ACCOUNT, TYPE, FEED_URI, 10).first().map { it.position },
            )
            assertEquals(1, dao.maxPosition(ACCOUNT, TYPE, FEED_URI))
        }

    @Test
    fun head_ordersByPosition_andLimits() =
        runTest {
            upsertEntries(listOf(post(position = 2), post(position = 0), post(position = 1)))
            dao.head(ACCOUNT, TYPE, FEED_URI, limit = 2).test {
                assertEquals(listOf(0, 1), awaitItem().map { it.position })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun head_scopedToPartition() =
        runTest {
            upsertEntries(
                listOf(
                    post(position = 0),
                    post(type = "DISCOVER", uri = "at://feed/x", position = 0, postUri = "at://other/0"),
                ),
            )
            assertEquals(1, dao.head(ACCOUNT, TYPE, FEED_URI, 10).first().size)
        }

    @Test
    fun maxPosition_returnsHighest_orNullWhenEmpty() =
        runTest {
            assertNull(dao.maxPosition(ACCOUNT, TYPE, FEED_URI))
            upsertEntries(listOf(post(position = 0), post(position = 5), post(position = 3)))
            assertEquals(5, dao.maxPosition(ACCOUNT, TYPE, FEED_URI))
        }

    @Test
    fun countInRange_countsEntriesInAHalfOpenRange() =
        runTest {
            // Sizes a completed catch-up window for analytics: [from, until).
            upsertEntries(listOf(post(position = -250), post(position = -200), post(position = -2), post(position = -1), post(position = 0)))

            assertEquals(3, dao.countInRange(ACCOUNT, TYPE, FEED_URI, from = -250, until = -1))
            assertEquals(0, dao.countInRange(ACCOUNT, TYPE, FEED_URI, from = -199, until = -2))
        }

    @Test
    fun minPosition_returnsNewest_orNullWhenEmpty() =
        runTest {
            assertNull(dao.minPosition(ACCOUNT, TYPE, FEED_URI))
            upsertEntries(listOf(post(position = 0), post(position = -7), post(position = 3)))
            // Newest = lowest, and a prepend puts it below zero.
            assertEquals(-7, dao.minPosition(ACCOUNT, TYPE, FEED_URI))
        }

    @Test
    fun firstPositionAtOrAbove_boundsTheRemainingWindow() =
        runTest {
            // The prepend's room check. From -250 the next occupied row is
            // -249, so one slot is free; from -248 it is 0, so the window
            // between them is what a resumed fill may still use.
            upsertEntries(listOf(post(position = -249), post(position = 0)))
            assertEquals(-249, dao.firstPositionAtOrAbove(ACCOUNT, TYPE, FEED_URI, from = -250))
            assertEquals(0, dao.firstPositionAtOrAbove(ACCOUNT, TYPE, FEED_URI, from = -248))
            assertNull(dao.firstPositionAtOrAbove(ACCOUNT, TYPE, FEED_URI, from = 1))
        }

    @Test
    fun upsert_overwritesSamePositionRow() =
        runTest {
            upsertEntries(listOf(post(position = 0, postUri = "at://old")))
            upsertEntries(listOf(post(position = 0, postUri = "at://new")))
            val rows = dao.head(ACCOUNT, TYPE, FEED_URI, 10).first()
            assertEquals(1, rows.size)
            assertEquals("at://new", rows.first().uri)
        }

    @Test
    fun trim_keepsTheNewestCapRows() =
        runTest {
            upsertEntries((0 until 10).map { post(position = it) })
            // Newest = lowest position (position 0 = top of the newest page).
            val cut = dao.positionAtOffsetFromTop(ACCOUNT, TYPE, FEED_URI, offset = 2)
            assertEquals(2, cut)
            dao.deleteOlderThan(ACCOUNT, TYPE, FEED_URI, position = 2)
            assertEquals(listOf(0, 1, 2), dao.head(ACCOUNT, TYPE, FEED_URI, 100).first().map { it.position })
        }

    @Test
    fun trim_countsRowsNotPositionValues() =
        runTest {
            // The case the old arithmetic got wrong. A prepend leaves the unused
            // end of its reserved window empty, so positions are gapped: here
            // -250, -249 and 0..4. `MIN(position) + cap` with cap = 3 would
            // delete from -247 and keep TWO rows, not three.
            upsertEntries(listOf(post(position = -250), post(position = -249)) + (0 until 5).map { post(position = it) })

            val cut = dao.positionAtOffsetFromTop(ACCOUNT, TYPE, FEED_URI, offset = 2)

            assertEquals(0, cut)
            dao.deleteOlderThan(ACCOUNT, TYPE, FEED_URI, position = requireNotNull(cut))
            assertEquals(listOf(-250, -249, 0), dao.head(ACCOUNT, TYPE, FEED_URI, 100).first().map { it.position })
        }

    @Test
    fun trim_cutOffIsNullUnderTheCap() =
        runTest {
            upsertEntries((0 until 3).map { post(position = it) })
            assertNull(dao.positionAtOffsetFromTop(ACCOUNT, TYPE, FEED_URI, offset = 9))
        }

    @Test
    fun oldestReferencedByProjection_tracksTheSurvivingItems() =
        runTest {
            // Entry positions and item positions are different coordinate
            // spaces, so the two grains are tied by content: whatever the
            // surviving projection still points at must stay, or a local
            // re-projection cannot rebuild it.
            upsertEntries((0 until 5).map { post(position = it) })
            db.feedItemDao().upsertMembers(
                listOf(
                    FeedItemMemberEntity(ACCOUNT, TYPE, FEED_URI, position = 0, ordinal = 0, uri = "at://post/0"),
                    FeedItemMemberEntity(ACCOUNT, TYPE, FEED_URI, position = 1, ordinal = 0, uri = "at://post/3"),
                ),
            )

            assertEquals(3, dao.oldestReferencedByProjection(ACCOUNT, TYPE, FEED_URI))
        }

    @Test
    fun oldestReferencedByProjection_isNullWithNoProjection() =
        runTest {
            upsertEntries((0 until 3).map { post(position = it) })
            assertNull(dao.oldestReferencedByProjection(ACCOUNT, TYPE, FEED_URI))
        }

    @Test
    fun clearPartition_scopedToPartition() =
        runTest {
            upsertEntries(
                listOf(
                    post(position = 0),
                    post(type = "DISCOVER", uri = "at://feed/x", position = 0, postUri = "at://other/0"),
                ),
            )
            dao.clearPartition(ACCOUNT, TYPE, FEED_URI)
            assertEquals(0, dao.head(ACCOUNT, TYPE, FEED_URI, 10).first().size)
            assertEquals(1, dao.head(ACCOUNT, "DISCOVER", "at://feed/x", 10).first().size)
        }

    @Test
    fun clearAccount_removesAllPartitionsForDid() =
        runTest {
            upsertEntries(
                listOf(
                    post(position = 0),
                    post(type = "DISCOVER", uri = "at://feed/x", position = 0, postUri = "at://o/0"),
                    post(account = "did:other", position = 0, postUri = "at://other-acct/0"),
                ),
            )
            dao.clearAccount(ACCOUNT)
            assertEquals(0, dao.head(ACCOUNT, TYPE, FEED_URI, 10).first().size)
            assertEquals(0, dao.head(ACCOUNT, "DISCOVER", "at://feed/x", 10).first().size)
            assertEquals(1, dao.head("did:other", TYPE, FEED_URI, 10).first().size)
        }

    @Test
    fun pagingSource_returnsRowsOrderedByPosition() =
        runTest {
            upsertEntries(listOf(post(position = 1), post(position = 0)))
            val source = dao.pagingSource(ACCOUNT, TYPE, FEED_URI)
            val result =
                source.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 10,
                        placeholdersEnabled = false,
                    ),
                )
            val page = result as PagingSource.LoadResult.Page
            assertEquals(listOf(0, 1), page.data.map { it.position })
        }

    private companion object {
        const val ACCOUNT = "did:me"
        const val TYPE = "FOLLOWING"
        const val FEED_URI = ""
    }
}
