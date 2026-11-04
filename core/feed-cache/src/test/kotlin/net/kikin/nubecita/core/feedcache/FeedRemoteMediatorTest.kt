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
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedPartitionStateDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.FeedPartitionStateEntity
import net.kikin.nubecita.core.database.model.FeedPostEntity
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity
import net.kikin.nubecita.core.feeds.FeedViewPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.FeedViewPrefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
internal class FeedRemoteMediatorTest {
    private val key = FeedKey.following("did:plc:me")
    private val feedPostDao = mockk<FeedPostDao>(relaxed = true)
    private val cachedPostDao = mockk<CachedPostDao>(relaxed = true)
    private val partitionStateDao =
        mockk<FeedPartitionStateDao>(relaxed = true).also {
            // Explicit: a relaxed mock answers a nullable return with a STUB,
            // not null, which would read as "a fingerprint exists and differs"
            // and make every partition look stale.
            coEvery { it.state(any(), any(), any()) } returns null
        }
    private val moderationPreferences =
        mockk<ModerationPreferencesRepository>().also {
            coEvery { it.prefs } returns MutableStateFlow(ModerationPrefs.DEFAULT)
        }
    private val feedViewPreferences =
        mockk<FeedViewPreferencesRepository>().also {
            coEvery { it.prefs } returns MutableStateFlow(FeedViewPrefs.DEFAULT)
        }
    private val projectionWriter =
        mockk<FeedProjectionWriter>(relaxed = true).also {
            // A relaxed mock answers an Int with 0, which now MEANS "this page
            // projected to nothing" and sends append into its top-up loop.
            // Default to a productive page; the top-up tests override it.
            coEvery { it.writeAppend(any(), any()) } returns 1
        }
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
            cachedPostDao = cachedPostDao,
            projectionWriter = projectionWriter,
            partitionStateDao = partitionStateDao,
            moderationPreferences = moderationPreferences,
            feedViewPreferences = feedViewPreferences,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            // REFRESH now delegates to the shared FeedRefresher; wire a real one
            // over the same mocks so the existing REFRESH assertions still hold
            // (delegation = no behavior change).
            feedRefresher =
                FeedRefresher(
                    networkSource = networkSource,
                    feedPostDao = feedPostDao,
                    cachedPostDao = cachedPostDao,
                    projectionWriter = projectionWriter,
                    remoteKeyDao = remoteKeyDao,
                    transactionRunner = transactionRunner,
                ),
        )

    private fun emptyState(): PagingState<Int, FeedItemUi> =
        PagingState(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = 25),
            leadingPlaceholderCount = 0,
        )

    @Test
    fun `append follows a barren page rather than reporting the end`() =
        runTest {
            // The page arrived but projected to nothing - every entry filtered
            // or de-duplicated away. Reporting end-of-pagination here would
            // stop the feed while the server still has content.
            coEvery {
                remoteKeyDao.remoteKey(any(), any(), any())
            } returns FeedRemoteKeyEntity("did:plc:me", "FOLLOWING", "", "cursor-1")
            coEvery { networkSource.fetchPage(key, cursor = "cursor-1") } returns
                Result.success(listOf(post("a")) to "cursor-2")
            coEvery { networkSource.fetchPage(key, cursor = "cursor-2") } returns
                Result.success(listOf(post("b")) to "cursor-3")
            coEvery { projectionWriter.writeAppend(key, any()) } returns 0 andThen 2

            val result = mediator().load(LoadType.APPEND, emptyState())

            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            coVerify { networkSource.fetchPage(key, cursor = "cursor-2") }
        }

    @Test
    fun `the top-up is bounded`() =
        runTest {
            // An aggressively filtered account could otherwise walk its whole
            // timeline inside one load().
            coEvery {
                remoteKeyDao.remoteKey(any(), any(), any())
            } returns FeedRemoteKeyEntity("did:plc:me", "FOLLOWING", "", "c")
            coEvery { networkSource.fetchPage(key, cursor = any()) } returns
                Result.success(listOf(post("a")) to "c")
            coEvery { projectionWriter.writeAppend(key, any()) } returns 0

            mediator().load(LoadType.APPEND, emptyState())

            coVerify(exactly = 3) { networkSource.fetchPage(key, cursor = any()) }
        }

    @Test
    fun `a barren page that is also the last page reports the end`() =
        runTest {
            // Nothing projected AND no cursor: this really is the end, and
            // following it would be a wasted fetch.
            coEvery {
                remoteKeyDao.remoteKey(any(), any(), any())
            } returns FeedRemoteKeyEntity("did:plc:me", "FOLLOWING", "", "cursor-1")
            coEvery { networkSource.fetchPage(key, cursor = "cursor-1") } returns
                Result.success(listOf(post("a")) to null)
            coEvery { projectionWriter.writeAppend(key, any()) } returns 0

            val result = mediator().load(LoadType.APPEND, emptyState())

            assertEquals(
                true,
                (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached,
            )
            coVerify(exactly = 1) { networkSource.fetchPage(key, cursor = any()) }
        }

    @Test
    fun `append advances only the append cursor`() =
        runTest {
            // The row also carries the prepend cursors; a whole-row upsert from
            // this direction would clear them and break the catch-up range.
            coEvery {
                remoteKeyDao.remoteKey(any(), any(), any())
            } returns FeedRemoteKeyEntity("did:plc:me", "FOLLOWING", "", "cursor-1")
            coEvery { networkSource.fetchPage(key, cursor = "cursor-1") } returns
                Result.success(listOf(post("a")) to "cursor-2")
            coEvery { projectionWriter.writeAppend(key, any()) } returns 1

            mediator().load(LoadType.APPEND, emptyState())

            coVerify { remoteKeyDao.updateAppendCursor("did:plc:me", "FOLLOWING", "", "cursor-2") }
            coVerify(exactly = 0) { remoteKeyDao.upsert(any()) }
        }

    @Test
    fun `append writes the projection, and prepend writes none`() =
        runTest {
            // The direction asymmetry, asserted rather than assumed: a prepend
            // path does not exist yet, so it must not be projecting anything.
            coEvery {
                remoteKeyDao.remoteKey(any(), any(), any())
            } returns FeedRemoteKeyEntity("did:plc:me", "FOLLOWING", "", "cursor-1")
            coEvery { networkSource.fetchPage(key, cursor = "cursor-1") } returns
                Result.success(listOf(post("a")) to null)

            mediator().load(LoadType.APPEND, emptyState())
            coVerify(exactly = 1) { projectionWriter.writeAppend(key, any()) }

            mediator().load(LoadType.PREPEND, emptyState())
            coVerify(exactly = 0) { projectionWriter.writeRefresh(key, any()) }
            coVerify(exactly = 1) { projectionWriter.writeAppend(key, any()) }
        }

    @Test
    fun `append writes the page's posts before its envelopes`() =
        runTest {
            // Same reason as the refresher's: cachedPostDao is a relaxed mock,
            // so dropping the call would otherwise be invisible.
            coEvery {
                remoteKeyDao.remoteKey(any(), any(), any())
            } returns FeedRemoteKeyEntity("did:plc:me", "FOLLOWING", "", "cursor-1")
            coEvery { networkSource.fetchPage(key, cursor = "cursor-1") } returns
                Result.success(listOf(post("a")) to null)

            mediator().load(LoadType.APPEND, emptyState())

            coVerifyOrder {
                cachedPostDao.upsert(any())
                feedPostDao.upsert(any())
            }
        }

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
    fun `initialize re-projects locally when the projection's preferences are stale`() =
        runTest {
            // The reader changed a setting since this partition was projected,
            // so its stored rows would keep showing what they asked to hide.
            // Re-projected from disk, NOT refetched: the preference applies and
            // the reader keeps their place, which is the point of the cache.
            coEvery { feedPostDao.maxPosition("did:plc:me", "FOLLOWING", "") } returns 12
            coEvery { partitionStateDao.state("did:plc:me", "FOLLOWING", "") } returns
                FeedPartitionStateEntity("did:plc:me", "FOLLOWING", "", prefsFingerprint = "stale-fingerprint")

            val action = mediator().initialize()

            assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, action)
            coVerify { projectionWriter.reproject(key) }
        }

    @Test
    fun `initialize does not re-project when the preferences still match`() =
        runTest {
            coEvery { feedPostDao.maxPosition("did:plc:me", "FOLLOWING", "") } returns 12
            coEvery { partitionStateDao.state("did:plc:me", "FOLLOWING", "") } returns
                FeedPartitionStateEntity(
                    "did:plc:me",
                    "FOLLOWING",
                    "",
                    prefsFingerprint =
                        projectionPrefsFingerprint(ModerationPrefs.DEFAULT, FeedViewPrefs.DEFAULT),
                )

            mediator().initialize()

            coVerify(exactly = 0) { projectionWriter.reproject(any()) }
        }

    @Test
    fun `initialize skips when the projection's preferences still match`() =
        runTest {
            coEvery { feedPostDao.maxPosition("did:plc:me", "FOLLOWING", "") } returns 12
            coEvery { partitionStateDao.state("did:plc:me", "FOLLOWING", "") } returns
                FeedPartitionStateEntity(
                    "did:plc:me",
                    "FOLLOWING",
                    "",
                    prefsFingerprint =
                        projectionPrefsFingerprint(ModerationPrefs.DEFAULT, FeedViewPrefs.DEFAULT),
                )

            assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, mediator().initialize())
        }

    @Test
    fun `a partition with no recorded preferences is not treated as stale`() =
        runTest {
            // Rows written before the column existed have nothing recorded.
            // Calling that stale would refetch every partition once on upgrade
            // for no reader-visible gain.
            coEvery { feedPostDao.maxPosition("did:plc:me", "FOLLOWING", "") } returns 12
            coEvery { partitionStateDao.state("did:plc:me", "FOLLOWING", "") } returns
                FeedPartitionStateEntity("did:plc:me", "FOLLOWING", "", prefsFingerprint = null)

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
