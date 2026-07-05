package net.kikin.nubecita.feature.widgets.impl.image

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class GlanceWidgetImagePrefetcherTest {
    @TempDir
    lateinit var root: File

    @Test
    fun `decodes one thumbnail per media post and skips text-only posts`() =
        runTest {
            val repo = mockk<FeedRepository>()
            coEvery { repo.head(FEED, any()) } returns
                flowOf(
                    listOf(
                        post("a", imageEmbed("thumbA")),
                        post("b", EmbedUi.Empty),
                        post("c", videoEmbed("posterC")),
                    ),
                )
            val store = WidgetThumbnailStore(root)
            val decoder = RecordingDecoder()
            val prefetcher = prefetcher(repo, store, decoder)

            prefetcher.prefetch(FEED)

            // One decode per media post (a, c); the text-only post (b) is skipped.
            assertEquals(
                listOf("thumbA" to store.thumbnailFile(ACCOUNT, "a"), "posterC" to store.thumbnailFile(ACCOUNT, "c")),
                decoder.calls,
            )
        }

    @Test
    fun `evicts thumbnails for posts no longer in the head`() =
        runTest {
            val repo = mockk<FeedRepository>()
            coEvery { repo.head(FEED, any()) } returns flowOf(listOf(post("a", imageEmbed("thumbA"))))
            val store = WidgetThumbnailStore(root)
            // A stale thumbnail for a post that has scrolled out of the head.
            store.thumbnailFile(ACCOUNT, "stale").writeBytes(byteArrayOf(1))
            val prefetcher = prefetcher(repo, store, RecordingDecoder())

            prefetcher.prefetch(FEED)

            // Eviction ran with the current head's ids → the stale thumbnail is gone.
            assertFalse(store.hasThumbnail(ACCOUNT, "stale"))
        }

    @Test
    fun `skips decode when a post's thumbnail is already cached`() =
        runTest {
            val repo = mockk<FeedRepository>()
            coEvery { repo.head(FEED, any()) } returns flowOf(listOf(post("a", imageEmbed("thumbA"))))
            val store = WidgetThumbnailStore(root)
            store.thumbnailFile(ACCOUNT, "a").writeBytes(byteArrayOf(1)) // already cached
            val decoder = RecordingDecoder()
            val prefetcher = prefetcher(repo, store, decoder)

            prefetcher.prefetch(FEED)

            assertTrue(decoder.calls.isEmpty())
        }

    private fun prefetcher(
        repo: FeedRepository,
        store: WidgetThumbnailStore,
        decoder: ThumbnailDecoder,
    ): GlanceWidgetImagePrefetcher = GlanceWidgetImagePrefetcher(repo, store, decoder, UnconfinedTestDispatcher())

    private class RecordingDecoder : ThumbnailDecoder {
        val calls = mutableListOf<Pair<String, File>>()

        override suspend fun decodeToFile(
            url: String,
            dest: File,
            allowNetwork: Boolean,
        ): Boolean {
            calls += url to dest
            return true
        }
    }

    private companion object {
        const val ACCOUNT = "did:plc:viewer"
        val FEED = FeedKey.following(ACCOUNT)

        fun imageEmbed(thumb: String): EmbedUi = EmbedUi.Images(items = persistentListOf(ImageUi(fullsizeUrl = "full", thumbUrl = thumb, altText = null, aspectRatio = null)))

        fun videoEmbed(poster: String): EmbedUi = EmbedUi.Video(posterUrl = poster, playlistUrl = "https://x/p.m3u8", aspectRatio = 1.77f, durationSeconds = null, altText = null)

        fun post(
            id: String,
            embed: EmbedUi,
        ): PostUi =
            PostUi(
                id = id,
                cid = "cid-$id",
                author = AuthorUi(did = "did:author", handle = "h", displayName = "D", avatarUrl = null),
                createdAt = Instant.fromEpochMilliseconds(0),
                text = "t",
                facets = persistentListOf(),
                embed = embed,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
            )
    }
}
