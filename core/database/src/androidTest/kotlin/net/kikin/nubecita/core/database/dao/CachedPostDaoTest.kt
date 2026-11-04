package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedPostEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
internal class CachedPostDaoTest : DatabaseTest() {
    private lateinit var dao: CachedPostDao
    private lateinit var feedDao: FeedPostDao

    @Before
    fun resolveDaos() {
        dao = db.cachedPostDao()
        feedDao = db.feedPostDao()
    }

    private fun cached(
        uri: String,
        account: String = ACCOUNT,
        text: String? = "body of $uri",
        unavailable: String? = null,
    ) = CachedPostEntity(
        accountDid = account,
        uri = uri,
        cid = if (unavailable == null) "cid-$uri" else null,
        authorDid = if (unavailable == null) "did:author" else null,
        indexedAt = if (unavailable == null) Instant.fromEpochMilliseconds(1) else null,
        text = text,
        postBlob = if (unavailable == null) "{\"uri\":\"$uri\"}" else null,
        unavailable = unavailable,
    )

    private fun entry(
        position: Int,
        postUri: String,
        account: String = ACCOUNT,
    ) = FeedPostEntity(
        accountDid = account,
        feedType = TYPE,
        feedUri = FEED_URI,
        position = position,
        uri = postUri,
    )

    @Test
    fun onePostStoredOnce_servesBothATimelineItemAndAnotherItemsContext() =
        runTest {
            // Post R arrives twice: as its own timeline entry, and later as the
            // reply root of another entry. Upsert is what makes "stored once"
            // true rather than aspirational.
            dao.upsert(listOf(cached("at://post/R")))
            dao.upsert(listOf(cached("at://post/R", text = "edited body")))

            assertEquals(1, dao.countForAccount(ACCOUNT))
            assertEquals("edited body", dao.byUri(ACCOUNT, "at://post/R")?.text)
        }

    @Test
    fun aContextPostThatWasNeverATimelineEntryIsStoredAndHydrates() =
        runTest {
            // The reason this table exists: a reply's root/parent usually is not
            // a timeline entry, so it holds no position in any partition and
            // could not be hydrated by joining one.
            feedDao.upsert(listOf(entry(position = 0, postUri = "at://post/leaf")))
            dao.upsert(listOf(cached("at://post/leaf"), cached("at://post/root")))

            val root = dao.byUri(ACCOUNT, "at://post/root")
            assertNotNull(root)
            assertEquals("body of at://post/root", root?.text)
            assertNull(feedDao.maxPosition(ACCOUNT, TYPE, "at://post/root"))
        }

    @Test
    fun anUnreadableContextPostIsStoredAsATombstone() =
        runTest {
            dao.upsert(listOf(cached("at://post/blocked", text = null, unavailable = "blocked")))

            val row = dao.byUri(ACCOUNT, "at://post/blocked")
            assertEquals("blocked", row?.unavailable)
            assertNull(row?.postBlob)
            // The row must exist even though there is nothing to render from it:
            // dropping it would turn a blocked reply into a root post.
            assertNotNull(row)
        }

    @Test
    fun byUris_fetchesAPartitionsContextInOneQuery() =
        runTest {
            dao.upsert(listOf(cached("at://post/a"), cached("at://post/b"), cached("at://post/c")))

            val got = dao.byUris(ACCOUNT, listOf("at://post/a", "at://post/c", "at://post/missing"))

            assertEquals(listOf("at://post/a", "at://post/c"), got.map { it.uri }.sorted())
        }

    @Test
    fun deleteUnreferenced_keepsReferencedPosts_andDropsOrphans() =
        runTest {
            feedDao.upsert(listOf(entry(position = 0, postUri = "at://post/kept")))
            dao.upsert(listOf(cached("at://post/kept"), cached("at://post/orphan")))

            dao.deleteUnreferenced(ACCOUNT)

            assertNotNull(dao.byUri(ACCOUNT, "at://post/kept"))
            assertNull(dao.byUri(ACCOUNT, "at://post/orphan"))
        }

    @Test
    fun deleteUnreferenced_isScopedToOneAccount() =
        runTest {
            dao.upsert(listOf(cached("at://post/x"), cached("at://post/x", account = "did:other")))

            dao.deleteUnreferenced(ACCOUNT)

            assertNull(dao.byUri(ACCOUNT, "at://post/x"))
            assertNotNull(dao.byUri("did:other", "at://post/x"))
        }

    @Test
    fun theSameUriIsADistinctRowPerAccount() =
        runTest {
            dao.upsert(
                listOf(
                    cached("at://post/shared", text = "mine"),
                    cached("at://post/shared", account = "did:other", text = "theirs"),
                ),
            )

            assertEquals("mine", dao.byUri(ACCOUNT, "at://post/shared")?.text)
            assertEquals("theirs", dao.byUri("did:other", "at://post/shared")?.text)
        }

    @Test
    fun clearAccount_removesOnlyThatAccountsPosts() =
        runTest {
            dao.upsert(listOf(cached("at://post/m"), cached("at://post/t", account = "did:other")))

            dao.clearAccount(ACCOUNT)

            assertEquals(0, dao.countForAccount(ACCOUNT))
            assertEquals(1, dao.countForAccount("did:other"))
        }

    private companion object {
        const val ACCOUNT = "did:me"
        const val TYPE = "FOLLOWING"
        const val FEED_URI = ""
    }
}
