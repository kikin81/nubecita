package net.kikin.nubecita.core.feeds

import app.cash.turbine.test
import io.github.kikin81.atproto.app.bsky.actor.AdultContentPref
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequestPreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.SavedFeed
import io.github.kikin81.atproto.app.bsky.actor.SavedFeedsPrefV2
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
        creatorHandle: String? = null,
    ): GeneratorMeta = GeneratorMeta(uri = uri, displayName = displayName, avatarUrl = avatar, creatorHandle = creatorHandle)

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
            // List display name uses the record key (last path segment) as a readable placeholder.
            assertEquals("abc", entity.displayName)
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
            assertEquals("b", feedB.displayName) // record key as readable placeholder (last URI segment)
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

    // -------------------------------------------------------------------------
    // pinFeed() — write path
    // -------------------------------------------------------------------------

    /**
     * Helper that builds a full preferences list containing one SavedFeedsPrefV2
     * plus an optional foreign preference, for use in pin/unpin tests.
     */
    private fun prefsWithFeeds(
        items: List<SavedFeed>,
        vararg foreign: GetPreferencesResponsePreferencesUnion,
    ): List<GetPreferencesResponsePreferencesUnion> = foreign.toList() + SavedFeedsPrefV2(items = items)

    @Test
    fun `pinFeed sets pinned=true on existing unpinned item and preserves foreign prefs`() =
        runTest {
            val foreignPref = AdultContentPref(enabled = false)
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items = listOf(savedFeed("1", "feed", "at://a", pinned = false)),
                    foreign = arrayOf(foreignPref),
                )
            val putSlot = slot<List<PutPreferencesRequestPreferencesUnion>>()
            coEvery { dataSource.putPreferences(capture(putSlot)) } returns Unit

            val result = repo().pinFeed("at://a")

            assertTrue(result.isSuccess)
            // Room optimistic write
            coVerify { dao.setPinned("at://a", true) }
            // putPreferences must carry a SavedFeedsPrefV2 with item pinned=true
            val putPrefs = putSlot.captured
            val updatedFeeds = putPrefs.filterIsInstance<SavedFeedsPrefV2>().single()
            val updated = updatedFeeds.items.single { it.value == "at://a" }
            assertTrue(updated.pinned)
            // Foreign pref (AdultContentPref) must be preserved
            assertTrue(putPrefs.any { it is AdultContentPref }, "foreign pref must survive the round-trip")
        }

    @Test
    fun `pinFeed adds new SavedFeed when uri not in saved feeds and Room upserts the new row`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items = listOf(savedFeed("1", "feed", "at://a", pinned = false)),
                )
            val putSlot = slot<List<PutPreferencesRequestPreferencesUnion>>()
            coEvery { dataSource.putPreferences(capture(putSlot)) } returns Unit
            val upsertSlot = slot<List<SavedFeedEntity>>()
            coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

            val result = repo().pinFeed("at://new")

            assertTrue(result.isSuccess)
            // Room upsert called with the new entity
            coVerify { dao.upsert(any()) }
            val upserted = upsertSlot.captured.single()
            assertEquals("at://new", upserted.uri)
            assertTrue(upserted.pinned)
            // putPreferences includes the new item pinned=true AND the original item
            val updatedFeeds = putSlot.captured.filterIsInstance<SavedFeedsPrefV2>().single()
            assertTrue(updatedFeeds.items.any { it.value == "at://new" && it.pinned })
            assertTrue(updatedFeeds.items.any { it.value == "at://a" }, "existing items must be preserved")
        }

    @Test
    fun `pinFeed is idempotent when feed is already pinned — skips network and Room writes`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items = listOf(savedFeed("1", "feed", "at://a", pinned = true)),
                )

            val result = repo().pinFeed("at://a")

            assertTrue(result.isSuccess)
            // Early-return path: no Room write and no network round-trip.
            coVerify(exactly = 0) { dao.setPinned(any(), any()) }
            coVerify(exactly = 0) { dataSource.putPreferences(any()) }
        }

    @Test
    fun `pinFeed rolls back Room setPinned when putPreferences fails`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items = listOf(savedFeed("1", "feed", "at://a", pinned = false)),
                )
            coEvery { dataSource.putPreferences(any()) } throws IOException("network error")

            val result = repo().pinFeed("at://a")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            // Optimistic write then rollback (in order)
            coVerify(Ordering.ORDERED) {
                dao.setPinned("at://a", true)
                dao.setPinned("at://a", false)
            }
        }

    @Test
    fun `pinFeed new item rollback calls deleteByUri not setPinned so no ghost row remains`() =
        runTest {
            // The URI has never been seen — it is absent from saved prefs entirely.
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(items = emptyList())
            coEvery { dataSource.putPreferences(any()) } throws IOException("network error")

            val result = repo().pinFeed("at://never-seen")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            // Optimistic upsert then rollback via deleteByUri (not setPinned), in order.
            coVerify(Ordering.ORDERED) {
                dao.upsert(any())
                dao.deleteByUri("at://never-seen")
            }
            // setPinned must NOT be called for a brand-new item rollback.
            coVerify(exactly = 0) { dao.setPinned(any(), any()) }
        }

    // -------------------------------------------------------------------------
    // unpinFeed() — write path
    // -------------------------------------------------------------------------

    @Test
    fun `unpinFeed sets pinned=false and item remains in SavedFeedsPrefV2 items (non-destructive)`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items =
                        listOf(
                            savedFeed("1", "feed", "at://a", pinned = true),
                            savedFeed("2", "feed", "at://b", pinned = true),
                        ),
                )
            val putSlot = slot<List<PutPreferencesRequestPreferencesUnion>>()
            coEvery { dataSource.putPreferences(capture(putSlot)) } returns Unit

            val result = repo().unpinFeed("at://a")

            assertTrue(result.isSuccess)
            coVerify { dao.setPinned("at://a", false) }
            val updatedFeeds = putSlot.captured.filterIsInstance<SavedFeedsPrefV2>().single()
            // Item is NOT removed — only pinned flag changes
            assertEquals(2, updatedFeeds.items.size, "both items must stay in the list")
            val unpinned = updatedFeeds.items.single { it.value == "at://a" }
            assertFalse(unpinned.pinned)
            // Other item is untouched
            assertTrue(updatedFeeds.items.single { it.value == "at://b" }.pinned)
        }

    @Test
    fun `unpinFeed rolls back Room setPinned when putPreferences fails`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items = listOf(savedFeed("1", "feed", "at://a", pinned = true)),
                )
            coEvery { dataSource.putPreferences(any()) } throws IOException("network error")

            val result = repo().unpinFeed("at://a")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            coVerify(Ordering.ORDERED) {
                dao.setPinned("at://a", false) // optimistic
                dao.setPinned("at://a", true) // rollback
            }
        }

    // -------------------------------------------------------------------------
    // Serialization under writeMutex
    // -------------------------------------------------------------------------

    @Test
    fun `two concurrent pinFeed calls each call getFullPreferences and putPreferences`() =
        runTest {
            // Both calls succeed; verify getFullPreferences and putPreferences are
            // each invoked once per call (mutex serializes them, not batches them).
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(items = listOf(savedFeed("1", "feed", "at://a", pinned = false)))
            coEvery { dataSource.putPreferences(any()) } returns Unit

            val repo = repo()
            val j1 = launch { repo.pinFeed("at://a") }
            val j2 = launch { repo.pinFeed("at://a") }
            j1.join()
            j2.join()

            coVerify(exactly = 2) { dataSource.getFullPreferences() }
            coVerify(exactly = 2) { dataSource.putPreferences(any()) }
        }

    // -------------------------------------------------------------------------
    // pinFeed / unpinFeed idempotency — Fix 2
    // -------------------------------------------------------------------------

    @Test
    fun `unpinFeed feed not in saved list returns success without any write`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items = listOf(savedFeed("1", "feed", "at://other", pinned = true)),
                )

            val result = repo().unpinFeed("at://not-saved")

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { dao.setPinned(any(), any()) }
            coVerify(exactly = 0) { dataSource.putPreferences(any()) }
        }

    @Test
    fun `unpinFeed already-unpinned feed returns success without any write`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items = listOf(savedFeed("1", "feed", "at://a", pinned = false)),
                )

            val result = repo().unpinFeed("at://a")

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { dao.setPinned(any(), any()) }
            coVerify(exactly = 0) { dataSource.putPreferences(any()) }
        }

    // -------------------------------------------------------------------------
    // pinFeed new-item placeholder uses record key — Fix 3
    // -------------------------------------------------------------------------

    @Test
    fun `pinFeed new item uses record key as placeholder displayName`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns prefsWithFeeds(items = emptyList())
            coEvery { dataSource.putPreferences(any()) } returns Unit
            val upsertSlot = slot<List<SavedFeedEntity>>()
            coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

            repo().pinFeed("at://did:plc:x/app.bsky.feed.generator/whats-hot")

            val upserted = upsertSlot.captured.single()
            assertEquals("whats-hot", upserted.displayName)
        }

    // -------------------------------------------------------------------------
    // creatorHandle populated from GeneratorMeta — Fix 4
    // -------------------------------------------------------------------------

    @Test
    fun `refresh generator entity captures creator handle from getFeedGenerators`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(savedFeed("1", "feed", "at://a", pinned = true))
            coEvery { dataSource.getFeedGenerators(any()) } returns
                listOf(generator("at://a", "Feed A", null, creatorHandle = "alice.bsky.social"))

            val upsertSlot = slot<List<SavedFeedEntity>>()
            coEvery { dao.upsert(capture(upsertSlot)) } returns Unit
            coEvery { dao.deleteUrisNotIn(any()) } returns Unit

            val result = repo().refresh()

            assertTrue(result.isSuccess)
            val feedA = upsertSlot.captured.single { it.uri == "at://a" }
            assertEquals("alice.bsky.social", feedA.creatorHandle)
        }

    @Test
    fun `refresh unresolved generator falls back to existing cached creator handle`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(savedFeed("1", "feed", "at://b", pinned = true))
            // Server returns nothing for at://b (partial response).
            coEvery { dataSource.getFeedGenerators(any()) } returns emptyList()
            coEvery { dao.getAllOnce() } returns
                listOf(
                    entity(
                        uri = "at://b",
                        displayName = "Cool B",
                        creatorHandle = "bob.bsky.social",
                        pinned = true,
                    ),
                )
            val upsertSlot = slot<List<SavedFeedEntity>>()
            coEvery { dao.upsert(capture(upsertSlot)) } returns Unit
            coEvery { dao.deleteUrisNotIn(any()) } returns Unit

            val result = repo().refresh()

            assertTrue(result.isSuccess)
            val feedB = upsertSlot.captured.single { it.uri == "at://b" }
            assertEquals("bob.bsky.social", feedB.creatorHandle)
        }

    // -------------------------------------------------------------------------
    // reorderPinnedFeeds() — reorder path (nubecita-ydfn.2)
    // -------------------------------------------------------------------------

    @Test
    fun `reorderPinnedFeeds writes the new pinned order to putPreferences and Room`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items =
                        listOf(
                            savedFeed("t", "timeline", "following", pinned = true),
                            savedFeed("a", "feed", "at://a", pinned = true),
                            savedFeed("b", "feed", "at://b", pinned = true),
                        ),
                )
            val putSlot = slot<List<PutPreferencesRequestPreferencesUnion>>()
            coEvery { dataSource.putPreferences(capture(putSlot)) } returns Unit
            val posSlot = slot<List<String>>()
            coEvery { dao.updatePositions(capture(posSlot)) } returns Unit

            val result = repo().reorderPinnedFeeds(listOf("at://b", "following", "at://a"))

            assertTrue(result.isSuccess)
            val items =
                putSlot.captured
                    .filterIsInstance<SavedFeedsPrefV2>()
                    .single()
                    .items
            assertEquals(listOf("at://b", "following", "at://a"), items.map { it.value })
            // Room positions rewritten to the same new order (timeline → "following" PK).
            assertEquals(listOf("at://b", "following", "at://a"), posSlot.captured)
        }

    @Test
    fun `reorderPinnedFeeds appends a server-pinned feed missing from the local order`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items =
                        listOf(
                            savedFeed("a", "feed", "at://a", pinned = true),
                            savedFeed("b", "feed", "at://b", pinned = true),
                            savedFeed("c", "feed", "at://c", pinned = true),
                        ),
                )
            val putSlot = slot<List<PutPreferencesRequestPreferencesUnion>>()
            coEvery { dataSource.putPreferences(capture(putSlot)) } returns Unit

            // Local list only knows B and A; C was pinned on another client after load.
            val result = repo().reorderPinnedFeeds(listOf("at://b", "at://a"))

            assertTrue(result.isSuccess)
            val items =
                putSlot.captured
                    .filterIsInstance<SavedFeedsPrefV2>()
                    .single()
                    .items
            // C appended in server order — never dropped, still pinned.
            assertEquals(listOf("at://b", "at://a", "at://c"), items.map { it.value })
            assertTrue(items.single { it.value == "at://c" }.pinned)
        }

    @Test
    fun `reorderPinnedFeeds keeps unpinned saved feeds after the pinned block`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items =
                        listOf(
                            savedFeed("a", "feed", "at://a", pinned = true),
                            savedFeed("x", "feed", "at://x", pinned = false),
                            savedFeed("b", "feed", "at://b", pinned = true),
                        ),
                )
            val putSlot = slot<List<PutPreferencesRequestPreferencesUnion>>()
            coEvery { dataSource.putPreferences(capture(putSlot)) } returns Unit

            val result = repo().reorderPinnedFeeds(listOf("at://b", "at://a"))

            assertTrue(result.isSuccess)
            val items =
                putSlot.captured
                    .filterIsInstance<SavedFeedsPrefV2>()
                    .single()
                    .items
            // Pinned reordered, then the unpinned entry preserved (still unpinned).
            assertEquals(listOf("at://b", "at://a", "at://x"), items.map { it.value })
            assertFalse(items.single { it.value == "at://x" }.pinned)
        }

    @Test
    fun `reorderPinnedFeeds preserves foreign preferences`() =
        runTest {
            val foreignPref = AdultContentPref(enabled = false)
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items =
                        listOf(
                            savedFeed("a", "feed", "at://a", pinned = true),
                            savedFeed("b", "feed", "at://b", pinned = true),
                        ),
                    foreign = arrayOf(foreignPref),
                )
            val putSlot = slot<List<PutPreferencesRequestPreferencesUnion>>()
            coEvery { dataSource.putPreferences(capture(putSlot)) } returns Unit

            val result = repo().reorderPinnedFeeds(listOf("at://b", "at://a"))

            assertTrue(result.isSuccess)
            assertTrue(putSlot.captured.any { it is AdultContentPref }, "foreign pref must survive the round-trip")
        }

    @Test
    fun `reorderPinnedFeeds rolls back Room positions when putPreferences fails`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items =
                        listOf(
                            savedFeed("a", "feed", "at://a", pinned = true),
                            savedFeed("b", "feed", "at://b", pinned = true),
                        ),
                )
            coEvery { dataSource.putPreferences(any()) } throws IOException("network error")

            val result = repo().reorderPinnedFeeds(listOf("at://b", "at://a"))

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            // Optimistic new order, then rollback to the prior order (in order).
            coVerify(Ordering.ORDERED) {
                dao.updatePositions(listOf("at://b", "at://a"))
                dao.updatePositions(listOf("at://a", "at://b"))
            }
        }

    @Test
    fun `reorderPinnedFeeds is a no-op when the resulting order is unchanged`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items =
                        listOf(
                            savedFeed("a", "feed", "at://a", pinned = true),
                            savedFeed("b", "feed", "at://b", pinned = true),
                        ),
                )

            val result = repo().reorderPinnedFeeds(listOf("at://a", "at://b"))

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { dataSource.putPreferences(any()) }
            coVerify(exactly = 0) { dao.updatePositions(any()) }
        }

    @Test
    fun `reorderPinnedFeeds dedupes a duplicated uri in the requested order`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items =
                        listOf(
                            savedFeed("a", "feed", "at://a", pinned = true),
                            savedFeed("b", "feed", "at://b", pinned = true),
                        ),
                )
            val putSlot = slot<List<PutPreferencesRequestPreferencesUnion>>()
            coEvery { dataSource.putPreferences(capture(putSlot)) } returns Unit
            val posSlot = slot<List<String>>()
            coEvery { dao.updatePositions(capture(posSlot)) } returns Unit

            // Caller repeats at://a — must not produce a duplicate entry/position.
            val result = repo().reorderPinnedFeeds(listOf("at://b", "at://a", "at://a"))

            assertTrue(result.isSuccess)
            val items =
                putSlot.captured
                    .filterIsInstance<SavedFeedsPrefV2>()
                    .single()
                    .items
            assertEquals(listOf("at://b", "at://a"), items.map { it.value })
            assertEquals(listOf("at://b", "at://a"), posSlot.captured)
        }

    @Test
    fun `reorderPinnedFeeds collapses duplicate timeline entries to a single following row`() =
        runTest {
            coEvery { dataSource.getFullPreferences() } returns
                prefsWithFeeds(
                    items =
                        listOf(
                            savedFeed("t1", "timeline", "following", pinned = true),
                            savedFeed("t2", "timeline", "following", pinned = true),
                            savedFeed("a", "feed", "at://a", pinned = true),
                        ),
                )
            val putSlot = slot<List<PutPreferencesRequestPreferencesUnion>>()
            coEvery { dataSource.putPreferences(capture(putSlot)) } returns Unit

            // Requested order omits "following"; the two timeline entries must not
            // both survive (they collide on the same "following" Room key).
            val result = repo().reorderPinnedFeeds(listOf("at://a"))

            assertTrue(result.isSuccess)
            val items =
                putSlot.captured
                    .filterIsInstance<SavedFeedsPrefV2>()
                    .single()
                    .items
            assertEquals(listOf("at://a", "following"), items.map { it.value })
        }
}
