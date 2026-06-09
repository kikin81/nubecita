package net.kikin.nubecita.core.feedcache

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.FeedPostEntity
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
internal class FeedRemoteMediatorTest {
    private val key = FeedKey.following("did:plc:me")
    private val feedPostDao = mockk<FeedPostDao>(relaxed = true)
    private val remoteKeyDao = mockk<FeedRemoteKeyDao>(relaxed = true)
    private val networkSource = mockk<FeedNetworkSource>()

    /** Pass-through runner — runs the block inline, no real transaction. */
    private val transactionRunner =
        object : FeedCacheTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }

    private fun mediator(): FeedRemoteMediator =
        FeedRemoteMediator(
            feedKey = key,
            networkSource = networkSource,
            feedPostDao = feedPostDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
        )

    private fun emptyState(): PagingState<Int, FeedPostEntity> =
        PagingState(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = 25),
            leadingPlaceholderCount = 0,
        )

    @Test
    fun `REFRESH clears the partition, inserts page with positions 0_n, stores cursor`() =
        runTest {
            val posts = listOf(post("a"), post("b"), post("c"))
            coEvery { networkSource.fetchPage(key, cursor = null) } returns Result.success(posts to "cursor-1")
            val inserted = slot<List<FeedPostEntity>>()
            coEvery { feedPostDao.upsert(capture(inserted)) } just Runs
            val storedKey = slot<FeedRemoteKeyEntity>()
            coEvery { remoteKeyDao.upsert(capture(storedKey)) } just Runs

            val result = mediator().load(LoadType.REFRESH, emptyState())

            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            coVerify(exactly = 1) { feedPostDao.clearPartition("did:plc:me", "FOLLOWING", "") }
            assertEquals(listOf(0, 1, 2), inserted.captured.map { it.position })
            assertEquals(listOf("a", "b", "c"), inserted.captured.map { it.uriTail() })
            assertEquals("cursor-1", storedKey.captured.nextCursor)
        }

    @Test
    fun `REFRESH with null cursor reaches end of pagination`() =
        runTest {
            coEvery { networkSource.fetchPage(key, cursor = null) } returns Result.success(listOf(post("a")) to null)

            val result = mediator().load(LoadType.REFRESH, emptyState())

            assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        }

    @Test
    fun `REFRESH with empty page reaches end of pagination`() =
        runTest {
            coEvery { networkSource.fetchPage(key, cursor = null) } returns Result.success(emptyList<FeedViewPost>() to "c")

            val result = mediator().load(LoadType.REFRESH, emptyState())

            assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        }

    @Test
    fun `APPEND uses stored cursor and appends at maxPosition plus 1`() =
        runTest {
            coEvery { remoteKeyDao.remoteKey("did:plc:me", "FOLLOWING", "") } returns
                FeedRemoteKeyEntity("did:plc:me", "FOLLOWING", "", nextCursor = "stored-cursor")
            coEvery { feedPostDao.maxPosition("did:plc:me", "FOLLOWING", "") } returns 9
            val posts = listOf(post("x"), post("y"))
            coEvery { networkSource.fetchPage(key, cursor = "stored-cursor") } returns Result.success(posts to "cursor-2")
            val inserted = slot<List<FeedPostEntity>>()
            coEvery { feedPostDao.upsert(capture(inserted)) } just Runs

            val result = mediator().load(LoadType.APPEND, emptyState())

            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            // maxPosition 9 -> next positions 10, 11
            assertEquals(listOf(10, 11), inserted.captured.map { it.position })
            coVerify { networkSource.fetchPage(key, cursor = "stored-cursor") }
            // APPEND must not clear the partition.
            coVerify(exactly = 0) { feedPostDao.clearPartition(any(), any(), any()) }
        }

    @Test
    fun `APPEND on empty partition starts positions at 0`() =
        runTest {
            coEvery { remoteKeyDao.remoteKey("did:plc:me", "FOLLOWING", "") } returns
                FeedRemoteKeyEntity("did:plc:me", "FOLLOWING", "", nextCursor = "stored-cursor")
            coEvery { feedPostDao.maxPosition("did:plc:me", "FOLLOWING", "") } returns null
            coEvery { networkSource.fetchPage(key, cursor = "stored-cursor") } returns Result.success(listOf(post("x")) to "c2")
            val inserted = slot<List<FeedPostEntity>>()
            coEvery { feedPostDao.upsert(capture(inserted)) } just Runs

            mediator().load(LoadType.APPEND, emptyState())

            assertEquals(listOf(0), inserted.captured.map { it.position })
        }

    @Test
    fun `APPEND with null stored cursor reaches end of pagination without fetching`() =
        runTest {
            coEvery { remoteKeyDao.remoteKey("did:plc:me", "FOLLOWING", "") } returns
                FeedRemoteKeyEntity("did:plc:me", "FOLLOWING", "", nextCursor = null)

            val result = mediator().load(LoadType.APPEND, emptyState())

            assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            coVerify(exactly = 0) { networkSource.fetchPage(any(), any(), any()) }
        }

    @Test
    fun `APPEND with no stored key reaches end of pagination without fetching`() =
        runTest {
            coEvery { remoteKeyDao.remoteKey("did:plc:me", "FOLLOWING", "") } returns null

            val result = mediator().load(LoadType.APPEND, emptyState())

            assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            coVerify(exactly = 0) { networkSource.fetchPage(any(), any(), any()) }
        }

    @Test
    fun `network failure on REFRESH yields MediatorResult Error`() =
        runTest {
            val boom = RuntimeException("network down")
            coEvery { networkSource.fetchPage(key, cursor = null) } returns Result.failure(boom)

            val result = mediator().load(LoadType.REFRESH, emptyState())

            assertTrue(result is RemoteMediator.MediatorResult.Error)
            assertSame(boom, (result as RemoteMediator.MediatorResult.Error).throwable)
            coVerify(exactly = 0) { feedPostDao.upsert(any()) }
        }

    @Test
    fun `PREPEND short-circuits to end of pagination`() =
        runTest {
            val result = mediator().load(LoadType.PREPEND, emptyState())

            assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            coVerify(exactly = 0) { networkSource.fetchPage(any(), any(), any()) }
        }

    @Test
    fun `initialize skips refresh when partition already has cached rows`() =
        runTest {
            coEvery { feedPostDao.maxPosition("did:plc:me", "FOLLOWING", "") } returns 12

            assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, mediator().initialize())
        }

    @Test
    fun `initialize launches refresh when partition is empty`() =
        runTest {
            coEvery { feedPostDao.maxPosition("did:plc:me", "FOLLOWING", "") } returns null

            assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator().initialize())
        }

    // The uri tail (last path segment) identifies a post in assertions.
    private fun FeedPostEntity.uriTail(): String = uri.substringAfterLast('/')
}

private val mediatorTestJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

/** A minimal wire [FeedViewPost] whose post uri ends in [tail]. */
private fun post(tail: String): FeedViewPost {
    val wire =
        buildJsonObject {
            put("\$type", "app.bsky.feed.defs#postView")
            put("uri", "at://did:plc:abc/app.bsky.feed.post/$tail")
            put("cid", "bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq")
            putJsonObject("author") {
                put("did", "did:plc:abc")
                put("handle", "alice.bsky.social")
                put("displayName", "Alice")
            }
            putJsonObject("record") {
                put("\$type", "app.bsky.feed.post")
                put("text", "post $tail")
                put("createdAt", "2026-04-15T19:51:12.861Z")
                putJsonArray("langs") { add("en") }
            }
            put("indexedAt", "2026-04-15T19:51:13.000Z")
        }
    return FeedViewPost(post = mediatorTestJson.decodeFromJsonElement(PostView.serializer(), wire))
}
