package net.kikin.nubecita.core.feeds

import app.cash.turbine.test
import io.github.kikin81.atproto.app.bsky.actor.SavedFeed
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.dao.SavedFeedDao
import net.kikin.nubecita.core.database.model.SavedFeedEntity
import net.kikin.nubecita.data.models.FeedKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultPinnedFeedsRepositoryTest {
    private val dataSource = mockk<FeedsDataSource>()
    private val dao = mockk<SavedFeedDao>(relaxed = true)

    private fun repo() =
        DefaultPinnedFeedsRepository(
            dataSource = dataSource,
            dao = dao,
            dispatcher = UnconfinedTestDispatcher(),
        )

    private fun savedFeed(
        id: String,
        type: String,
        value: String,
        pinned: Boolean,
    ): SavedFeed = SavedFeed(id = id, type = type, value = value, pinned = pinned)

    private fun generator(
        uri: String,
        displayName: String,
        avatar: String?,
    ): GeneratorMeta = GeneratorMeta(uri = uri, displayName = displayName, avatarUrl = avatar)

    private fun entity(
        uri: String,
        displayName: String = uri,
        creatorHandle: String? = null,
        avatarUrl: String? = null,
        pinned: Boolean = true,
        position: Int = 0,
    ): SavedFeedEntity =
        SavedFeedEntity(
            uri = uri,
            displayName = displayName,
            creatorHandle = creatorHandle,
            avatarUrl = avatarUrl,
            pinned = pinned,
            position = position,
        )

    // -------------------------------------------------------------------------
    // observePinnedFeeds() — read path
    // -------------------------------------------------------------------------

    @Test
    fun `observePinnedFeeds maps pinned entities to PinnedFeedUi in position order`() =
        runTest {
            every { dao.observeSavedFeeds() } returns
                flowOf(
                    listOf(
                        entity("following", displayName = "Following", pinned = true, position = 0),
                        entity(
                            "at://did:plc:x/app.bsky.feed.generator/art",
                            displayName = "Art",
                            avatarUrl = "https://cdn/art.jpg",
                            pinned = true,
                            position = 1,
                        ),
                        entity(
                            "at://did:plc:x/app.bsky.graph.list/friends",
                            displayName = "Friends",
                            pinned = true,
                            position = 2,
                        ),
                    ),
                )

            repo().observePinnedFeeds().test {
                val result = awaitItem()
                assertFalse(result.usedFallback)
                assertEquals(3, result.feeds.size)
                assertEquals("following", result.feeds[0].uri)
                assertEquals(FeedKind.Following, result.feeds[0].kind)
                assertEquals("Following", result.feeds[0].displayName)
                assertNull(result.feeds[0].avatarUrl)
                assertEquals("at://did:plc:x/app.bsky.feed.generator/art", result.feeds[1].uri)
                assertEquals(FeedKind.Generator, result.feeds[1].kind)
                assertEquals("Art", result.feeds[1].displayName)
                assertEquals("https://cdn/art.jpg", result.feeds[1].avatarUrl)
                assertEquals("at://did:plc:x/app.bsky.graph.list/friends", result.feeds[2].uri)
                assertEquals(FeedKind.List, result.feeds[2].kind)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `observePinnedFeeds filters out unpinned entities`() =
        runTest {
            every { dao.observeSavedFeeds() } returns
                flowOf(
                    listOf(
                        entity("following", displayName = "Following", pinned = true, position = 0),
                        entity(
                            "at://did:plc:x/app.bsky.feed.generator/art",
                            displayName = "Art",
                            pinned = false,
                            position = 1,
                        ),
                    ),
                )

            repo().observePinnedFeeds().test {
                val result = awaitItem()
                assertFalse(result.usedFallback)
                assertEquals(listOf("following"), result.feeds.map { it.uri })
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `observePinnedFeeds emits fallback when all entities are unpinned`() =
        runTest {
            every { dao.observeSavedFeeds() } returns
                flowOf(
                    listOf(
                        entity(
                            "at://did:plc:x/app.bsky.feed.generator/art",
                            pinned = false,
                            position = 0,
                        ),
                    ),
                )

            repo().observePinnedFeeds().test {
                val result = awaitItem()
                assertTrue(result.usedFallback)
                assertEquals(
                    listOf(
                        PinnedFeedsRepository.FOLLOWING_FEED_URI,
                        PinnedFeedsRepository.DISCOVER_FEED_URI,
                    ),
                    result.feeds.map { it.uri },
                )
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `observePinnedFeeds emits fallback when cache is empty`() =
        runTest {
            every { dao.observeSavedFeeds() } returns flowOf(emptyList())

            repo().observePinnedFeeds().test {
                val result = awaitItem()
                assertTrue(result.usedFallback)
                assertEquals(
                    listOf(
                        PinnedFeedsRepository.FOLLOWING_FEED_URI,
                        PinnedFeedsRepository.DISCOVER_FEED_URI,
                    ),
                    result.feeds.map { it.uri },
                )
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `observePinnedFeeds drops entities with unknown URI shapes`() =
        runTest {
            every { dao.observeSavedFeeds() } returns
                flowOf(
                    listOf(
                        entity("following", displayName = "Following", pinned = true, position = 0),
                        // Unrecognised URI — should be dropped silently.
                        entity("unknown-scheme://whatever", pinned = true, position = 1),
                    ),
                )

            repo().observePinnedFeeds().test {
                val result = awaitItem()
                assertFalse(result.usedFallback)
                assertEquals(listOf("following"), result.feeds.map { it.uri })
                cancelAndConsumeRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // refresh() — write path
    // -------------------------------------------------------------------------

    @Test
    fun `refresh upserts resolved entities and prunes stale URIs`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(
                    savedFeed("1", "timeline", "following", pinned = true),
                    savedFeed("2", "feed", "at://a", pinned = true),
                )
            coEvery { dataSource.getFeedGenerators(any()) } returns
                listOf(generator("at://a", "Feed A", "https://cdn/a.jpg"))

            val upsertSlot = slot<List<SavedFeedEntity>>()
            val pruneSlot = slot<List<String>>()
            coEvery { dao.upsert(capture(upsertSlot)) } returns Unit
            coEvery { dao.deleteUrisNotIn(capture(pruneSlot)) } returns Unit

            val result = repo().refresh()

            assertTrue(result.isSuccess)
            val entities = upsertSlot.captured
            assertEquals(2, entities.size)
            val followingEntity = entities.single { it.uri == "following" }
            assertEquals("Following", followingEntity.displayName)
            assertTrue(followingEntity.pinned)
            assertEquals(0, followingEntity.position)
            val feedAEntity = entities.single { it.uri == "at://a" }
            assertEquals("Feed A", feedAEntity.displayName)
            assertEquals("https://cdn/a.jpg", feedAEntity.avatarUrl)
            assertTrue(feedAEntity.pinned)
            assertEquals(1, feedAEntity.position)
            assertEquals(listOf("following", "at://a"), pruneSlot.captured.sorted().let { pruneSlot.captured })
            coVerify(exactly = 0) { dao.clear() }
        }

    @Test
    fun `refresh calls clear when no feeds resolve`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns emptyList()

            val result = repo().refresh()

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { dao.clear() }
            coVerify(exactly = 0) { dao.upsert(any()) }
            coVerify(exactly = 0) { dao.deleteUrisNotIn(any()) }
        }

    @Test
    fun `refresh calls clear when null savedFeedsPrefV2`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns null

            val result = repo().refresh()

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { dao.clear() }
            coVerify(exactly = 0) { dao.upsert(any()) }
        }

    @Test
    fun `refresh on getSavedFeedItems failure does not touch DAO`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } throws IOException("network down")

            val result = repo().refresh()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            coVerify(exactly = 0) { dao.upsert(any()) }
            coVerify(exactly = 0) { dao.deleteUrisNotIn(any()) }
            coVerify(exactly = 0) { dao.clear() }
        }

    @Test
    fun `refresh on getFeedGenerators failure does not touch DAO`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(
                    savedFeed("1", "timeline", "following", pinned = true),
                    savedFeed("2", "feed", "at://a", pinned = true),
                )
            coEvery { dataSource.getFeedGenerators(any()) } throws IOException("network down")

            val result = repo().refresh()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            coVerify(exactly = 0) { dao.upsert(any()) }
            coVerify(exactly = 0) { dao.deleteUrisNotIn(any()) }
            coVerify(exactly = 0) { dao.clear() }
        }

    @Test
    fun `refresh does not call getFeedGenerators when no generator pins exist`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(
                    savedFeed("1", "timeline", "following", pinned = true),
                    savedFeed("2", "list", "at://l", pinned = true),
                )
            coEvery { dao.upsert(any()) } returns Unit
            coEvery { dao.deleteUrisNotIn(any()) } returns Unit

            repo().refresh()

            coVerify(exactly = 0) { dataSource.getFeedGenerators(any()) }
        }

    @Test
    fun `refresh deduplicates timeline to a single following entity`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(
                    savedFeed("1", "timeline", "following", pinned = true),
                    savedFeed("2", "timeline", "following", pinned = true),
                    savedFeed("3", "feed", "at://a", pinned = true),
                )
            coEvery { dataSource.getFeedGenerators(any()) } returns
                listOf(generator("at://a", "Feed A", null))

            val upsertSlot = slot<List<SavedFeedEntity>>()
            coEvery { dao.upsert(capture(upsertSlot)) } returns Unit
            coEvery { dao.deleteUrisNotIn(any()) } returns Unit

            repo().refresh()

            val entities = upsertSlot.captured
            // Only one "following" entity despite two timeline saved feeds.
            assertEquals(1, entities.count { it.uri == "following" })
            assertEquals(2, entities.size) // following + Feed A
        }

    @Test
    fun `refresh writes list entity with URI as displayName`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(savedFeed("1", "list", "at://did:plc:x/app.bsky.graph.list/abc", pinned = true))

            val upsertSlot = slot<List<SavedFeedEntity>>()
            coEvery { dao.upsert(capture(upsertSlot)) } returns Unit
            coEvery { dao.deleteUrisNotIn(any()) } returns Unit

            repo().refresh()

            val entity = upsertSlot.captured.single()
            assertEquals("at://did:plc:x/app.bsky.graph.list/abc", entity.uri)
            // List display name falls back to the URI (no list-metadata endpoint).
            assertEquals("at://did:plc:x/app.bsky.graph.list/abc", entity.displayName)
            assertTrue(entity.pinned)
        }

    @Test
    fun `refresh upsert precedes deleteUrisNotIn (write-before-prune ordering)`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(savedFeed("1", "timeline", "following", pinned = true))
            coEvery { dao.upsert(any()) } returns Unit
            coEvery { dao.deleteUrisNotIn(any()) } returns Unit

            repo().refresh()

            // Verify that upsert is called before deleteUrisNotIn (not the other way around).
            coVerify(Ordering.ORDERED) {
                dao.upsert(any())
                dao.deleteUrisNotIn(any())
            }
        }

    @Test
    fun `refresh partial getFeedGenerators keeps unresolved-but-saved generator with URI fallback`() =
        runTest {
            // Two pinned generators, server returns metadata only for feed A.
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(
                    savedFeed("1", "feed", "at://a", pinned = true),
                    savedFeed("2", "feed", "at://b", pinned = true),
                )
            // Server returns only "at://a" — "at://b" is NOT in the response (partial result).
            coEvery { dataSource.getFeedGenerators(any()) } returns
                listOf(generator("at://a", "Feed A", null))
            // No existing cached row for at://b; URI is used as display name fallback.

            val upsertSlot = slot<List<SavedFeedEntity>>()
            val pruneSlot = slot<List<String>>()
            coEvery { dao.upsert(capture(upsertSlot)) } returns Unit
            coEvery { dao.deleteUrisNotIn(capture(pruneSlot)) } returns Unit

            val result = repo().refresh()

            // Should succeed — partial server response is not an error.
            assertTrue(result.isSuccess)
            // Both feeds are written (at://b is retained with URI as display name).
            val entities = upsertSlot.captured
            assertEquals(2, entities.size)
            val feedA = entities.single { it.uri == "at://a" }
            assertEquals("Feed A", feedA.displayName)
            val feedB = entities.single { it.uri == "at://b" }
            assertEquals("at://b", feedB.displayName) // URI as last-resort display name
            assertNull(feedB.avatarUrl)
            // Prune key is the saved-prefs set — both feeds are kept.
            assertEquals(listOf("at://a", "at://b"), pruneSlot.captured)
        }

    @Test
    fun `refresh partial getFeedGenerators retains existing cached metadata for unresolved generator`() =
        runTest {
            // Two pinned generators, server returns metadata only for feed A.
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(
                    savedFeed("1", "feed", "at://a", pinned = true),
                    savedFeed("2", "feed", "at://b", pinned = true),
                )
            coEvery { dataSource.getFeedGenerators(any()) } returns
                listOf(generator("at://a", "Feed A", null))
            // at://b has a stale but valid cached row from a previous refresh.
            coEvery { dao.getAllOnce() } returns
                listOf(
                    entity(
                        uri = "at://b",
                        displayName = "Cool Feed B",
                        avatarUrl = "https://cdn/b.jpg",
                        pinned = true,
                        position = 0,
                    ),
                )

            val upsertSlot = slot<List<SavedFeedEntity>>()
            coEvery { dao.upsert(capture(upsertSlot)) } returns Unit
            coEvery { dao.deleteUrisNotIn(any()) } returns Unit

            val result = repo().refresh()

            assertTrue(result.isSuccess)
            val entities = upsertSlot.captured
            assertEquals(2, entities.size)
            val feedB = entities.single { it.uri == "at://b" }
            // Retained from the existing cache, not replaced with URI fallback.
            assertEquals("Cool Feed B", feedB.displayName)
            assertEquals("https://cdn/b.jpg", feedB.avatarUrl)
        }

    @Test
    fun `refresh prune set is keyed on saved prefs so removed feeds are pruned but partial-response feeds are not`() =
        runTest {
            // Prefs: at://a (saved). at://b is NOT in prefs (was there before, now gone).
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(savedFeed("1", "feed", "at://a", pinned = true))
            coEvery { dataSource.getFeedGenerators(any()) } returns
                listOf(generator("at://a", "Feed A", null))

            val pruneSlot = slot<List<String>>()
            coEvery { dao.upsert(any()) } returns Unit
            coEvery { dao.deleteUrisNotIn(capture(pruneSlot)) } returns Unit

            repo().refresh()

            // Prune set = prefs URIs only; at://b (not in prefs) will be deleted by Room.
            assertEquals(listOf("at://a"), pruneSlot.captured)
        }

    // -------------------------------------------------------------------------
    // validateSelectedFeedUri — unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `validateSelectedFeedUri keeps a still-pinned uri`() {
        val pinned =
            DefaultPinnedFeedsRepository.fallbackFeeds().toMutableList().also {
                it.add(
                    net.kikin.nubecita.data.models.PinnedFeedUi(
                        id = "art",
                        uri = "at://art",
                        kind = FeedKind.Generator,
                        displayName = "Art",
                        avatarUrl = null,
                    ),
                )
            }

        assertEquals("at://art", repo().validateSelectedFeedUri("at://art", pinned))
    }

    @Test
    fun `validateSelectedFeedUri falls back to Following when the uri is no longer pinned`() {
        val pinned = DefaultPinnedFeedsRepository.fallbackFeeds()

        assertEquals(
            PinnedFeedsRepository.FOLLOWING_FEED_URI,
            repo().validateSelectedFeedUri("at://stale", pinned),
        )
    }

    @Test
    fun `validateSelectedFeedUri falls back to Following for a null persisted uri`() {
        val pinned = DefaultPinnedFeedsRepository.fallbackFeeds()

        assertEquals(
            PinnedFeedsRepository.FOLLOWING_FEED_URI,
            repo().validateSelectedFeedUri(null, pinned),
        )
    }
}
