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
}
