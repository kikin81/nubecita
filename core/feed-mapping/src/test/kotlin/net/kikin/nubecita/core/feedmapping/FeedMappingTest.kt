package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.PostView
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.EmbedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-JVM regression tests for the shared atproto-wire-type → UI-model
 * helpers hosted in `:core:feed-mapping`. The contract: every fixture
 * shape both feed and post-detail consume must project to the same
 * [EmbedUi] / [net.kikin.nubecita.data.models.PostUi] regardless of which
 * caller invokes the helpers.
 *
 * These tests intentionally use inline JSON rather than copy the feed
 * module's `src/test/resources/fixtures/` pool. The behavioural contract
 * (single input → single output) is the test surface; mirroring the
 * fixture file layout would couple two modules' test data without
 * additional coverage.
 */
internal class FeedMappingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `null embed projects to EmbedUi Empty`() {
        val postView = decodePostView(POST_NO_EMBED)
        val mapped = postView.toPostUiCore()
        assertNotNull(mapped)
        assertEquals(EmbedUi.Empty, mapped!!.embed)
    }

    @Test
    fun `images embed projects to EmbedUi Images with the right item count`() {
        val postView = decodePostView(POST_WITH_TWO_IMAGES)
        val mapped = postView.toPostUiCore()
        assertNotNull(mapped)
        val images = assertInstanceOf(EmbedUi.Images::class.java, mapped!!.embed)
        assertEquals(2, images.items.size)
        assertEquals("https://cdn.bsky.app/img/feed_fullsize/plain/did:plc:fake/cid1@jpeg", images.items[0].url)
    }

    @Test
    fun `external embed projects to EmbedUi External with parsed display domain`() {
        val postView = decodePostView(POST_WITH_EXTERNAL_EMBED)
        val mapped = postView.toPostUiCore()
        assertNotNull(mapped)
        val external = assertInstanceOf(EmbedUi.External::class.java, mapped!!.embed)
        assertEquals("https://www.example.com/article", external.uri)
        // Leading `www.` must be stripped from the display domain so the
        // render layer shows `example.com`, not `www.example.com`.
        assertEquals("example.com", external.domain)
    }

    @Test
    fun `video embed projects to EmbedUi Video with parsed aspect ratio`() {
        val postView = decodePostView(POST_WITH_VIDEO_EMBED)
        val mapped = postView.toPostUiCore()
        assertNotNull(mapped)
        val video = assertInstanceOf(EmbedUi.Video::class.java, mapped!!.embed)
        assertEquals(1920f / 1080f, video.aspectRatio, 0.0001f)
    }

    @Test
    fun `video embed without aspect ratio falls back to 16-9`() {
        val postView = decodePostView(POST_WITH_VIDEO_NO_ASPECT)
        val mapped = postView.toPostUiCore()
        assertNotNull(mapped)
        val video = assertInstanceOf(EmbedUi.Video::class.java, mapped!!.embed)
        assertEquals(16f / 9f, video.aspectRatio, 0.0001f)
    }

    @Test
    fun `malformed record JSON yields null PostUi`() {
        // PostView whose record lacks the required `text` / `createdAt`
        // fields. The mapper is contractually required to return null
        // (callers' mapNotNull / fallback paths handle it) rather than
        // throw.
        val postView = decodePostView(POST_WITH_MALFORMED_RECORD)
        assertNull(postView.toPostUiCore())
    }

    @Test
    fun `repostedBy is null on the core projection — feed-specific overlay is not applied here`() {
        // The shared core MUST NOT consult FeedViewPost.reason; that's a
        // per-feed-entry concept layered by :feature:feed:impl's
        // toPostUiOrNull via .copy(repostedBy = ...).
        val postView = decodePostView(POST_NO_EMBED)
        val mapped = postView.toPostUiCore()
        assertNotNull(mapped)
        assertNull(mapped!!.repostedBy)
    }

    @Test
    fun `cid is propagated from PostView to PostUi`() {
        val postView = decodePostView(POST_NO_EMBED)
        val mapped = postView.toPostUiCore()
        assertNotNull(mapped)
        assertTrue(mapped!!.cid.isNotBlank())
    }

    private fun decodePostView(jsonString: String): PostView = json.decodeFromString(PostView.serializer(), jsonString)

    private companion object {
        const val POST_NO_EMBED = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/p1",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": {
                "did": "did:plc:fake",
                "handle": "fake.bsky.social",
                "displayName": "Fake User"
              },
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "hello",
                "createdAt": "2026-04-26T12:00:00Z"
              }
            }
        """

        const val POST_WITH_TWO_IMAGES = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/p2",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": {
                "did": "did:plc:fake",
                "handle": "fake.bsky.social"
              },
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "two images",
                "createdAt": "2026-04-26T12:00:00Z"
              },
              "embed": {
                "${'$'}type": "app.bsky.embed.images#view",
                "images": [
                  {
                    "thumb": "https://cdn.bsky.app/img/feed_thumbnail/plain/did:plc:fake/cid1@jpeg",
                    "fullsize": "https://cdn.bsky.app/img/feed_fullsize/plain/did:plc:fake/cid1@jpeg",
                    "alt": "first",
                    "aspectRatio": { "width": 1, "height": 1 }
                  },
                  {
                    "thumb": "https://cdn.bsky.app/img/feed_thumbnail/plain/did:plc:fake/cid2@jpeg",
                    "fullsize": "https://cdn.bsky.app/img/feed_fullsize/plain/did:plc:fake/cid2@jpeg",
                    "alt": "second",
                    "aspectRatio": { "width": 16, "height": 9 }
                  }
                ]
              }
            }
        """

        const val POST_WITH_EXTERNAL_EMBED = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/p3",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": {
                "did": "did:plc:fake",
                "handle": "fake.bsky.social"
              },
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "external link",
                "createdAt": "2026-04-26T12:00:00Z"
              },
              "embed": {
                "${'$'}type": "app.bsky.embed.external#view",
                "external": {
                  "uri": "https://www.example.com/article",
                  "title": "Example title",
                  "description": "Example description"
                }
              }
            }
        """

        const val POST_WITH_VIDEO_EMBED = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/p4",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": {
                "did": "did:plc:fake",
                "handle": "fake.bsky.social"
              },
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "video",
                "createdAt": "2026-04-26T12:00:00Z"
              },
              "embed": {
                "${'$'}type": "app.bsky.embed.video#view",
                "cid": "bafkreifake0000000000000000000000000000000000",
                "playlist": "https://video.bsky.app/watch/did%3Aplc%3Afake/bafkreifake/playlist.m3u8",
                "aspectRatio": { "height": 1080, "width": 1920 }
              }
            }
        """

        const val POST_WITH_VIDEO_NO_ASPECT = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/p5",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": {
                "did": "did:plc:fake",
                "handle": "fake.bsky.social"
              },
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "video without aspect",
                "createdAt": "2026-04-26T12:00:00Z"
              },
              "embed": {
                "${'$'}type": "app.bsky.embed.video#view",
                "cid": "bafkreifake0000000000000000000000000000000000",
                "playlist": "https://video.bsky.app/watch/did%3Aplc%3Afake/bafkreifake/playlist.m3u8"
              }
            }
        """

        const val POST_WITH_MALFORMED_RECORD = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/bad",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": {
                "did": "did:plc:fake",
                "handle": "fake.bsky.social"
              },
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": {
                "${'$'}type": "app.bsky.feed.post"
              }
            }
        """
    }
}
