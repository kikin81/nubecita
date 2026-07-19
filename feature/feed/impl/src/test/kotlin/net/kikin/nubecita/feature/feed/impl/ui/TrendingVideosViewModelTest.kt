package net.kikin.nubecita.feature.feed.impl.ui

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.videofeed.VideoFeedPage
import net.kikin.nubecita.core.videofeed.VideoFeedSource
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Instant

class TrendingVideosViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val source = mockk<VideoFeedSource>()

    @Test
    fun load_mapsVideoPostsToThumbsWithIndices() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(listOf(videoPost("a", "poster-a"), videoPost("b", "poster-b")), cursor = "c1"))

            val vm = TrendingVideosViewModel(source)
            advanceUntilIdle()

            val thumbs = vm.thumbs.value
            assertEquals(listOf(0, 1), thumbs.map { it.index })
            assertEquals(listOf("poster-a", "poster-b"), thumbs.map { it.posterUrl })
        }

    @Test
    fun loadFailure_leavesEmpty() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.failure(RuntimeException("boom"))

            val vm = TrendingVideosViewModel(source)
            advanceUntilIdle()

            assertTrue(vm.thumbs.value.isEmpty())
        }

    private fun videoPost(
        id: String,
        poster: String,
    ): PostUi =
        PostUi(
            id = id,
            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
            author = AuthorUi(did = "did:plc:fake", handle = "a.bsky.social", displayName = "A", avatarUrl = null),
            createdAt = Instant.parse("2026-07-18T12:00:00Z"),
            text = "",
            facets = persistentListOf(),
            embed = EmbedUi.Video(posterUrl = poster, playlistUrl = "https://cdn.example/$id.m3u8", aspectRatio = 0.56f, durationSeconds = 8, altText = null),
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
}
