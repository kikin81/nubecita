package net.kikin.nubecita.core.videofeed

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Unit tests for the pure video-filter that backs [toVideoPosts]. The wire →
 * [PostUi] projection (`toPostUiCore`) is covered by `:core:feed-mapping`; here
 * we verify the filter keeps exactly the video-bearing posts, since `thevids`
 * can surface mixed content.
 */
class TrendingVideoMappingTest {
    @Test
    fun videoPostsOnly_keepsOnlyVideoEmbeds() {
        val video = post("v1", fakeVideoEmbed())
        val image = post("i1", EmbedUi.Empty)
        val alsoVideo = post("v2", fakeVideoEmbed())

        val kept = listOf(video, image, alsoVideo).videoPostsOnly()

        assertEquals(listOf("v1", "v2"), kept.map { it.id })
    }

    @Test
    fun videoPostsOnly_preservesOrder() {
        val posts = listOf(post("a", fakeVideoEmbed()), post("b", fakeVideoEmbed()), post("c", fakeVideoEmbed()))

        assertEquals(listOf("a", "b", "c"), posts.videoPostsOnly().map { it.id })
    }

    @Test
    fun videoPostsOnly_emptyWhenNoVideos() {
        val kept = listOf(post("x", EmbedUi.Empty), post("y", EmbedUi.Empty)).videoPostsOnly()

        assertEquals(emptyList<PostUi>(), kept)
    }

    private fun fakeVideoEmbed(): EmbedUi.Video =
        EmbedUi.Video(
            posterUrl = "https://cdn.example/poster.jpg",
            playlistUrl = "https://cdn.example/playlist.m3u8",
            aspectRatio = 0.5625f,
            durationSeconds = 12,
            altText = null,
        )

    private fun post(
        id: String,
        embed: EmbedUi,
    ): PostUi =
        PostUi(
            id = id,
            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
            author = AuthorUi(did = "did:plc:fake", handle = "alice.bsky.social", displayName = "Alice", avatarUrl = null),
            createdAt = Instant.parse("2026-07-18T12:00:00Z"),
            text = "",
            facets = persistentListOf(),
            embed = embed,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
}
