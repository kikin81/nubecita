package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.GetTimelineResponse
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.EmbedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FeedViewPostMapperTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `happy path maps a typical FeedViewPost to a non-null PostUi`() {
        val response = decodeFixture("timeline_typical.json")
        val first = response.feed.first()

        val mapped = first.toPostUiOrNull()
        assertNotNull(mapped)
        assertEquals(first.post.uri.raw, mapped!!.id)
        assertEquals(first.post.author.handle.raw, mapped.author.handle)
        assertEquals(EmbedUi.Empty, mapped.embed)
        assertTrue(mapped.text.isNotBlank())
    }

    @Test
    fun `repostedBy is populated from ReasonRepost`() {
        val response = decodeFixture("timeline_with_repost.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        // The reposted-by name is the reposter's display name (or handle fallback);
        // it must be non-null because the fixture entry is reasonRepost.
        assertNotNull(mapped!!.repostedBy)
    }

    @Test
    fun `repostedBy is null when there is no reason`() {
        val response = decodeFixture("timeline_typical.json")
        val mapped = response.feed.first().toPostUiOrNull()
        assertNotNull(mapped)
        assertNull(mapped!!.repostedBy)
    }

    @Test
    fun `posts with no embed map to EmbedUi_Empty`() {
        val response = decodeFixture("timeline_typical.json")
        response.feed.forEach { entry ->
            val mapped = entry.toPostUiOrNull()
            assertNotNull(mapped)
            assertEquals(EmbedUi.Empty, mapped!!.embed)
        }
    }

    @Test
    fun `images embed maps to EmbedUi_Images with correct count for 1, 2, 3, and 4 images`() {
        val response = decodeFixture("timeline_with_images_embed.json")
        val mapped = response.feed.mapNotNull { it.toPostUiOrNull() }
        assertEquals(4, mapped.size)
        val counts = mapped.map { (it.embed as EmbedUi.Images).items.size }
        assertEquals(listOf(1, 2, 3, 4), counts)
    }

    @Test
    fun `external embed maps to EmbedUi_External with uri title description and thumbUrl`() {
        val response = decodeFixture("timeline_with_external_embed.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        val external = mapped!!.embed as EmbedUi.External
        assertEquals("https://davidimel.substack.com/p/bluesky-is-looking-for-its-myspace", external.uri)
        assertEquals("Bluesky is doubling down", external.title)
        assertEquals("Let's see if a feed-builder is it.", external.description)
        // The thumb is a server-side preview-card URL produced by the appview;
        // existence is what the assertion checks (the exact URL shape is
        // appview-internal and may evolve).
        assertNotNull(external.thumbUrl)
    }

    @Test
    fun `external embed without thumb maps to EmbedUi_External with thumbUrl null`() {
        // Synthetic external view that omits the optional `thumb` field.
        // The mapper must still produce EmbedUi.External; the render layer
        // omits the thumb section entirely (text-only card).
        val noThumbJson =
            """
            {
              "feed": [{
                "post": {
                  "uri": "at://did:plc:fake000000000000000000/app.bsky.feed.post/no-thumb",
                  "cid": "bafyreifakecid000000000000000000000000000000000",
                  "author": {
                    "did": "did:plc:fake000000000000000000",
                    "handle": "fake.bsky.social"
                  },
                  "indexedAt": "2026-04-26T12:00:00Z",
                  "record": {
                    "${'$'}type": "app.bsky.feed.post",
                    "text": "external without a thumb",
                    "createdAt": "2026-04-26T12:00:00Z"
                  },
                  "embed": {
                    "${'$'}type": "app.bsky.embed.external#view",
                    "external": {
                      "uri": "https://example.com/article",
                      "title": "Article without a thumbnail",
                      "description": "A short description."
                    }
                  }
                }
              }]
            }
            """.trimIndent()
        val response = json.decodeFromString(GetTimelineResponse.serializer(), noThumbJson)
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        val external = mapped!!.embed as EmbedUi.External
        assertEquals("https://example.com/article", external.uri)
        assertEquals("Article without a thumbnail", external.title)
        assertNull(external.thumbUrl)
    }

    @Test
    fun `external embed with empty title and description still maps to EmbedUi_External`() {
        // The lexicon types title and description as non-null String, but
        // Bluesky permits empty strings (e.g. when the OG scraper finds
        // nothing). The mapper must pass them through; the render layer
        // skips empty rows rather than treating empty as Unsupported.
        val emptyFieldsJson =
            """
            {
              "feed": [{
                "post": {
                  "uri": "at://did:plc:fake000000000000000000/app.bsky.feed.post/empty-fields",
                  "cid": "bafyreifakecid000000000000000000000000000000000",
                  "author": {
                    "did": "did:plc:fake000000000000000000",
                    "handle": "fake.bsky.social"
                  },
                  "indexedAt": "2026-04-26T12:00:00Z",
                  "record": {
                    "${'$'}type": "app.bsky.feed.post",
                    "text": "external with empty title/description",
                    "createdAt": "2026-04-26T12:00:00Z"
                  },
                  "embed": {
                    "${'$'}type": "app.bsky.embed.external#view",
                    "external": {
                      "uri": "https://example.com/no-metadata",
                      "title": "",
                      "description": ""
                    }
                  }
                }
              }]
            }
            """.trimIndent()
        val response = json.decodeFromString(GetTimelineResponse.serializer(), emptyFieldsJson)
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        val external = mapped!!.embed as EmbedUi.External
        assertEquals("", external.title)
        assertEquals("", external.description)
    }

    @Test
    fun `well-formed video embed maps to EmbedUi_Video with playlist + thumbnail + aspect ratio`() {
        val response = decodeFixture("timeline_with_video_embed.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        val video = mapped!!.embed as EmbedUi.Video
        assertEquals(
            "https://video.bsky.app/watch/did%3Aplc%3Az72i7hdynmk6r22z27h6tvur/bafkreifhuv36ji7vcq3tmdjltceyrfaat6vdccn2pklxf7j7dgsobdlgbm/playlist.m3u8",
            video.playlistUrl,
        )
        assertEquals(
            "https://video.bsky.app/watch/did%3Aplc%3Az72i7hdynmk6r22z27h6tvur/bafkreifhuv36ji7vcq3tmdjltceyrfaat6vdccn2pklxf7j7dgsobdlgbm/thumbnail.jpg",
            video.posterUrl,
        )
        // Fixture aspectRatio is 381 / 800 ≈ 0.476 (portrait/gif video).
        assertEquals(381f / 800f, video.aspectRatio, 0.0001f)
        // Lexicon does not currently expose duration; mapper passes null in v1.
        assertNull(video.durationSeconds)
        assertNull(video.altText)
    }

    @Test
    fun `video embed without thumbnail maps to EmbedUi_Video with posterUrl null`() {
        // Synthetic video view that omits the optional `thumbnail` field.
        // The mapper must still produce EmbedUi.Video with posterUrl = null
        // (render layer falls back to a gradient placeholder).
        val noThumbnailJson =
            """
            {
              "feed": [{
                "post": {
                  "uri": "at://did:plc:fake000000000000000000/app.bsky.feed.post/no-thumb",
                  "cid": "bafyreifakecid000000000000000000000000000000000",
                  "author": {
                    "did": "did:plc:fake000000000000000000",
                    "handle": "fake.bsky.social"
                  },
                  "indexedAt": "2026-04-26T12:00:00Z",
                  "record": {
                    "${'$'}type": "app.bsky.feed.post",
                    "text": "video without a thumbnail",
                    "createdAt": "2026-04-26T12:00:00Z"
                  },
                  "embed": {
                    "${'$'}type": "app.bsky.embed.video#view",
                    "cid": "bafkreifake0000000000000000000000000000000000",
                    "playlist": "https://video.bsky.app/watch/did%3Aplc%3Afake/bafkreifake/playlist.m3u8",
                    "aspectRatio": { "height": 1080, "width": 1920 }
                  }
                }
              }]
            }
            """.trimIndent()
        val response = json.decodeFromString(GetTimelineResponse.serializer(), noThumbnailJson)
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        val video = mapped!!.embed as EmbedUi.Video
        assertNull(video.posterUrl)
        assertEquals(1920f / 1080f, video.aspectRatio, 0.0001f)
    }

    @Test
    fun `video embed without aspectRatio falls back to 16-9`() {
        // Synthetic video view that omits the optional `aspectRatio` field.
        // The mapper must supply 16:9 (1.777f) so the render layer never
        // observes a null aspect — the LazyColumn measurement pass needs a
        // stable height before the poster loads.
        val noAspectJson =
            """
            {
              "feed": [{
                "post": {
                  "uri": "at://did:plc:fake000000000000000000/app.bsky.feed.post/no-aspect",
                  "cid": "bafyreifakecid000000000000000000000000000000000",
                  "author": {
                    "did": "did:plc:fake000000000000000000",
                    "handle": "fake.bsky.social"
                  },
                  "indexedAt": "2026-04-26T12:00:00Z",
                  "record": {
                    "${'$'}type": "app.bsky.feed.post",
                    "text": "video without aspectRatio",
                    "createdAt": "2026-04-26T12:00:00Z"
                  },
                  "embed": {
                    "${'$'}type": "app.bsky.embed.video#view",
                    "cid": "bafkreifake0000000000000000000000000000000000",
                    "playlist": "https://video.bsky.app/watch/did%3Aplc%3Afake/bafkreifake/playlist.m3u8"
                  }
                }
              }]
            }
            """.trimIndent()
        val response = json.decodeFromString(GetTimelineResponse.serializer(), noAspectJson)
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        val video = mapped!!.embed as EmbedUi.Video
        assertEquals(16f / 9f, video.aspectRatio, 0.0001f)
    }

    @Test
    fun `video embed with empty playlist falls through to Unsupported`() {
        // Synthetic video view whose required `playlist` is the empty
        // string — the mapper MUST fall through to Unsupported rather
        // than produce a EmbedUi.Video that points nowhere.
        val emptyPlaylistJson =
            """
            {
              "feed": [{
                "post": {
                  "uri": "at://did:plc:fake000000000000000000/app.bsky.feed.post/empty-playlist",
                  "cid": "bafyreifakecid000000000000000000000000000000000",
                  "author": {
                    "did": "did:plc:fake000000000000000000",
                    "handle": "fake.bsky.social"
                  },
                  "indexedAt": "2026-04-26T12:00:00Z",
                  "record": {
                    "${'$'}type": "app.bsky.feed.post",
                    "text": "video with empty playlist",
                    "createdAt": "2026-04-26T12:00:00Z"
                  },
                  "embed": {
                    "${'$'}type": "app.bsky.embed.video#view",
                    "cid": "bafkreifake0000000000000000000000000000000000",
                    "playlist": "",
                    "aspectRatio": { "height": 1080, "width": 1920 }
                  }
                }
              }]
            }
            """.trimIndent()
        val response = json.decodeFromString(GetTimelineResponse.serializer(), emptyPlaylistJson)
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        assertEquals(EmbedUi.Unsupported(typeUri = "app.bsky.embed.video"), mapped!!.embed)
    }

    @Test
    fun `record embed maps to EmbedUi_Unsupported with the record lexicon URI`() {
        // The repost fixture's post carries a record (quote-post) embed
        val response = decodeFixture("timeline_with_repost.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        assertEquals(EmbedUi.Unsupported(typeUri = "app.bsky.embed.record"), mapped!!.embed)
    }

    @Test
    fun `missing optional counts default to 0`() {
        // Synthetic, minimal-but-spec-conforming GetTimelineResponse JSON where
        // every count field is explicitly absent from the wire — exercises the
        // mapper's `(post.foo ?: 0L).toInt()` null→0 path that real fixtures
        // (where bsky.app's posts always carry counts) can't reach.
        val noCountsJson =
            """
            {
              "feed": [{
                "post": {
                  "author": {
                    "did": "did:plc:fake000000000000000000",
                    "handle": "fake.bsky.social"
                  },
                  "cid": "bafyreifakecidvalue00000000000000000000000000000",
                  "indexedAt": "2026-04-25T12:00:00Z",
                  "uri": "at://did:plc:fake000000000000000000/app.bsky.feed.post/abc",
                  "record": {
                    "${'$'}type": "app.bsky.feed.post",
                    "text": "post with no counts on the wire",
                    "createdAt": "2026-04-25T12:00:00Z"
                  }
                }
              }]
            }
            """.trimIndent()
        val response = json.decodeFromString(GetTimelineResponse.serializer(), noCountsJson)
        val mapped = response.feed.first().toPostUiOrNull()
        assertNotNull(mapped)
        assertEquals(0, mapped!!.stats.replyCount)
        assertEquals(0, mapped.stats.repostCount)
        assertEquals(0, mapped.stats.likeCount)
        assertEquals(0, mapped.stats.quoteCount)
    }

    @Test
    fun `malformed record returns null and does not throw`() {
        val response = decodeFixture("timeline_malformed_record.json")
        // Fixture has 2 entries: one well-formed, one with the required `text`
        // field stripped from the embedded record.
        val results = response.feed.map { it.toPostUiOrNull() }
        assertEquals(2, results.size)
        assertNotNull(results[0])
        assertNull(results[1])
    }

    @Test
    fun `repository drops malformed posts via mapNotNull`() {
        val response = decodeFixture("timeline_malformed_record.json")
        val good = response.feed.mapNotNull { it.toPostUiOrNull() }
        assertEquals(1, good.size)
    }

    @Test
    fun `facets are extracted for posts that carry them (link facet case)`() {
        val response = decodeFixture("timeline_with_external_embed.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        // The fixture post has a single link facet pointing at the external URL.
        assertEquals(1, mapped!!.facets.size)
    }

    @Test
    fun `facets list is empty when the record has no facets array`() {
        val response = decodeFixture("timeline_typical.json")
        val mapped = response.feed.first().toPostUiOrNull()
        assertNotNull(mapped)
        // The first fixture post's record has no `facets` key at all (verified
        // by inspecting timeline_typical.json). The mapper must return an empty
        // list, NOT null, so downstream `state.facets` reads are total.
        assertTrue(mapped!!.facets.isEmpty(), "expected empty facets, got ${mapped.facets}")
    }

    private fun decodeFixture(name: String): GetTimelineResponse {
        val classLoader = checkNotNull(this::class.java.classLoader) { "test class loader missing" }
        val text =
            requireNotNull(classLoader.getResourceAsStream("fixtures/$name")) {
                "fixture $name not found on test classpath"
            }.bufferedReader().use { it.readText() }
        return json.decodeFromString(GetTimelineResponse.serializer(), text)
    }
}
