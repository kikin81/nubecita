package net.kikin.nubecita.feature.feed.impl.video

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Tests the visibility math that drives [FeedVideoPlayerCoordinator]
 * binding. The wiring around it (the `combine(isScrollInProgress,
 * snapshotFlow { ... })` flow in `FeedScreen`) is too thin to unit-test
 * directly — covered manually on a 120Hz device per task 4.8.
 */
internal class MostVisibleVideoTargetTest {
    private val viewportHeight = 1200

    @Test
    fun `topmost video card with fraction over 0_6 wins`() {
        val posts =
            mapOf(
                "post-text" to textPost("post-text"),
                "post-video-1" to videoPost("post-video-1", "https://playlist1.m3u8"),
                "post-video-2" to videoPost("post-video-2", "https://playlist2.m3u8"),
            )
        // post-text fully visible at the top, post-video-1 mostly visible
        // (above threshold), post-video-2 just peeking in.
        val info =
            layoutInfo(
                items =
                    listOf(
                        itemInfo(key = "post-text", offset = 0, size = 200),
                        itemInfo(key = "post-video-1", offset = 200, size = 600), // fully visible
                        itemInfo(key = "post-video-2", offset = 800, size = 600), // 400/600 = 0.66
                    ),
            )
        val target = mostVisibleVideoTarget(info, posts)
        assertEquals(VideoBindingTarget("post-video-1", "https://playlist1.m3u8"), target)
    }

    @Test
    fun `non-video posts are skipped even when most visible`() {
        val posts =
            mapOf(
                "post-text" to textPost("post-text"),
                "post-video" to videoPost("post-video", "https://video.m3u8"),
            )
        // post-text spans most of the viewport; post-video is just peeking
        // in but is the only video — and its visible-fraction is below
        // threshold, so the binding is null.
        val info =
            layoutInfo(
                items =
                    listOf(
                        itemInfo(key = "post-text", offset = 0, size = 1000),
                        itemInfo(key = "post-video", offset = 1000, size = 600), // 200/600 = 0.33
                    ),
            )
        assertNull(mostVisibleVideoTarget(info, posts))
    }

    @Test
    fun `card peeking just under the threshold is rejected`() {
        val posts = mapOf("post-video" to videoPost("post-video", "https://video.m3u8"))
        // viewport is [0, 1200]; item spans [700, 1700]; visible portion
        // is 1200 - 700 = 500 of size 1000 = 0.5 — below the 0.6
        // threshold, so the binding is null.
        val info =
            layoutInfo(
                items = listOf(itemInfo(key = "post-video", offset = 700, size = 1000)),
            )
        assertNull(mostVisibleVideoTarget(info, posts))
    }

    @Test
    fun `unknown keys do not crash and contribute nothing`() {
        // Trailing "appending" sentinel item or a stale key from a
        // previous post page — neither map to a PostUi. Skip silently.
        val posts = mapOf("post-video" to videoPost("post-video", "https://video.m3u8"))
        val info =
            layoutInfo(
                items =
                    listOf(
                        itemInfo(key = "appending", offset = 0, size = 200),
                        itemInfo(key = "post-video", offset = 200, size = 600), // fully visible
                    ),
            )
        val target = mostVisibleVideoTarget(info, posts)
        assertEquals(VideoBindingTarget("post-video", "https://video.m3u8"), target)
    }

    @Test
    fun `empty visibleItemsInfo yields null target`() {
        assertNull(
            mostVisibleVideoTarget(
                layoutInfo(items = emptyList()),
                mapOf("post-video" to videoPost("post-video", "https://video.m3u8")),
            ),
        )
    }

    @Test
    fun `card extending past viewport bottom uses clipped intersection`() {
        // Post extends from offset 200 to 200+1500=1700; viewport ends
        // at 1200. Visible portion = 1000; size = 1500; fraction = 0.66.
        val posts = mapOf("post-video" to videoPost("post-video", "https://video.m3u8"))
        val info =
            layoutInfo(
                items = listOf(itemInfo(key = "post-video", offset = 200, size = 1500)),
            )
        val target = mostVisibleVideoTarget(info, posts)
        assertEquals(VideoBindingTarget("post-video", "https://video.m3u8"), target)
    }

    private fun layoutInfo(items: List<LazyListItemInfo>): LazyListLayoutInfo {
        val mocked = mockk<LazyListLayoutInfo>()
        every { mocked.visibleItemsInfo } returns items
        every { mocked.viewportStartOffset } returns 0
        every { mocked.viewportEndOffset } returns viewportHeight
        return mocked
    }

    private fun itemInfo(
        key: String,
        offset: Int,
        size: Int,
    ): LazyListItemInfo {
        val item = mockk<LazyListItemInfo>()
        every { item.key } returns key
        every { item.offset } returns offset
        every { item.size } returns size
        return item
    }

    private fun textPost(id: String): PostUi =
        PostUi(
            id = id,
            author =
                AuthorUi(
                    did = "did:plc:fake",
                    handle = "fake.bsky.social",
                    displayName = "Fake",
                    avatarUrl = null,
                ),
            createdAt = Instant.fromEpochSeconds(0),
            text = "text post",
            facets = persistentListOf<Facet>(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )

    private fun videoPost(
        id: String,
        playlistUrl: String,
    ): PostUi =
        textPost(id).copy(
            embed =
                EmbedUi.Video(
                    posterUrl = null,
                    playlistUrl = playlistUrl,
                    aspectRatio = 16f / 9f,
                    durationSeconds = null,
                    altText = null,
                ),
        )
}
