package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.SavedFeed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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

    private fun repo() =
        DefaultPinnedFeedsRepository(
            dataSource = dataSource,
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

    @Test
    fun `pinned feeds are returned in stored order and unpinned items are dropped`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(
                    savedFeed("1", "timeline", "following", pinned = true),
                    savedFeed("2", "feed", "at://a", pinned = true),
                    savedFeed("3", "feed", "at://b", pinned = false),
                    savedFeed("4", "list", "at://l", pinned = true),
                )
            coEvery { dataSource.getFeedGenerators(any()) } returns
                listOf(generator("at://a", "Feed A", "https://cdn/a.jpg"))

            val result = repo().loadPinnedFeeds()

            assertFalse(result.usedFallback)
            assertEquals(listOf("following", "at://a", "at://l"), result.feeds.map { it.uri })
            assertEquals(
                listOf(FeedKind.Following, FeedKind.Generator, FeedKind.List),
                result.feeds.map { it.kind },
            )
        }

    @Test
    fun `generator metadata is hydrated via a single batch call`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(
                    savedFeed("1", "feed", "at://a", pinned = true),
                    savedFeed("2", "feed", "at://b", pinned = true),
                )
            val urisSlot = slot<List<String>>()
            coEvery { dataSource.getFeedGenerators(capture(urisSlot)) } returns
                listOf(
                    generator("at://a", "Feed A", "https://cdn/a.jpg"),
                    generator("at://b", "Feed B", null),
                )

            val result = repo().loadPinnedFeeds()

            // Exactly one batch call carrying both URIs in order.
            coVerify(exactly = 1) { dataSource.getFeedGenerators(any()) }
            assertEquals(listOf("at://a", "at://b"), urisSlot.captured)

            val a = result.feeds.single { it.uri == "at://a" }
            assertEquals("Feed A", a.displayName)
            assertEquals("https://cdn/a.jpg", a.avatarUrl)
            val b = result.feeds.single { it.uri == "at://b" }
            assertEquals("Feed B", b.displayName)
            assertNull(b.avatarUrl)
        }

    @Test
    fun `following entry carries no remote avatar`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(savedFeed("1", "timeline", "following", pinned = true))

            val result = repo().loadPinnedFeeds()

            val following = result.feeds.single()
            assertEquals(FeedKind.Following, following.kind)
            assertEquals(PinnedFeedsRepository.FOLLOWING_FEED_URI, following.uri)
            assertNull(following.avatarUrl)
        }

    @Test
    fun `list items split to FeedKind List`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(savedFeed("1", "list", "at://did:plc:x/app.bsky.graph.list/abc", pinned = true))

            val result = repo().loadPinnedFeeds()

            assertEquals(FeedKind.List, result.feeds.single().kind)
        }

    @Test
    fun `no getFeedGenerators call is made when there are no generator pins`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(
                    savedFeed("1", "timeline", "following", pinned = true),
                    savedFeed("2", "list", "at://l", pinned = true),
                )

            repo().loadPinnedFeeds()

            coVerify(exactly = 0) { dataSource.getFeedGenerators(any()) }
        }

    @Test
    fun `no savedFeedsPrefV2 falls back to Following plus Discover without hydration`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns null

            val result = repo().loadPinnedFeeds()

            assertTrue(result.usedFallback)
            assertEquals(
                listOf(PinnedFeedsRepository.FOLLOWING_FEED_URI, PinnedFeedsRepository.DISCOVER_FEED_URI),
                result.feeds.map { it.uri },
            )
            assertEquals(listOf(FeedKind.Following, FeedKind.Generator), result.feeds.map { it.kind })
            // Defaults render from local glyphs — no remote avatar, no network hydration.
            assertTrue(result.feeds.all { it.avatarUrl == null })
            coVerify(exactly = 0) { dataSource.getFeedGenerators(any()) }
        }

    @Test
    fun `empty pinned set falls back to Following plus Discover`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(savedFeed("1", "feed", "at://a", pinned = false))

            val result = repo().loadPinnedFeeds()

            assertTrue(result.usedFallback)
            assertEquals(
                listOf(PinnedFeedsRepository.FOLLOWING_FEED_URI, PinnedFeedsRepository.DISCOVER_FEED_URI),
                result.feeds.map { it.uri },
            )
            coVerify(exactly = 0) { dataSource.getFeedGenerators(any()) }
        }

    @Test
    fun `getPreferences failure falls back and signals a non-fatal error`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } throws IOException("net down")

            val result = repo().loadPinnedFeeds()

            assertTrue(result.usedFallback)
            assertEquals(
                listOf(PinnedFeedsRepository.FOLLOWING_FEED_URI, PinnedFeedsRepository.DISCOVER_FEED_URI),
                result.feeds.map { it.uri },
            )
            // Non-fatal: the call returns normally (does not throw) so the Feed
            // stays usable, but the failure is observable to the caller.
            assertTrue(result.error is IOException)
        }

    @Test
    fun `getFeedGenerators failure still yields chips, dropping unhydrated generators non-fatally`() =
        runTest {
            coEvery { dataSource.getSavedFeedItems() } returns
                listOf(
                    savedFeed("1", "timeline", "following", pinned = true),
                    savedFeed("2", "feed", "at://a", pinned = true),
                )
            coEvery { dataSource.getFeedGenerators(any()) } throws IOException("net down")

            val result = repo().loadPinnedFeeds()

            // Following still renders; the un-hydratable generator is dropped
            // rather than crashing the whole load.
            assertEquals(listOf(PinnedFeedsRepository.FOLLOWING_FEED_URI), result.feeds.map { it.uri })
            assertFalse(result.usedFallback)
            assertTrue(result.error is IOException)
        }

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
