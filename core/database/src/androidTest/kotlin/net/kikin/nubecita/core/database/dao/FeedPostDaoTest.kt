package net.kikin.nubecita.core.database.dao

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.FeedPostEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
internal class FeedPostDaoTest : DatabaseTest() {
    private lateinit var dao: FeedPostDao

    @Before
    fun resolveDao() {
        dao = db.feedPostDao()
    }

    private fun post(
        account: String = ACCOUNT,
        type: String = TYPE,
        uri: String = FEED_URI,
        position: Int,
        postUri: String = "at://post/$position",
        author: String = "did:author",
    ) = FeedPostEntity(
        accountDid = account,
        feedType = type,
        feedUri = uri,
        position = position,
        uri = postUri,
        cid = "cid-$position",
        authorDid = author,
        indexedAt = Instant.fromEpochMilliseconds(position.toLong()),
        text = "post $position",
        postBlob = null,
    )

    @Test
    fun head_ordersByPosition_andLimits() =
        runTest {
            dao.upsert(listOf(post(position = 2), post(position = 0), post(position = 1)))
            dao.head(ACCOUNT, TYPE, FEED_URI, limit = 2).test {
                assertEquals(listOf(0, 1), awaitItem().map { it.position })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun head_scopedToPartition() =
        runTest {
            dao.upsert(
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
            dao.upsert(listOf(post(position = 0), post(position = 5), post(position = 3)))
            assertEquals(5, dao.maxPosition(ACCOUNT, TYPE, FEED_URI))
        }

    @Test
    fun upsert_overwritesSamePositionRow() =
        runTest {
            dao.upsert(listOf(post(position = 0, postUri = "at://old")))
            dao.upsert(listOf(post(position = 0, postUri = "at://new")))
            val rows = dao.head(ACCOUNT, TYPE, FEED_URI, 10).first()
            assertEquals(1, rows.size)
            assertEquals("at://new", rows.first().uri)
        }

    @Test
    fun trimToCap_keepsNewestCap() =
        runTest {
            dao.upsert((0 until 10).map { post(position = it) })
            dao.trimToCap(ACCOUNT, TYPE, FEED_URI, cap = 3)
            // Newest = lowest position (position 0 = top of the newest page).
            // cap = 3 keeps the newest three: positions 0, 1, 2.
            assertEquals(listOf(0, 1, 2), dao.head(ACCOUNT, TYPE, FEED_URI, 100).first().map { it.position })
        }

    @Test
    fun trimToCap_noOpWhenUnderCap() =
        runTest {
            dao.upsert((0 until 3).map { post(position = it) })
            dao.trimToCap(ACCOUNT, TYPE, FEED_URI, cap = 10)
            assertEquals(3, dao.head(ACCOUNT, TYPE, FEED_URI, 100).first().size)
        }

    @Test
    fun clearPartition_scopedToPartition() =
        runTest {
            dao.upsert(
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
            dao.upsert(
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
            dao.upsert(listOf(post(position = 1), post(position = 0)))
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
