package net.kikin.nubecita.core.feedcache

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.model.FeedItemEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FeedCachePostRemoverTest {
    private val feedPostDao = mockk<FeedPostDao>(relaxed = true)
    private val feedItemDao = mockk<FeedItemDao>(relaxed = true)
    private val cachedPostDao = mockk<CachedPostDao>(relaxed = true)
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn("me.bsky.social", ACCOUNT))

    private var transactions = 0
    private val transactionRunner =
        object : FeedCacheTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T {
                transactions++
                return block()
            }
        }

    private val remover =
        FeedCachePostRemover(
            feedPostDao = feedPostDao,
            feedItemDao = feedItemDao,
            cachedPostDao = cachedPostDao,
            sessionStateProvider =
                object : SessionStateProvider {
                    override val state = session

                    override suspend fun refresh() = Unit
                },
            transactionRunner = transactionRunner,
        )

    private fun item(
        variant: String,
        position: Int = 3,
        feedUri: String = "",
    ) = FeedItemEntity(
        accountDid = ACCOUNT,
        feedType = "FOLLOWING",
        feedUri = feedUri,
        position = position,
        rowType = FeedItemEntity.ROW_TYPE_ITEM,
        variant = variant,
        itemKey = "$variant:$position",
        leafUri = POST,
    )

    @Test
    fun `a deleted post leaves the store as a tombstone and loses its entries`() =
        runTest {
            remover.onPostDeleted(POST)

            coVerify(exactly = 1) { feedPostDao.deleteEntriesFor(ACCOUNT, POST) }
            coVerify(exactly = 1) { cachedPostDao.markUnavailable(ACCOUNT, POST, UNAVAILABLE_NOT_FOUND) }
        }

    @Test
    fun `an item the post was the point of is removed, in every partition`() =
        runTest {
            coEvery { feedItemDao.itemsWithLeaf(ACCOUNT, POST) } returns
                listOf(item(VARIANT_SINGLE), item(VARIANT_CLUSTER, position = 7, feedUri = "at://feed/other"))

            remover.onPostDeleted(POST)

            coVerify(exactly = 1) { feedItemDao.deleteItemAt(ACCOUNT, "FOLLOWING", "", 3) }
            coVerify(exactly = 1) { feedItemDao.deleteItemAt(ACCOUNT, "FOLLOWING", "at://feed/other", 7) }
        }

    /**
     * The earlier posts in the thread are still live. The tombstone is what
     * thins the chain on read, so deleting the row would take them with it.
     */
    @Test
    fun `a chain outlives its last post`() =
        runTest {
            coEvery { feedItemDao.itemsWithLeaf(ACCOUNT, POST) } returns listOf(item(VARIANT_CHAIN))

            remover.onPostDeleted(POST)

            coVerify(exactly = 0) { feedItemDao.deleteItemAt(any(), any(), any(), any()) }
            coVerify(exactly = 1) { cachedPostDao.markUnavailable(ACCOUNT, POST, UNAVAILABLE_NOT_FOUND) }
        }

    @Test
    fun `the whole removal is one transaction`() =
        runTest {
            remover.onPostDeleted(POST)

            assertEquals(1, transactions)
        }

    /** Sign-out already clears the account, and there is no partition to name. */
    @Test
    fun `nothing happens without a signed-in account`() =
        runTest {
            session.value = SessionState.SignedOut

            remover.onPostDeleted(POST)

            assertEquals(0, transactions)
        }

    private companion object {
        const val ACCOUNT = "did:plc:me"
        const val POST = "at://did:plc:me/app.bsky.feed.post/gone"
    }
}
