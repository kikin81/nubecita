package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.FeedPartitionStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FeedPartitionStateDaoTest : DatabaseTest() {
    private lateinit var dao: FeedPartitionStateDao

    @Before
    fun resolveDao() {
        dao = db.feedPartitionStateDao()
    }

    @Test
    fun anchorRoundTrips() =
        runTest {
            dao.saveAnchor(ACCOUNT, TYPE, FEED_URI, itemKey = "chain:at://post/a", position = 42, offset = -17)

            val state = dao.state(ACCOUNT, TYPE, FEED_URI)

            assertEquals("chain:at://post/a", state?.anchorItemKey)
            assertEquals(42, state?.anchorPosition)
            // A negative offset is ordinary: the row is scrolled partly above
            // the viewport, which is where a reader usually stops.
            assertEquals(-17, state?.anchorOffset)
        }

    @Test
    fun anchorSavesOnAPartitionWithNoRowYet() =
        runTest {
            // The reader can scroll a partition whose projection fingerprint was
            // never written. An UPDATE alone would silently do nothing here and
            // the anchor would never be saved — the whole feature, lost to a
            // no-op write.
            assertNull(dao.state(ACCOUNT, TYPE, FEED_URI))

            dao.saveAnchor(ACCOUNT, TYPE, FEED_URI, itemKey = "single:at://post/b", position = 3, offset = 0)

            assertEquals("single:at://post/b", dao.state(ACCOUNT, TYPE, FEED_URI)?.anchorItemKey)
        }

    @Test
    fun anchorAndFingerprintDoNotClobberEachOther() =
        runTest {
            // Both writes are targeted for this reason: the row carries two
            // independent concerns, and a whole-row write from either would
            // erase the other's. A lost fingerprint means a stale projection
            // never re-projects; a lost anchor means the reader loses their
            // place.
            dao.upsert(FeedPartitionStateEntity(ACCOUNT, TYPE, FEED_URI, prefsFingerprint = "replies=false"))
            dao.saveAnchor(ACCOUNT, TYPE, FEED_URI, itemKey = "single:at://post/c", position = 9, offset = 4)
            dao.updatePrefsFingerprint(ACCOUNT, TYPE, FEED_URI, fingerprint = "replies=true")

            val state = dao.state(ACCOUNT, TYPE, FEED_URI)

            assertEquals("replies=true", state?.prefsFingerprint)
            assertEquals("single:at://post/c", state?.anchorItemKey)
            assertEquals(9, state?.anchorPosition)
        }

    @Test
    fun clearAnchorLeavesTheRestOfTheRow() =
        runTest {
            // What the destructive refresh does (D8): the reader asked for a
            // clean timeline, so restoring them into the old one would be the
            // opposite of what the gesture meant.
            dao.upsert(FeedPartitionStateEntity(ACCOUNT, TYPE, FEED_URI, prefsFingerprint = "replies=false"))
            dao.saveAnchor(ACCOUNT, TYPE, FEED_URI, itemKey = "single:at://post/d", position = 2, offset = 1)

            dao.clearAnchor(ACCOUNT, TYPE, FEED_URI)

            val state = dao.state(ACCOUNT, TYPE, FEED_URI)
            assertNull(state?.anchorItemKey)
            assertNull(state?.anchorPosition)
            assertNull(state?.anchorOffset)
            assertEquals("replies=false", state?.prefsFingerprint)
        }

    @Test
    fun anchorIsScopedToItsPartition() =
        runTest {
            dao.saveAnchor(ACCOUNT, TYPE, FEED_URI, itemKey = "single:at://post/e", position = 1, offset = 0)
            dao.saveAnchor(ACCOUNT, "DISCOVER", "at://feed/x", itemKey = "single:at://post/f", position = 7, offset = 0)

            assertEquals("single:at://post/e", dao.state(ACCOUNT, TYPE, FEED_URI)?.anchorItemKey)
            assertEquals("single:at://post/f", dao.state(ACCOUNT, "DISCOVER", "at://feed/x")?.anchorItemKey)
            // And not readable from another account's partition of the same feed.
            assertNull(dao.state("did:plc:other", TYPE, FEED_URI))
        }

    @Test
    fun signOutPurgesTheAnchorWithTheAccount() =
        runTest {
            // The reason the anchor lives beside the cache rather than in
            // DataStore (D6): one delete, no orphan, no second lifecycle.
            dao.saveAnchor(ACCOUNT, TYPE, FEED_URI, itemKey = "single:at://post/g", position = 5, offset = 0)
            dao.saveAnchor("did:plc:other", TYPE, FEED_URI, itemKey = "single:at://post/h", position = 6, offset = 0)

            dao.clearAccount(ACCOUNT)

            assertNull(dao.state(ACCOUNT, TYPE, FEED_URI))
            assertEquals("single:at://post/h", dao.state("did:plc:other", TYPE, FEED_URI)?.anchorItemKey)
        }

    private companion object {
        const val ACCOUNT = "did:plc:me"
        const val TYPE = "FOLLOWING"
        const val FEED_URI = ""
    }
}
