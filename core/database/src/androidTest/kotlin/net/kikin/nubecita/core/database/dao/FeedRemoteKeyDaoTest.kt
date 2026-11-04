package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FeedRemoteKeyDaoTest : DatabaseTest() {
    private lateinit var dao: FeedRemoteKeyDao

    @Before
    fun resolveDao() {
        dao = db.feedRemoteKeyDao()
    }

    private fun key(
        account: String = ACCOUNT,
        type: String = TYPE,
        uri: String = FEED_URI,
        cursor: String?,
    ) = FeedRemoteKeyEntity(account, type, uri, cursor)

    @Test
    fun remoteKey_nullWhenAbsent() =
        runTest {
            assertNull(dao.remoteKey(ACCOUNT, TYPE, FEED_URI))
        }

    @Test
    fun upsert_thenRead_roundTrips() =
        runTest {
            dao.upsert(key(cursor = "cursor-1"))
            assertEquals("cursor-1", dao.remoteKey(ACCOUNT, TYPE, FEED_URI)?.nextCursor)
        }

    @Test
    fun upsert_overwritesCursorForSamePartition() =
        runTest {
            dao.upsert(key(cursor = "cursor-1"))
            dao.upsert(key(cursor = "cursor-2"))
            assertEquals("cursor-2", dao.remoteKey(ACCOUNT, TYPE, FEED_URI)?.nextCursor)
        }

    @Test
    fun nullCursor_roundTrips() =
        runTest {
            dao.upsert(key(cursor = null))
            assertNull(dao.remoteKey(ACCOUNT, TYPE, FEED_URI)?.nextCursor)
        }

    @Test
    fun clearAccount_removesOnlyThatDid() =
        runTest {
            dao.upsert(key(cursor = "a"))
            dao.upsert(key(account = "did:other", cursor = "b"))
            dao.clearAccount(ACCOUNT)
            assertNull(dao.remoteKey(ACCOUNT, TYPE, FEED_URI))
            assertEquals("b", dao.remoteKey("did:other", TYPE, FEED_URI)?.nextCursor)
        }

    private companion object {
        const val ACCOUNT = "did:me"
        const val TYPE = "FOLLOWING"
        const val FEED_URI = ""
    }

    @Test
    fun prependCursors_roundTrip() =
        runTest {
            dao.upsert(key(cursor = "append-1"))

            dao.updatePrependCursors(
                ACCOUNT,
                TYPE,
                FEED_URI,
                boundarySince = "boundary-1",
                fillCursor = "fill-1",
                pendingBoundary = "pending-1",
            )

            val row = dao.remoteKey(ACCOUNT, TYPE, FEED_URI)
            assertEquals("boundary-1", row?.boundarySince)
            assertEquals("fill-1", row?.fillCursor)
            assertEquals("pending-1", row?.pendingBoundary)
        }

    @Test
    fun aPrependWriteLeavesTheAppendCursorAlone() =
        runTest {
            // The whole reason these are targeted updates rather than a
            // whole-row upsert. A prepend that wiped next_cursor would silently
            // break forward pagination, and a test that only checked its own
            // direction would never see it.
            dao.upsert(key(cursor = "append-1"))

            dao.updatePrependCursors(ACCOUNT, TYPE, FEED_URI, "boundary-1", "fill-1", "pending-1")

            assertEquals("append-1", dao.remoteKey(ACCOUNT, TYPE, FEED_URI)?.nextCursor)
        }

    @Test
    fun anAppendWriteLeavesThePrependCursorsAlone() =
        runTest {
            dao.upsert(key(cursor = "append-1"))
            dao.updatePrependCursors(ACCOUNT, TYPE, FEED_URI, "boundary-1", "fill-1", "pending-1")

            dao.updateAppendCursor(ACCOUNT, TYPE, FEED_URI, nextCursor = "append-2")

            val row = dao.remoteKey(ACCOUNT, TYPE, FEED_URI)
            assertEquals("append-2", row?.nextCursor)
            assertEquals("boundary-1", row?.boundarySince)
            assertEquals("fill-1", row?.fillCursor)
            assertEquals("pending-1", row?.pendingBoundary)
        }

    @Test
    fun aRefreshUpsertResetsEveryCursor() =
        runTest {
            // REFRESH legitimately starts over, so the whole-row write is right
            // here: the partition it described no longer exists.
            dao.upsert(key(cursor = "append-1"))
            dao.updatePrependCursors(ACCOUNT, TYPE, FEED_URI, "boundary-1", "fill-1", "pending-1")

            dao.upsert(key(cursor = "append-fresh"))

            val row = dao.remoteKey(ACCOUNT, TYPE, FEED_URI)
            assertEquals("append-fresh", row?.nextCursor)
            assertNull(row?.boundarySince)
            assertNull(row?.fillCursor)
            assertNull(row?.pendingBoundary)
        }

    @Test
    fun cursorUpdatesAreScopedToOnePartition() =
        runTest {
            dao.upsert(key(cursor = "a"))
            dao.upsert(key(type = "DISCOVER", uri = "at://feed/x", cursor = "b"))

            dao.updatePrependCursors(ACCOUNT, TYPE, FEED_URI, "boundary-1", null, null)

            assertEquals("boundary-1", dao.remoteKey(ACCOUNT, TYPE, FEED_URI)?.boundarySince)
            assertNull(dao.remoteKey(ACCOUNT, "DISCOVER", "at://feed/x")?.boundarySince)
        }
}
