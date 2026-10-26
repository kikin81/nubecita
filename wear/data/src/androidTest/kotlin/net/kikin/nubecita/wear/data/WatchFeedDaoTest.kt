package net.kikin.nubecita.wear.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.wear.data.db.WatchDatabase
import net.kikin.nubecita.wear.data.db.WatchFeedEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchFeedDaoTest {
    private val db =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WatchDatabase::class.java).build()
    private val dao = db.feedDao()

    @After
    fun close() = db.close()

    private fun post(
        account: String,
        feed: String,
        position: Int,
    ) = WatchPostEntity(
        accountDid = account,
        feedKey = feed,
        position = position,
        uri = "at://$account/$feed/$position",
        cid = "cid",
        authorDid = "did:plc:x",
        authorHandle = "x",
        authorDisplayName = null,
        authorAvatarPath = null,
        text = "t",
        createdAtMs = 0,
        replyCount = 0,
        repostCount = 0,
        likeCount = 0,
        repostedBy = null,
        likeUri = null,
        bookmarked = false,
        imageCount = 0,
        isVideo = false,
        hasContentWarning = false,
        thumbnailPath = null,
    )

    @Test
    fun replaceFeed_swapsOneFeedsPostsAndLeavesOthers() =
        runTest {
            dao.replaceFeed(WatchFeedEntity("a", "following", null, 1), listOf(post("a", "following", 0), post("a", "following", 1)))
            dao.replaceFeed(WatchFeedEntity("a", "cats", "Cats", 1), listOf(post("a", "cats", 0)))

            dao.replaceFeed(WatchFeedEntity("a", "following", null, 2), listOf(post("a", "following", 0)))

            assertEquals(1, dao.observePosts("a", "following").first().size)
            assertEquals(1, dao.observePosts("a", "cats").first().size)
            assertEquals(2L, dao.publishedAtMs("a", "following"))
        }

    @Test
    fun keepOnlyAccount_dropsOtherAccounts_andNullDropsAll() =
        runTest {
            dao.replaceFeed(WatchFeedEntity("a", "following", null, 1), listOf(post("a", "following", 0)))
            dao.replaceFeed(WatchFeedEntity("b", "following", null, 1), listOf(post("b", "following", 0)))

            dao.keepOnlyAccount("b")
            assertTrue(dao.observeFeeds("a").first().isEmpty())
            assertEquals(1, dao.observeFeeds("b").first().size)

            dao.keepOnlyAccount(null)
            assertTrue(dao.observeFeeds("b").first().isEmpty())
        }
}
