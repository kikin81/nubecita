package net.kikin.nubecita.core.wearsync

import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feedcache.FeedType
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsResult
import net.kikin.nubecita.core.preferences.WatchFeedsPreference
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDecodeResult
import net.kikin.nubecita.core.wearcontract.WearFeedKey
import net.kikin.nubecita.core.wearcontract.WearFeedSnapshot
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearsync.internal.WearDataLayer
import net.kikin.nubecita.core.wearsync.internal.WearImageEncoder
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import net.kikin.nubecita.data.models.PostUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class DefaultWearFeedSyncTest {
    private val did = "did:plc:viewer"
    private val catsUri = "at://did:plc:x/app.bsky.feed.generator/cats"
    private val listUri = "at://did:plc:x/app.bsky.graph.list/friends"
    private val discoverUri = PinnedFeedsRepository.DISCOVER_FEED_URI

    private val dataLayer = FakeDataLayer()
    private val selection = MutableStateFlow(setOf(WatchFeedsPreference.FOLLOWING_FEED_URI))
    private val heads = mutableMapOf<FeedKey, List<PostUi>>()

    private fun sync(): DefaultWearFeedSync {
        val preference =
            object : WatchFeedsPreference {
                override val selectedFeedUris = selection

                override suspend fun setSelectedFeedUris(uris: Set<String>) = Unit
            }
        val pinned =
            mockk<PinnedFeedsRepository> {
                every { observePinnedFeeds() } returns
                    flowOf(
                        PinnedFeedsResult(
                            persistentListOf(
                                PinnedFeedUi(catsUri, catsUri, FeedKind.Generator, "Cats", null),
                                PinnedFeedUi(discoverUri, discoverUri, FeedKind.Generator, "Discover", null),
                                PinnedFeedUi(listUri, listUri, FeedKind.List, "Friends", null),
                            ),
                            usedFallback = false,
                        ),
                    )
            }
        val feeds =
            mockk<FeedRepository> {
                every { head(any(), any()) } answers { flowOf(heads[firstArg()].orEmpty()) }
            }
        val encoder =
            object : WearImageEncoder {
                override suspend fun encode(
                    url: String,
                    maxPx: Int,
                ): ByteArray? = if ("broken" in url) null else url.encodeToByteArray()
            }
        val clock =
            object : Clock {
                override fun now() = Instant.fromEpochMilliseconds(42)
            }
        return DefaultWearFeedSync(dataLayer, preference, pinned, feeds, encoder, clock)
    }

    @Test
    fun without_a_watch_nothing_is_refreshed_or_published() =
        runTest {
            dataLayer.hasWatch = false
            heads[FeedKey.following(did)] = listOf(testPost("p1"))

            assertTrue(sync().feedKeysToRefresh(did).isEmpty())
            sync().publish(did)

            assertTrue(dataLayer.puts.isEmpty())
        }

    @Test
    fun selected_uris_map_to_their_cache_partitions() =
        runTest {
            selection.value = setOf(WatchFeedsPreference.FOLLOWING_FEED_URI, PinnedFeedsRepository.DISCOVER_FEED_URI, catsUri, listUri)

            val keys = sync().feedKeysToRefresh(did).map { it.feedType }

            assertEquals(listOf(FeedType.FOLLOWING, FeedType.DISCOVER, FeedType.CUSTOM, FeedType.LIST), keys)
        }

    @Test
    fun publishes_one_non_urgent_snapshot_per_feed_with_its_images() =
        runTest {
            selection.value = setOf(WatchFeedsPreference.FOLLOWING_FEED_URI, catsUri)
            heads[FeedKey.following(did)] = listOf(testPost("p1"))
            heads[FeedKey(did, FeedType.CUSTOM, catsUri)] = listOf(testPost("p2"))

            sync().publish(did)

            assertEquals(2, dataLayer.puts.size)
            assertTrue(dataLayer.puts.none { it.urgent })
            val following = dataLayer.snapshot(WearPaths.feed(did, WearFeedKey.FOLLOWING))
            assertNull(following.feedName) // the watch localizes "Following"
            assertEquals(42L, following.publishedAtMs)
            val cats = dataLayer.snapshot(WearPaths.feed(did, WearFeedKey.forFeedUri(catsUri)))
            assertEquals("Cats", cats.feedName)
            val put = dataLayer.puts.first()
            assertEquals(setOf("a0"), put.assets.keys) // one avatar, encoded once
        }

    @Test
    fun an_image_that_fails_to_load_is_not_referenced() =
        runTest {
            heads[FeedKey.following(did)] = listOf(testPost("p1", avatarUrl = "https://cdn/broken.jpg"))

            sync().publish(did)

            val post = dataLayer.snapshot(WearPaths.feed(did, WearFeedKey.FOLLOWING)).posts.single()
            assertNull(post.author.avatarAssetKey)
            assertTrue(
                dataLayer.puts
                    .single()
                    .assets
                    .isEmpty(),
            )
        }

    @Test
    fun an_empty_cache_is_skipped_not_published_empty() =
        runTest {
            sync().publish(did)

            assertTrue(dataLayer.puts.isEmpty())
        }

    @Test
    fun an_empty_cache_keeps_the_watchs_previous_snapshot() =
        runTest {
            val previous = WearPaths.feed(did, WearFeedKey.FOLLOWING)
            dataLayer.existing += previous

            sync().publish(did)

            assertTrue(dataLayer.deleted.isEmpty())
        }

    @Test
    fun every_image_in_a_feed_is_encoded() =
        runTest {
            val posts =
                (1..12).map {
                    testPost("p$it", authorDid = "did:plc:a$it", avatarUrl = "https://cdn/avatar/$it.jpg")
                }
            heads[FeedKey.following(did)] = posts

            sync().publish(did)

            assertEquals(
                12,
                dataLayer.puts
                    .single()
                    .assets.size,
            )
        }

    @Test
    fun snapshots_of_deselected_feeds_are_deleted() =
        runTest {
            val stale = WearPaths.feed(did, WearFeedKey.forFeedUri(catsUri))
            dataLayer.existing += stale
            heads[FeedKey.following(did)] = listOf(testPost("p1"))

            sync().publish(did)

            assertEquals(listOf(stale), dataLayer.deleted)
        }

    @Test
    fun deleting_a_stale_feed_whose_key_prefixes_a_kept_one_spares_the_kept_feed() =
        runTest {
            val kept = WearPaths.feed(did, WearFeedKey.FOLLOWING)
            // A prefix delete of ".../follow" would also wipe ".../following".
            val stale = WearPaths.feed(did, "follow")
            dataLayer.existing += listOf(kept, stale)
            heads[FeedKey.following(did)] = listOf(testPost("p1"))

            sync().publish(did)

            assertEquals(listOf(stale), dataLayer.deleted)
        }

    @Test
    fun an_unpinned_selection_stops_syncing_and_falls_back_to_following() =
        runTest {
            selection.value = setOf("at://did:plc:x/app.bsky.feed.generator/no-longer-pinned")

            assertEquals(listOf(FeedKey.following(did)), sync().feedKeysToRefresh(did))
        }

    @Test
    fun an_empty_selection_falls_back_to_following() =
        runTest {
            selection.value = emptySet()

            assertEquals(listOf(FeedKey.following(did)), sync().feedKeysToRefresh(did))
        }

    private data class Put(
        val path: String,
        val json: String,
        val urgent: Boolean,
        val assets: Map<String, ByteArray>,
    )

    private class FakeDataLayer : WearDataLayer {
        var hasWatch = true
        val puts = mutableListOf<Put>()
        val existing = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        fun snapshot(path: String): WearFeedSnapshot = (WearCodec.decode(WearFeedSnapshot.serializer(), puts.single { it.path == path }.json) as WearDecodeResult.Decoded).value

        override suspend fun hasWearAppNode() = hasWatch

        override suspend fun put(
            path: String,
            json: String,
            urgent: Boolean,
            assets: Map<String, ByteArray>,
        ) {
            puts += Put(path, json, urgent, assets)
            existing += path
        }

        override suspend fun deleteUnder(pathPrefix: String) = error("feed sync must delete exact paths only")

        override suspend fun delete(path: String) {
            deleted += path
        }

        override suspend fun pathsUnder(pathPrefix: String): List<String> = existing.filter { it.startsWith(pathPrefix) }
    }
}
