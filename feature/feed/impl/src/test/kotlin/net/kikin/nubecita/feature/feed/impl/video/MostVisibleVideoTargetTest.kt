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
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
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

    // ---------- Quoted-post video extension ----------

    @Test
    fun `quoted-post video binds when parent has no own video`() {
        // Parent is a text post that quotes a video post; bind identity
        // is the quoted post's URI, not the parent's id.
        val quotedUri = "at://did:plc:fake/app.bsky.feed.post/quoted"
        val posts =
            mapOf(
                "post-text-quoting-video" to
                    quotedVideoPost(
                        parentId = "post-text-quoting-video",
                        quotedUri = quotedUri,
                        quotedPlaylistUrl = "https://video.bsky.app/q.m3u8",
                    ),
            )
        val info =
            layoutInfo(
                items = listOf(itemInfo(key = "post-text-quoting-video", offset = 0, size = 600)),
            )
        val target = mostVisibleVideoTarget(info, posts)
        assertEquals(VideoBindingTarget(quotedUri, "https://video.bsky.app/q.m3u8"), target)
    }

    // No test for "parent video wins over quoted video on the same
    // item" — `PostUi.embed` is a single sealed slot, so a feed item
    // structurally cannot carry both `EmbedUi.Video` AND `EmbedUi.Record`
    // simultaneously through the public mapper. The parent-first
    // ordering in `videoBindingFor` is a defensive guarantee for an
    // unreachable case; the standalone tests below cover the orderings
    // that ARE structurally reachable (parent-only, quoted-only, neither).

    @Test
    fun `topmost rule applies across mixed parent and quoted videos`() {
        // Post A (offset 0, parent video) and Post B (offset 800,
        // quoted video) both visible above threshold. Topmost wins.
        val posts =
            mapOf(
                "post-a-parent" to videoPost("post-a-parent", "https://a.m3u8"),
                "post-b-quoted" to
                    quotedVideoPost(
                        parentId = "post-b-quoted",
                        quotedUri = "at://did:plc:b/app.bsky.feed.post/q",
                        quotedPlaylistUrl = "https://b.m3u8",
                    ),
            )
        val info =
            layoutInfo(
                items =
                    listOf(
                        itemInfo(key = "post-a-parent", offset = 0, size = 600),
                        itemInfo(key = "post-b-quoted", offset = 600, size = 600), // 600/600 = 1.0
                    ),
            )
        val target = mostVisibleVideoTarget(info, posts)
        // A is at the top of the viewport — it wins, even though B is
        // also fully visible (topmost, not max-visible).
        assertEquals(VideoBindingTarget("post-a-parent", "https://a.m3u8"), target)
    }

    @Test
    fun `quoted-post video below threshold yields null target`() {
        // Same shape as the existing parent-only "below threshold"
        // case, but for the quoted-video path.
        val quotedUri = "at://did:plc:fake/app.bsky.feed.post/quoted"
        val posts =
            mapOf(
                "post-text-quoting-video" to
                    quotedVideoPost(
                        parentId = "post-text-quoting-video",
                        quotedUri = quotedUri,
                        quotedPlaylistUrl = "https://q.m3u8",
                    ),
            )
        // Item at offset 700, size 1000 → visible 500/1000 = 0.5 (below 0.6).
        val info =
            layoutInfo(
                items = listOf(itemInfo(key = "post-text-quoting-video", offset = 700, size = 1000)),
            )
        assertNull(mostVisibleVideoTarget(info, posts))
    }

    // ---------- videoBindingFor standalone tests ----------

    @Test
    fun `videoBindingFor returns parent target when parent video is present`() {
        val post = videoPost("post-id", "https://parent.m3u8")
        assertEquals(
            VideoBindingTarget("post-id", "https://parent.m3u8"),
            videoBindingFor(post),
        )
    }

    @Test
    fun `videoBindingFor returns quoted target when only quoted video is present`() {
        val post =
            quotedVideoPost(
                parentId = "parent",
                quotedUri = "at://did:plc:q/app.bsky.feed.post/q",
                quotedPlaylistUrl = "https://q.m3u8",
            )
        assertEquals(
            VideoBindingTarget("at://did:plc:q/app.bsky.feed.post/q", "https://q.m3u8"),
            videoBindingFor(post),
        )
    }

    @Test
    fun `videoBindingFor returns null for text-only posts`() {
        assertNull(videoBindingFor(textPost("post-id")))
    }

    @Test
    fun `videoBindingFor returns null for posts with non-video quoted embed`() {
        // Quote a post whose own embed is empty — there's no video
        // anywhere on this feed item.
        val post =
            textPost("parent").copy(
                embed =
                    EmbedUi.Record(
                        quotedPost =
                            QuotedPostUi(
                                uri = "at://did:plc:q/app.bsky.feed.post/q",
                                cid = "bafyq",
                                author = fakeAuthor(),
                                createdAt = Instant.fromEpochSeconds(0),
                                text = "quoted text",
                                facets = persistentListOf<Facet>(),
                                embed = QuotedEmbedUi.Empty,
                            ),
                    ),
            )
        assertNull(videoBindingFor(post))
    }

    @Test
    fun `videoBindingFor returns null for RecordUnavailable`() {
        val post =
            textPost("parent").copy(
                embed = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
            )
        assertNull(videoBindingFor(post))
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

    /**
     * A parent post whose own embed is a `Record` carrying a
     * `QuotedPostUi` whose own embed is a video. This is the wire
     * shape that drives the quoted-video bind path in
     * [mostVisibleVideoTarget].
     */
    private fun quotedVideoPost(
        parentId: String,
        quotedUri: String,
        quotedPlaylistUrl: String,
    ): PostUi =
        textPost(parentId).copy(
            embed =
                EmbedUi.Record(
                    quotedPost =
                        QuotedPostUi(
                            uri = quotedUri,
                            cid = "bafyqcid000000000000000000000000000000000",
                            author = fakeAuthor(),
                            createdAt = Instant.fromEpochSeconds(0),
                            text = "quoted post text",
                            facets = persistentListOf<Facet>(),
                            embed =
                                QuotedEmbedUi.Video(
                                    posterUrl = null,
                                    playlistUrl = quotedPlaylistUrl,
                                    aspectRatio = 16f / 9f,
                                    durationSeconds = null,
                                    altText = null,
                                ),
                        ),
                ),
        )

    private fun fakeAuthor(): AuthorUi =
        AuthorUi(
            did = "did:plc:fake",
            handle = "fake.bsky.social",
            displayName = "Fake",
            avatarUrl = null,
        )
}
