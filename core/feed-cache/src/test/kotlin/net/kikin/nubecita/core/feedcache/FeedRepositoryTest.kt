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
import net.kikin.nubecita.core.database.NubecitaDatabase
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPartitionStateDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.CachedFeedEntry
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedItemUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
internal class FeedRepositoryTest {
    private val key = FeedKey.following("did:plc:me")
    private val feedPostDao = mockk<FeedPostDao>(relaxed = true)
    private val cachedPostDao = mockk<CachedPostDao>(relaxed = true)
    private val projectionWriter = mockk<FeedProjectionWriter>(relaxed = true)
    private val feedItemDao = mockk<FeedItemDao>(relaxed = true)
    private val partitionStateDao = mockk<FeedPartitionStateDao>(relaxed = true)
    private val database = mockk<NubecitaDatabase>(relaxed = true)
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
        object : RemoteMediator<Int, FeedItemUi>() {
            override suspend fun initialize() = InitializeAction.SKIP_INITIAL_REFRESH

            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, FeedItemUi>,
            ) = MediatorResult.Success(endOfPaginationReached = true)
        }

    private fun repository(seed: List<FeedItemUi>): DefaultFeedRepository {
        val factory =
            object : FeedRemoteMediator.Factory {
                override fun create(feedKey: FeedKey) = error("unused — repository wraps in a no-op for the test")
            }
        return TestFeedRepository(
            feedPostDao = feedPostDao,
            cachedPostDao = cachedPostDao,
            feedItemDao = feedItemDao,
            partitionStateDao = partitionStateDao,
            database = database,
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
            // PREPEND delegates to the shared FeedCatchUp for the same reason;
            // a real one over the same mocks keeps these tests exercising the
            // actual window allocation rather than a stub of it.
            feedCatchUp =
                FeedCatchUp(
                    networkSource = networkSource,
                    feedPostDao = feedPostDao,
                    cachedPostDao = cachedPostDao,
                    projectionWriter = projectionWriter,
                    remoteKeyDao = remoteKeyDao,
                    transactionRunner = passthroughRunner,
                ),
            testMediator = noopMediator,
            testRows = seed,
        )
    }

    @Test
    fun `normal entity maps to PostUi in the paged stream`() =
        runTest {
            val repo = repository(listOf(item("good", text = "hello", position = 0)))

            val items = repo.pagedFeed(key).asSnapshot()

            assertEquals(1, items.size)
            assertEquals("hello", (items.single() as FeedItemUi.Single).post.text)
        }

    @Test
    fun `the paged stream serves the projection unchanged`() =
        runTest {
            // Moderation and the reply tuners are applied where the projection
            // is computed, not here: this layer hands through whatever the
            // source read. The dropping behaviour is covered against the
            // rehydration mapper, which is where it now lives.
            val repo = repository(listOf(item("a", text = "first", position = 0), item("b", text = "second", position = 1)))

            val items = repo.pagedFeed(key).asSnapshot()

            assertEquals(listOf("first", "second"), items.map { (it as FeedItemUi.Single).post.text })
        }

    @Test
    fun `head returns at most n posts mapped to PostUi in position order`() =
        runTest {
            val rows =
                MutableStateFlow(
                    listOf(
                        entry("a", text = "first", position = 0),
                        entry("b", text = "second", position = 1),
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
                        entry("ok", text = "kept", position = 0),
                        entry("nsfw", text = "dropped", position = 1, labels = listOf("did:plc:labeler" to "porn")),
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
            val rows = MutableStateFlow(listOf(entry("a", text = "first", position = 0)))
            every { feedPostDao.head("did:plc:me", "FOLLOWING", "", 5) } returns rows
            val repo = repository(emptyList())

            repo.head(key, n = 5).test {
                assertEquals(listOf("first"), awaitItem().map { it.text })
                rows.value = listOf(entry("a", text = "first", position = 0), entry("b", text = "second", position = 1))
                assertEquals(listOf("first", "second"), awaitItem().map { it.text })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the trim bounds every grain, not just the entry table`() =
        runTest {
            // The growth bug the scale measurement found: the old trim bounded
            // feed_post alone while the projection, its members and the post
            // store grew without limit.
            coEvery { feedItemDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 99) } returns 120
            coEvery { feedItemDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 1499) } returns null
            coEvery { feedPostDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 99) } returns 140
            coEvery { feedPostDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 1499) } returns null
            coEvery { feedPostDao.oldestReferencedByProjection(any(), any(), any()) } returns null

            repository(emptyList()).trimToCap(key, cap = 100)

            coVerify { feedItemDao.deleteOlderThan("did:plc:me", "FOLLOWING", "", 120) }
            coVerify { feedPostDao.deleteOlderThan("did:plc:me", "FOLLOWING", "", 140) }
            // Reference-counted, and last: a context post holds no position, so
            // nothing above could have evicted it.
            coVerify { cachedPostDao.deleteUnreferenced("did:plc:me") }
        }

    @Test
    fun `a partition under the cap is left alone`() =
        runTest {
            coEvery { feedItemDao.positionAtOffsetFromTop(any(), any(), any(), any()) } returns null
            coEvery { feedPostDao.positionAtOffsetFromTop(any(), any(), any(), any()) } returns null
            coEvery { feedPostDao.oldestReferencedByProjection(any(), any(), any()) } returns null

            repository(emptyList()).trimToCap(key, cap = 100)

            coVerify(exactly = 0) { feedItemDao.deleteOlderThan(any(), any(), any(), any()) }
            coVerify(exactly = 0) { feedPostDao.deleteOlderThan(any(), any(), any(), any()) }
            // Still run: a post can be unreferenced without any row being
            // deleted here — a REFRESH replaced the partition it belonged to.
            coVerify { cachedPostDao.deleteUnreferenced("did:plc:me") }
        }

    @Test
    fun `the anchor's backlog survives a cap that would delete it`() =
        runTest {
            // A catch-up writes up to half the cap above the anchor, so the
            // newest `cap` rows are the freshly fetched ones and the reader's
            // unread backlog is everything below. Trimming to the cap here
            // would delete the anchor and the backlog while the app was
            // backgrounded, which is the window this feature exists to protect.
            coEvery { feedItemDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 99) } returns 40
            coEvery { feedItemDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 1499) } returns null
            coEvery { feedPostDao.positionAtOffsetFromTop(any(), any(), any(), any()) } returns null
            coEvery { feedPostDao.oldestReferencedByProjection(any(), any(), any()) } returns null

            repository(emptyList()).trimToCap(key, cap = 100, anchorItemPosition = 310)

            coVerify { feedItemDao.deleteOlderThan("did:plc:me", "FOLLOWING", "", 310) }
        }

    @Test
    fun `an anchor above the cap boundary does not shrink the retention`() =
        runTest {
            // The anchor only ever EXTENDS what is kept. A reader sitting near
            // the top must not cause a deeper trim than the cap asks for.
            coEvery { feedItemDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 99) } returns 120
            coEvery { feedItemDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 1499) } returns null
            coEvery { feedPostDao.positionAtOffsetFromTop(any(), any(), any(), any()) } returns null
            coEvery { feedPostDao.oldestReferencedByProjection(any(), any(), any()) } returns null

            repository(emptyList()).trimToCap(key, cap = 100, anchorItemPosition = 5)

            coVerify { feedItemDao.deleteOlderThan("did:plc:me", "FOLLOWING", "", 120) }
        }

    @Test
    fun `the ceiling bounds the anchor exception`() =
        runTest {
            // Otherwise a reader who never comes back to a deep position keeps
            // that partition alive forever.
            coEvery { feedItemDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 99) } returns 120
            coEvery { feedItemDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 299) } returns 400
            coEvery { feedPostDao.positionAtOffsetFromTop(any(), any(), any(), any()) } returns null
            coEvery { feedPostDao.oldestReferencedByProjection(any(), any(), any()) } returns null

            repository(emptyList()).trimToCap(key, cap = 100, anchorItemPosition = 9_000, ceiling = 300)

            coVerify { feedItemDao.deleteOlderThan("did:plc:me", "FOLLOWING", "", 400) }
        }

    @Test
    fun `entries the surviving projection references are kept`() =
        runTest {
            // Entry positions and item positions are different coordinate
            // spaces — a chain folds several entries into one item — so the
            // two grains are tied by content. An entry trimmed out from under a
            // surviving item would leave a re-projection unable to rebuild it.
            coEvery { feedItemDao.positionAtOffsetFromTop(any(), any(), any(), any()) } returns null
            coEvery { feedPostDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 99) } returns 100
            coEvery { feedPostDao.positionAtOffsetFromTop("did:plc:me", "FOLLOWING", "", 1499) } returns null
            coEvery { feedPostDao.oldestReferencedByProjection("did:plc:me", "FOLLOWING", "") } returns 175

            repository(emptyList()).trimToCap(key, cap = 100)

            coVerify { feedPostDao.deleteOlderThan("did:plc:me", "FOLLOWING", "", 175) }
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
            coVerify { partitionStateDao.clearAccount("did:plc:me") }
            coVerify { remoteKeyDao.clearAccount("did:plc:me") }
        }

    @Test
    fun `refresh delegates to FeedRefresher`() =
        runTest {
            // Empty page + null cursor -> endOfPaginationReached == true; the
            // delegated FeedRefresher fetches page 1 and writes through.
            coEvery { networkSource.fetchPage(key, cursor = null) } returns Result.success(FeedNetworkPage(emptyList<FeedViewPost>(), null))

            val result = repository(emptyList()).refresh(key)

            assertEquals(Result.success(true), result)
            coVerify { networkSource.fetchPage(key, cursor = null) }
            coVerify { feedPostDao.clearPartition("did:plc:me", "FOLLOWING", "") }
        }

    private val passthroughRunner =
        object : FeedCacheTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }

    @Test
    fun `head is undeduplicated - two entries in one thread both survive`() =
        runTest {
            // The projection would collapse these to one item: they share a
            // thread root, and thread-root de-duplication keeps the first. The
            // widget and the watch render a flat list and want both, so head
            // must not go through the projection.
            val rows =
                MutableStateFlow(
                    listOf(
                        entry("r1", text = "first reply", position = 0),
                        entry("r2", text = "second reply", position = 1),
                    ),
                )
            every { feedPostDao.head("did:plc:me", "FOLLOWING", "", 10) } returns rows
            val repo = repository(emptyList())

            repo.head(key, n = 10).test {
                assertEquals(listOf("first reply", "second reply"), awaitItem().map { it.text })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `head never reads the projection`() =
        runTest {
            // The structural guarantee behind D15, and the one a future change
            // is most likely to break: routing head through feed_item would
            // silently apply the reply tuners and de-duplication to the widget
            // and the watch, which curate nothing and expect every entry.
            val rows = MutableStateFlow(listOf(entry("a", text = "only", position = 0)))
            every { feedPostDao.head("did:plc:me", "FOLLOWING", "", 3) } returns rows
            val repo = repository(emptyList())

            repo.head(key, n = 3).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 0) { feedItemDao.itemsIn(any(), any(), any()) }
            coVerify(exactly = 0) { feedItemDao.itemsFrom(any(), any(), any(), any(), any()) }
        }

    /**
     * One joined entry row, as `head` reads them — built from the two
     * production write mappers so a test cannot serve a pairing the write path
     * would never produce.
     */
    private fun entry(
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

    /** One rendered item, as the projection would have produced it. */
    private fun item(
        tail: String,
        text: String,
        position: Int,
        labels: List<Pair<String, String>> = emptyList(),
    ): FeedItemUi =
        FeedItemUi.Single(
            requireNotNull(entry(tail, text, position, labels).toPostUi("did:plc:me", ModerationPrefs.DEFAULT)),
        )
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
    partitionStateDao: FeedPartitionStateDao,
    database: NubecitaDatabase,
    remoteKeyDao: FeedRemoteKeyDao,
    mediatorFactory: FeedRemoteMediator.Factory,
    sessionStateProvider: SessionStateProvider,
    moderationPreferences: ModerationPreferencesRepository,
    transactionRunner: FeedCacheTransactionRunner,
    feedRefresher: FeedRefresher,
    feedCatchUp: FeedCatchUp,
    private val testMediator: RemoteMediator<Int, FeedItemUi>,
    private val testRows: List<FeedItemUi>,
) : DefaultFeedRepository(
        feedPostDao,
        cachedPostDao,
        feedItemDao,
        partitionStateDao,
        database,
        remoteKeyDao,
        mediatorFactory,
        sessionStateProvider,
        moderationPreferences,
        transactionRunner,
        feedRefresher,
        feedCatchUp,
    ) {
    override fun mediatorFor(feedKey: FeedKey): RemoteMediator<Int, FeedItemUi> = testMediator

    override fun pagingSourceFor(
        feedKey: FeedKey,
        initialPosition: Int?,
    ): PagingSource<Int, FeedItemUi> = FakePagingSource(testRows)
}

/** In-memory single-page [PagingSource] over the seeded rows. */
private class FakePagingSource(
    private val rows: List<FeedItemUi>,
) : PagingSource<Int, FeedItemUi>() {
    override fun getRefreshKey(state: PagingState<Int, FeedItemUi>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedItemUi> = LoadResult.Page(data = rows, prevKey = null, nextKey = null)
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
