package net.kikin.nubecita.core.database.dao

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.CachedPostEntity
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
    fun upsert_overwritesSamePositionRow() =
        runTest {
            upsertEntries(listOf(post(position = 0, postUri = "at://old")))
            upsertEntries(listOf(post(position = 0, postUri = "at://new")))
            val rows = dao.head(ACCOUNT, TYPE, FEED_URI, 10).first()
            assertEquals(1, rows.size)
            assertEquals("at://new", rows.first().uri)
        }

    @Test
    fun trimToCap_keepsNewestCap() =
        runTest {
            upsertEntries((0 until 10).map { post(position = it) })
            dao.trimToCap(ACCOUNT, TYPE, FEED_URI, cap = 3)
            // Newest = lowest position (position 0 = top of the newest page).
            // cap = 3 keeps the newest three: positions 0, 1, 2.
            assertEquals(listOf(0, 1, 2), dao.head(ACCOUNT, TYPE, FEED_URI, 100).first().map { it.position })
        }

    @Test
    fun trimToCap_noOpWhenUnderCap() =
        runTest {
            upsertEntries((0 until 3).map { post(position = it) })
            dao.trimToCap(ACCOUNT, TYPE, FEED_URI, cap = 10)
            assertEquals(3, dao.head(ACCOUNT, TYPE, FEED_URI, 100).first().size)
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
