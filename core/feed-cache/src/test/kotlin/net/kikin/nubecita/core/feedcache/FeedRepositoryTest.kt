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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedPartitionStateEntity
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedItemUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        object : RemoteMediator<Int, CachedFeedRow>() {
            override suspend fun initialize() = InitializeAction.SKIP_INITIAL_REFRESH

            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, CachedFeedRow>,
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
                    feedItemDao = feedItemDao,
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
            assertEquals(
                "hello",
                items
                    .single()
                    .asSingle()
                    .post.text,
            )
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

            assertEquals(listOf("first", "second"), items.map { it.asSingle().post.text })
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

    // ---------- the reader's place ----------

    @Test
    fun `the anchor resolves by key, carrying its stored offset`() =
        runTest {
            // The key is what survives a re-projection moving the row, so it
            // is tried first and the position it resolves to may differ from
            // the one stored.
            coEvery { partitionStateDao.state("did:plc:me", "FOLLOWING", "") } returns
                FeedPartitionStateEntity(
                    "did:plc:me",
                    "FOLLOWING",
                    "",
                    anchorItemKey = "single:at://post/a",
                    anchorPosition = 40,
                    anchorOffset = -24,
                )
            coEvery { feedItemDao.positionOf("did:plc:me", "FOLLOWING", "", "single:at://post/a") } returns 37

            assertEquals(
                FeedAnchor(position = 37, itemKey = "single:at://post/a", offset = -24),
                repository(emptyList()).anchor(key),
            )
        }

    @Test
    fun `a dead anchor resolves downward, to the first surviving row below it`() =
        runTest {
            // D11: the items just under where the reader stopped are the ones
            // they had not read. Landing above would re-show what they
            // finished, which is the one outcome worse than landing at the top.
            coEvery { partitionStateDao.state("did:plc:me", "FOLLOWING", "") } returns
                FeedPartitionStateEntity(
                    "did:plc:me",
                    "FOLLOWING",
                    "",
                    anchorItemKey = "single:at://post/deleted",
                    anchorPosition = 40,
                    anchorOffset = -24,
                )
            coEvery { feedItemDao.positionOf(any(), any(), any(), any()) } returns null
            coEvery { feedItemDao.firstAtOrBelow("did:plc:me", "FOLLOWING", "", 40) } returns
                itemRow(position = 43, key = "single:at://post/next")

            val anchor = repository(emptyList()).anchor(key)

            assertEquals(43, anchor?.position)
            assertEquals("single:at://post/next", anchor?.itemKey)
            // Offset 0: the stored one measured a scroll into a row that is
            // gone, and a different row is a different height.
            assertEquals(0, anchor?.offset)
        }

    @Test
    fun `an anchor below everything that survived resolves to nothing`() =
        runTest {
            // The reader was in a stretch the trim has since evicted. Opening
            // at the top is the honest answer; there is no backlog left to
            // return them to.
            coEvery { partitionStateDao.state("did:plc:me", "FOLLOWING", "") } returns
                FeedPartitionStateEntity(
                    "did:plc:me",
                    "FOLLOWING",
                    "",
                    anchorItemKey = "single:at://post/evicted",
                    anchorPosition = 900,
                )
            coEvery { feedItemDao.positionOf(any(), any(), any(), any()) } returns null
            coEvery { feedItemDao.firstAtOrBelow(any(), any(), any(), any()) } returns null

            assertNull(repository(emptyList()).anchor(key))
        }

    @Test
    fun `a partition with no anchor restores nothing`() =
        runTest {
            coEvery { partitionStateDao.state(any(), any(), any()) } returns
                FeedPartitionStateEntity("did:plc:me", "FOLLOWING", "", prefsFingerprint = "replies=false")

            assertNull(repository(emptyList()).anchor(key))
        }

    @Test
    fun `a generator feed keeps no place, and asks the database nothing about one`() =
        runTest {
            // D14. A generator refreshes on open, so the partition a place
            // referred to is gone before anyone could be put back in it. The
            // gate lives here rather than at each screen, and the `coVerify`
            // is the point: reading or writing the row at all would mean a
            // later caller could still get a place out of it.
            val discover = FeedKey("did:plc:me", FeedType.DISCOVER, "at://feed/whats-hot")

            val repo = repository(emptyList())
            repo.saveAnchor(discover, itemKey = "single:at://post/a", offset = -8)

            assertNull(repo.anchor(discover))
            coVerify(exactly = 0) { partitionStateDao.saveAnchor(any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { partitionStateDao.state("did:plc:me", "DISCOVER", any()) }
        }

    @Test
    fun `saving an anchor resolves its position first`() =
        runTest {
            coEvery { feedItemDao.positionOf("did:plc:me", "FOLLOWING", "", "single:at://post/a") } returns 12

            repository(emptyList()).saveAnchor(key, itemKey = "single:at://post/a", offset = -8)

            coVerify {
                partitionStateDao.saveAnchor(
                    accountDid = "did:plc:me",
                    feedType = "FOLLOWING",
                    feedUri = "",
                    itemKey = "single:at://post/a",
                    position = 12,
                    offset = -8,
                )
            }
        }

    @Test
    fun `an anchor whose row has gone is not written at all`() =
        runTest {
            // A refresh landed between the scroll settling and this write.
            // Storing a position the key does not have would make the
            // dead-anchor fallback resolve from a stale place; the next settle
            // writes a current one.
            coEvery { feedItemDao.positionOf(any(), any(), any(), any()) } returns null

            repository(emptyList()).saveAnchor(key, itemKey = "single:at://post/gone", offset = 0)

            coVerify(exactly = 0) { partitionStateDao.saveAnchor(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `clearing the anchor leaves the rest of the partition state`() =
        runTest {
            repository(emptyList()).clearAnchor(key)

            coVerify { partitionStateDao.clearAnchor("did:plc:me", "FOLLOWING", "") }
            coVerify(exactly = 0) { partitionStateDao.upsert(any()) }
        }

    // ---------- the new-posts pill ----------

    @Test
    fun `entries written above the reader raise the pill`() =
        runTest {
            // The case the pill exists for: the background worker prepended
            // while the app was away, so there is no in-session event to
            // observe — only rows above where the reader stopped (D20).
            coEvery { partitionStateDao.observe("did:plc:me", "FOLLOWING", "") } returns
                flowOf(FeedPartitionStateEntity("did:plc:me", "FOLLOWING", "", anchorPosition = 12))
            coEvery {
                feedItemDao.leafPostsAbove("did:plc:me", "FOLLOWING", "", position = 12, limit = 15)
            } returns flowOf(listOf(cachedPost("at://new/1", "did:plc:nova"), cachedPost("at://new/2", "did:plc:theo")))

            val authors = repository(emptyList()).newPostAuthors(key).first()

            assertEquals(listOf("did:plc:nova", "did:plc:theo"), authors.map { it.did })
        }

    @Test
    fun `nothing above the reader means no pill`() =
        runTest {
            coEvery { partitionStateDao.observe(any(), any(), any()) } returns
                flowOf(FeedPartitionStateEntity("did:plc:me", "FOLLOWING", "", anchorPosition = 12))
            coEvery { feedItemDao.leafPostsAbove(any(), any(), any(), any(), any()) } returns flowOf(emptyList())

            assertEquals(emptyList<String>(), repository(emptyList()).newPostAuthors(key).first().map { it.did })
        }

    @Test
    fun `a dismissal, not the anchor, is the waterline once it exists`() =
        runTest {
            // Dismissing has to beat the anchor or the pill would come back on
            // the next composition, since the rows are still above the reader.
            coEvery { partitionStateDao.observe(any(), any(), any()) } returns
                flowOf(
                    FeedPartitionStateEntity(
                        "did:plc:me",
                        "FOLLOWING",
                        "",
                        anchorPosition = 12,
                        pillDismissedPosition = -250,
                    ),
                )
            coEvery { feedItemDao.leafPostsAbove(any(), any(), any(), any(), any()) } returns flowOf(emptyList())

            repository(emptyList()).newPostAuthors(key).first()

            // Queried above the dismissal, not above the anchor.
            coVerify { feedItemDao.leafPostsAbove("did:plc:me", "FOLLOWING", "", position = -250, limit = 15) }
        }

    @Test
    fun `with neither anchor nor dismissal the waterline is the top of the last refresh`() =
        runTest {
            // Position 0 is where a destructive refresh starts writing, so a
            // prepend's negative positions are above it and a reader who never
            // scrolled still gets told about new posts.
            coEvery { partitionStateDao.observe(any(), any(), any()) } returns flowOf(null)
            coEvery { feedItemDao.leafPostsAbove(any(), any(), any(), any(), any()) } returns flowOf(emptyList())

            repository(emptyList()).newPostAuthors(key).first()

            coVerify { feedItemDao.leafPostsAbove("did:plc:me", "FOLLOWING", "", position = 0, limit = 15) }
        }

    @Test
    fun `a burst from one account does not crowd out the people behind it`() =
        runTest {
            // The ordinary shape of an absence: one account posts a thread,
            // and two other people post once each behind it. De-duplicating a
            // query that stopped at three rows would show nova alone, so the
            // query reads further than the pill shows.
            coEvery { partitionStateDao.observe(any(), any(), any()) } returns flowOf(null)
            coEvery { feedItemDao.leafPostsAbove(any(), any(), any(), any(), any()) } returns
                flowOf(
                    listOf(
                        cachedPost("at://new/1", "did:plc:nova"),
                        cachedPost("at://new/2", "did:plc:nova"),
                        cachedPost("at://new/3", "did:plc:nova"),
                        cachedPost("at://new/4", "did:plc:theo"),
                        cachedPost("at://new/5", "did:plc:wren"),
                    ),
                )

            val authors = repository(emptyList()).newPostAuthors(key).first()

            assertEquals(listOf("did:plc:nova", "did:plc:theo", "did:plc:wren"), authors.map { it.did })
            // The mocked DAO hands back five rows whatever the limit, so the
            // over-fetch itself is pinned here rather than inferred from them.
            coVerify { feedItemDao.leafPostsAbove(any(), any(), any(), any(), limit = 15) }
        }

    @Test
    fun `more distinct people than the pill holds are cut to its width`() =
        runTest {
            // Over-fetching is for de-duplication, not for a wider pill.
            coEvery { partitionStateDao.observe(any(), any(), any()) } returns flowOf(null)
            coEvery { feedItemDao.leafPostsAbove(any(), any(), any(), any(), any()) } returns
                flowOf((1..6).map { cachedPost("at://new/$it", "did:plc:p$it") })

            assertEquals(3, repository(emptyList()).newPostAuthors(key).first().size)
        }

    @Test
    fun `the same author twice shows one face`() =
        runTest {
            coEvery { partitionStateDao.observe(any(), any(), any()) } returns flowOf(null)
            coEvery { feedItemDao.leafPostsAbove(any(), any(), any(), any(), any()) } returns
                flowOf(listOf(cachedPost("at://new/1", "did:plc:nova"), cachedPost("at://new/2", "did:plc:nova")))

            assertEquals(1, repository(emptyList()).newPostAuthors(key).first().size)
        }

    @Test
    fun `dismissing records the current top as the waterline`() =
        runTest {
            // Everything above the reader right now is what they acknowledged;
            // a later arrival lands above that mark and raises the pill again.
            coEvery { feedItemDao.minPosition("did:plc:me", "FOLLOWING", "") } returns -250

            repository(emptyList()).dismissNewPosts(key)

            coVerify { partitionStateDao.dismissPillAbove("did:plc:me", "FOLLOWING", "", -250) }
        }

    @Test
    fun `dismissing an empty partition writes nothing`() =
        runTest {
            coEvery { feedItemDao.minPosition(any(), any(), any()) } returns null

            repository(emptyList()).dismissNewPosts(key)

            coVerify(exactly = 0) { partitionStateDao.dismissPillAbove(any(), any(), any(), any()) }
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

    /** The rendered post inside a paged row — the shape every assertion here is about. */
    private fun CachedFeedRow.asSingle(): FeedItemUi.Single = (this as CachedFeedRow.Item).item as FeedItemUi.Single

    /** A stored post whose blob carries [authorDid] — what the pill reads faces from. */
    private fun cachedPost(
        uri: String,
        authorDid: String,
    ) = CachedPostEntity(
        accountDid = "did:plc:me",
        uri = uri,
        cid = "bafyreipill000000000000000000000000000000000000",
        authorDid = authorDid,
        postBlob =
            """
            {
              "${'$'}type": "app.bsky.feed.defs#postView",
              "uri": "$uri",
              "cid": "bafyreipill000000000000000000000000000000000000",
              "author": { "did": "$authorDid", "handle": "${authorDid.substringAfterLast(':')}.bsky.social" },
              "record": { "${'$'}type": "app.bsky.feed.post", "text": "new post", "createdAt": "2026-04-26T12:00:00Z" },
              "indexedAt": "2026-04-26T12:00:00Z"
            }
            """.trimIndent(),
    )

    /** A projection row as the DAO returns it — what the dead-anchor fallback resolves to. */
    private fun itemRow(
        position: Int,
        key: String,
    ) = FeedItemEntity(
        accountDid = "did:plc:me",
        feedType = "FOLLOWING",
        feedUri = "",
        position = position,
        rowType = FeedItemEntity.ROW_TYPE_ITEM,
        variant = "single",
        itemKey = key,
        leafUri = key.substringAfter(':'),
        suppressedReplyCount = 0,
    )

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
    private val testMediator: RemoteMediator<Int, CachedFeedRow>,
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
    override fun mediatorFor(feedKey: FeedKey): RemoteMediator<Int, CachedFeedRow> = testMediator

    override fun pagingSourceFor(
        feedKey: FeedKey,
        initialPosition: Int?,
    ): PagingSource<Int, CachedFeedRow> = FakePagingSource(testRows)
}

/** In-memory single-page [PagingSource] over the seeded rows. */
private class FakePagingSource(
    private val rows: List<FeedItemUi>,
) : PagingSource<Int, CachedFeedRow>() {
    override fun getRefreshKey(state: PagingState<Int, CachedFeedRow>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CachedFeedRow> = LoadResult.Page(data = rows.map(CachedFeedRow::Item), prevKey = null, nextKey = null)
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
