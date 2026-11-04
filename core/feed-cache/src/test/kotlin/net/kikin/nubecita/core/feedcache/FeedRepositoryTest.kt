package net.kikin.nubecita.core.feedcache

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.CachedFeedEntry
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
internal class FeedRepositoryTest {
    private val key = FeedKey.following("did:plc:me")
    private val feedPostDao = mockk<FeedPostDao>(relaxed = true)
    private val cachedPostDao = mockk<CachedPostDao>(relaxed = true)
    private val projectionWriter = mockk<FeedProjectionWriter>(relaxed = true)
    private val feedItemDao = mockk<FeedItemDao>(relaxed = true)
    private val remoteKeyDao = mockk<FeedRemoteKeyDao>(relaxed = true)
    private val networkSource = mockk<FeedNetworkSource>(relaxed = true)

    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn("me.bsky.social", "did:plc:me"))
    private val prefs = MutableStateFlow(ModerationPrefs.DEFAULT)

    private val sessionProvider =
        object : SessionStateProvider {
            override val state = session

            override suspend fun refresh() = Unit
        }

    private val moderationPrefs =
        mockk<ModerationPreferencesRepository> {
            every { prefs } returns this@FeedRepositoryTest.prefs
        }

    // No-op mediator so the Pager doesn't hit the network; the PagingSource
    // already returns the seeded rows.
    private val noopMediator =
        object : RemoteMediator<Int, CachedFeedEntry>() {
            override suspend fun initialize() = InitializeAction.SKIP_INITIAL_REFRESH

            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, CachedFeedEntry>,
            ) = MediatorResult.Success(endOfPaginationReached = true)
        }

    private fun repository(seed: List<CachedFeedEntry>): DefaultFeedRepository {
        coEvery { feedPostDao.pagingSource(any(), any(), any()) } answers { FakePagingSource(seed) }
        val factory =
            object : FeedRemoteMediator.Factory {
                override fun create(feedKey: FeedKey) = error("unused — repository wraps in a no-op for the test")
            }
        return TestFeedRepository(
            feedPostDao = feedPostDao,
            cachedPostDao = cachedPostDao,
            feedItemDao = feedItemDao,
            remoteKeyDao = remoteKeyDao,
            mediatorFactory = factory,
            sessionStateProvider = sessionProvider,
            moderationPreferences = moderationPrefs,
            transactionRunner = passthroughRunner,
            feedRefresher =
                FeedRefresher(
                    networkSource = networkSource,
                    feedPostDao = feedPostDao,
                    cachedPostDao = cachedPostDao,
                    projectionWriter = projectionWriter,
                    remoteKeyDao = remoteKeyDao,
                    transactionRunner = passthroughRunner,
                ),
            testMediator = noopMediator,
        )
    }

    @Test
    fun `normal entity maps to PostUi in the paged stream`() =
        runTest {
            val repo = repository(listOf(entity("good", text = "hello", position = 0)))

            val items = repo.pagedFeed(key).asSnapshot()

            assertEquals(1, items.size)
            assertEquals("hello", items.single().text)
        }

    @Test
    fun `moderation-filtered entity is dropped from the paged stream`() =
        runTest {
            // adult content disabled (DEFAULT) -> a porn-labeled post is dropped.
            val repo =
                repository(
                    listOf(
                        entity("ok", text = "kept", position = 0),
                        entity("nsfw", text = "dropped", position = 1, labels = listOf("did:plc:labeler" to "porn")),
                    ),
                )

            val items = repo.pagedFeed(key).asSnapshot()

            assertEquals(listOf("kept"), items.map { it.text })
        }

    @Test
    fun `head returns at most n posts mapped to PostUi in position order`() =
        runTest {
            val rows =
                MutableStateFlow(
                    listOf(
                        entity("a", text = "first", position = 0),
                        entity("b", text = "second", position = 1),
                    ),
                )
            every { feedPostDao.head("did:plc:me", "FOLLOWING", "", 5) } returns rows
            val repo = repository(emptyList())

            repo.head(key, n = 5).test {
                assertEquals(listOf("first", "second"), awaitItem().map { it.text })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `head drops a moderation-filtered entity`() =
        runTest {
            // adult content disabled (DEFAULT) -> a porn-labeled post is dropped.
            val rows =
                MutableStateFlow(
                    listOf(
                        entity("ok", text = "kept", position = 0),
                        entity("nsfw", text = "dropped", position = 1, labels = listOf("did:plc:labeler" to "porn")),
                    ),
                )
            every { feedPostDao.head("did:plc:me", "FOLLOWING", "", 10) } returns rows
            val repo = repository(emptyList())

            repo.head(key, n = 10).test {
                assertEquals(listOf("kept"), awaitItem().map { it.text })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `head emits again on a cache change`() =
        runTest {
            val rows = MutableStateFlow(listOf(entity("a", text = "first", position = 0)))
            every { feedPostDao.head("did:plc:me", "FOLLOWING", "", 5) } returns rows
            val repo = repository(emptyList())

            repo.head(key, n = 5).test {
                assertEquals(listOf("first"), awaitItem().map { it.text })
                rows.value = listOf(entity("a", text = "first", position = 0), entity("b", text = "second", position = 1))
                assertEquals(listOf("first", "second"), awaitItem().map { it.text })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `trimToCap delegates to the dao`() =
        runTest {
            repository(emptyList()).trimToCap(key, cap = 100)
            coVerify { feedPostDao.trimToCap("did:plc:me", "FOLLOWING", "", 100) }
        }

    @Test
    fun `clearAccount clears every grain, not just the entry table`() =
        runTest {
            // Sign-out has to remove the content, and the content is in the post
            // store and the projection — the entry table holds only references.
            // Clearing envelopes and cursors alone would leave a signed-out
            // account's posts readable on disk.
            repository(emptyList()).clearAccount("did:plc:me")
            coVerify { feedPostDao.clearAccount("did:plc:me") }
            coVerify { feedItemDao.clearAccount("did:plc:me") }
            coVerify { cachedPostDao.clearAccount("did:plc:me") }
            coVerify { remoteKeyDao.clearAccount("did:plc:me") }
        }

    @Test
    fun `refresh delegates to FeedRefresher`() =
        runTest {
            // Empty page + null cursor -> endOfPaginationReached == true; the
            // delegated FeedRefresher fetches page 1 and writes through.
            coEvery { networkSource.fetchPage(key, cursor = null) } returns Result.success(emptyList<FeedViewPost>() to null)

            val result = repository(emptyList()).refresh(key)

            assertEquals(Result.success(true), result)
            coVerify { networkSource.fetchPage(key, cursor = null) }
            coVerify { feedPostDao.clearPartition("did:plc:me", "FOLLOWING", "") }
        }

    private val passthroughRunner =
        object : FeedCacheTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }

    /**
     * One row of the joined read shape, built from the two production write
     * mappers so the fake source cannot serve a pairing the write path would
     * never produce.
     */
    private fun entity(
        tail: String,
        text: String,
        position: Int,
        labels: List<Pair<String, String>> = emptyList(),
    ): CachedFeedEntry {
        val wire = FeedViewPost(post = wirePost(tail, text, labels))
        val envelope = wire.toFeedPostEntity(key, position)
        return CachedFeedEntry(
            position = envelope.position,
            uri = envelope.uri,
            repostedByDid = envelope.repostedByDid,
            repostedByLabel = envelope.repostedByLabel,
            replyRootUri = envelope.replyRootUri,
            replyParentUri = envelope.replyParentUri,
            post = wire.toCachedPosts(key).first { it.uri == envelope.uri },
        )
    }
}

/**
 * Subclass that swaps the assisted mediator factory for a fixed no-op mediator,
 * so the Pager runs purely against the seeded [FakePagingSource] on the JVM.
 */
@OptIn(ExperimentalPagingApi::class)
private class TestFeedRepository(
    feedPostDao: FeedPostDao,
    cachedPostDao: CachedPostDao,
    feedItemDao: FeedItemDao,
    remoteKeyDao: FeedRemoteKeyDao,
    mediatorFactory: FeedRemoteMediator.Factory,
    sessionStateProvider: SessionStateProvider,
    moderationPreferences: ModerationPreferencesRepository,
    transactionRunner: FeedCacheTransactionRunner,
    feedRefresher: FeedRefresher,
    private val testMediator: RemoteMediator<Int, CachedFeedEntry>,
) : DefaultFeedRepository(
        feedPostDao,
        cachedPostDao,
        feedItemDao,
        remoteKeyDao,
        mediatorFactory,
        sessionStateProvider,
        moderationPreferences,
        transactionRunner,
        feedRefresher,
    ) {
    override fun mediatorFor(feedKey: FeedKey): RemoteMediator<Int, CachedFeedEntry> = testMediator
}

/** In-memory single-page [PagingSource] over the seeded rows. */
private class FakePagingSource(
    private val rows: List<CachedFeedEntry>,
) : PagingSource<Int, CachedFeedEntry>() {
    override fun getRefreshKey(state: PagingState<Int, CachedFeedEntry>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CachedFeedEntry> = LoadResult.Page(data = rows, prevKey = null, nextKey = null)
}

private val repoTestJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

private fun wirePost(
    tail: String,
    text: String,
    labels: List<Pair<String, String>>,
): PostView {
    val uri = "at://did:plc:abc/app.bsky.feed.post/$tail"
    val wire =
        buildJsonObject {
            put("\$type", "app.bsky.feed.defs#postView")
            put("uri", uri)
            put("cid", "bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq")
            putJsonObject("author") {
                put("did", "did:plc:other")
                put("handle", "alice.bsky.social")
                put("displayName", "Alice")
            }
            putJsonObject("record") {
                put("\$type", "app.bsky.feed.post")
                put("text", text)
                put("createdAt", "2026-04-15T19:51:12.861Z")
                putJsonArray("langs") { add("en") }
            }
            put("indexedAt", "2026-04-15T19:51:13.000Z")
            if (labels.isNotEmpty()) {
                putJsonArray("labels") {
                    labels.forEach { (src, value) ->
                        addJsonObject {
                            put("src", src)
                            put("uri", uri)
                            put("val", value)
                            put("cts", "2026-04-15T19:51:13.000Z")
                        }
                    }
                }
            }
        }
    return repoTestJson.decodeFromJsonElement(PostView.serializer(), wire)
}
