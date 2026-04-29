package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.GetTimelineResponse
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
        assertEquals("davidimel.substack.com", external.domain)
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
        assertEquals("example.com", external.domain)
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
        assertEquals("https://example.com/no-metadata", external.uri)
        assertEquals("example.com", external.domain)
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
    fun `record embed (viewRecord) maps to EmbedUi_Record with quoted post fields populated`() {
        // The repost fixture's post quotes albert-breer's post; that quoted
        // post itself quotes surf.social, so the inner embed is the recursion-
        // bound sentinel.
        val response = decodeFixture("timeline_with_repost.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        val record = mapped!!.embed as EmbedUi.Record
        val quoted = record.quotedPost
        assertEquals("at://did:plc:aokijbva3wrsbt7sqsvm4g5w/app.bsky.feed.post/3mjxaa6tti22w", quoted.uri)
        assertEquals("bafyreif723oiwg4goatyl2miyjifwcvggqi62i2mlrubfkwadsvfy4smaa", quoted.cid)
        assertEquals("albert-breer.bsky.social", quoted.author.handle)
        assertTrue(quoted.text.startsWith("We're doing an AMA here"), "unexpected text: ${quoted.text}")
        // albert-breer's post quotes surf.social — the recursion bound kicks in here.
        assertEquals(QuotedEmbedUi.QuotedThreadChip, quoted.embed)
        // The fixture carries a single link facet on the quoted post's text.
        assertEquals(1, quoted.facets.size)
    }

    @Test
    fun `record viewNotFound maps to EmbedUi_RecordUnavailable_NotFound`() {
        val mapped = decodeAndMapSingle(quotedPostEmbedJson(viewType = "viewNotFound"))
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
            mapped.embed,
        )
    }

    @Test
    fun `record viewBlocked maps to EmbedUi_RecordUnavailable_Blocked`() {
        val mapped = decodeAndMapSingle(quotedPostEmbedJson(viewType = "viewBlocked"))
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Blocked),
            mapped.embed,
        )
    }

    @Test
    fun `record viewDetached maps to EmbedUi_RecordUnavailable_Detached`() {
        val mapped = decodeAndMapSingle(quotedPostEmbedJson(viewType = "viewDetached"))
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Detached),
            mapped.embed,
        )
    }

    @Test
    fun `record viewRecord with malformed value maps to RecordUnavailable_Unknown and the parent still maps`() {
        // Quoted record `value` is missing the required `text` field —
        // decode fails. Parent post must still map to a non-null PostUi.
        val malformedJson =
            wrapAsTimelineJson(
                """
                "embed": {
                  "${'$'}type": "app.bsky.embed.record#view",
                  "record": {
                    "${'$'}type": "app.bsky.embed.record#viewRecord",
                    "uri": "at://did:plc:fake/app.bsky.feed.post/quoted",
                    "cid": "bafyreifakequotedcid000000000000000000000000000",
                    "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
                    "indexedAt": "2026-04-26T12:00:00Z",
                    "value": {
                      "${'$'}type": "app.bsky.feed.post",
                      "createdAt": "2026-04-26T12:00:00Z"
                    }
                  }
                }
                """.trimIndent(),
            )
        val mapped = decodeAndMapSingle(malformedJson)
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Unknown),
            mapped.embed,
        )
        // Parent text is preserved — the malformed quoted record did not
        // tank the parent post mapping.
        assertEquals("parent post text", mapped.text)
    }

    @Test
    fun `record viewRecord with malformed createdAt maps to RecordUnavailable_Unknown`() {
        // The quoted record's `value` decodes (text + createdAt fields
        // present) but createdAt is "not-a-date" — Instant.parse throws.
        val badCreatedAtJson =
            wrapAsTimelineJson(
                """
                "embed": {
                  "${'$'}type": "app.bsky.embed.record#view",
                  "record": {
                    "${'$'}type": "app.bsky.embed.record#viewRecord",
                    "uri": "at://did:plc:fake/app.bsky.feed.post/quoted",
                    "cid": "bafyreifakequotedcid000000000000000000000000000",
                    "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
                    "indexedAt": "2026-04-26T12:00:00Z",
                    "value": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "quoted text",
                      "createdAt": "not-a-date"
                    }
                  }
                }
                """.trimIndent(),
            )
        val mapped = decodeAndMapSingle(badCreatedAtJson)
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Unknown),
            mapped.embed,
        )
    }

    @Test
    fun `record viewRecord with inner Images embed maps quotedPost_embed to QuotedEmbedUi_Images`() {
        val innerImagesJson =
            wrapAsTimelineJson(
                """
                "embed": {
                  "${'$'}type": "app.bsky.embed.record#view",
                  "record": {
                    "${'$'}type": "app.bsky.embed.record#viewRecord",
                    "uri": "at://did:plc:fake/app.bsky.feed.post/quoted",
                    "cid": "bafyreifakequotedcid000000000000000000000000000",
                    "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
                    "indexedAt": "2026-04-26T12:00:00Z",
                    "value": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "quoted text",
                      "createdAt": "2026-04-26T12:00:00Z"
                    },
                    "embeds": [
                      {
                        "${'$'}type": "app.bsky.embed.images#view",
                        "images": [
                          { "thumb": "https://cdn/t.jpg", "fullsize": "https://cdn/f.jpg", "alt": "" },
                          { "thumb": "https://cdn/t2.jpg", "fullsize": "https://cdn/f2.jpg", "alt": "" }
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        val mapped = decodeAndMapSingle(innerImagesJson)
        val quoted = (mapped.embed as EmbedUi.Record).quotedPost
        val images = quoted.embed as QuotedEmbedUi.Images
        assertEquals(2, images.items.size)
        assertEquals("https://cdn/f.jpg", images.items[0].url)
    }

    @Test
    fun `record viewRecord with inner External embed maps quotedPost_embed to QuotedEmbedUi_External with precomputed domain`() {
        val innerExternalJson =
            wrapAsTimelineJson(
                """
                "embed": {
                  "${'$'}type": "app.bsky.embed.record#view",
                  "record": {
                    "${'$'}type": "app.bsky.embed.record#viewRecord",
                    "uri": "at://did:plc:fake/app.bsky.feed.post/quoted",
                    "cid": "bafyreifakequotedcid000000000000000000000000000",
                    "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
                    "indexedAt": "2026-04-26T12:00:00Z",
                    "value": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "quoted text",
                      "createdAt": "2026-04-26T12:00:00Z"
                    },
                    "embeds": [
                      {
                        "${'$'}type": "app.bsky.embed.external#view",
                        "external": {
                          "uri": "https://www.example.com/article",
                          "title": "Article",
                          "description": ""
                        }
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        val mapped = decodeAndMapSingle(innerExternalJson)
        val quoted = (mapped.embed as EmbedUi.Record).quotedPost
        val external = quoted.embed as QuotedEmbedUi.External
        assertEquals("https://www.example.com/article", external.uri)
        assertEquals("example.com", external.domain)
    }

    @Test
    fun `record viewRecord with inner Video embed maps quotedPost_embed to QuotedEmbedUi_Video`() {
        val innerVideoJson =
            wrapAsTimelineJson(
                """
                "embed": {
                  "${'$'}type": "app.bsky.embed.record#view",
                  "record": {
                    "${'$'}type": "app.bsky.embed.record#viewRecord",
                    "uri": "at://did:plc:fake/app.bsky.feed.post/quoted",
                    "cid": "bafyreifakequotedcid000000000000000000000000000",
                    "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
                    "indexedAt": "2026-04-26T12:00:00Z",
                    "value": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "quoted text",
                      "createdAt": "2026-04-26T12:00:00Z"
                    },
                    "embeds": [
                      {
                        "${'$'}type": "app.bsky.embed.video#view",
                        "cid": "bafkreifakevidcid0000000000000000000000000000",
                        "playlist": "https://video.bsky.app/.../q.m3u8",
                        "aspectRatio": { "width": 1920, "height": 1080 }
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        val mapped = decodeAndMapSingle(innerVideoJson)
        val quoted = (mapped.embed as EmbedUi.Record).quotedPost
        val video = quoted.embed as QuotedEmbedUi.Video
        assertEquals("https://video.bsky.app/.../q.m3u8", video.playlistUrl)
        assertEquals(1920f / 1080f, video.aspectRatio, 0.0001f)
    }

    @Test
    fun `record viewRecord with inner Video embed missing playlist falls through to QuotedEmbedUi_Unsupported`() {
        val innerBlankPlaylistJson =
            wrapAsTimelineJson(
                """
                "embed": {
                  "${'$'}type": "app.bsky.embed.record#view",
                  "record": {
                    "${'$'}type": "app.bsky.embed.record#viewRecord",
                    "uri": "at://did:plc:fake/app.bsky.feed.post/quoted",
                    "cid": "bafyreifakequotedcid000000000000000000000000000",
                    "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
                    "indexedAt": "2026-04-26T12:00:00Z",
                    "value": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "quoted text",
                      "createdAt": "2026-04-26T12:00:00Z"
                    },
                    "embeds": [
                      {
                        "${'$'}type": "app.bsky.embed.video#view",
                        "cid": "bafkreifakevidcid0000000000000000000000000000",
                        "playlist": ""
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        val mapped = decodeAndMapSingle(innerBlankPlaylistJson)
        val quoted = (mapped.embed as EmbedUi.Record).quotedPost
        assertEquals(QuotedEmbedUi.Unsupported("app.bsky.embed.video"), quoted.embed)
    }

    @Test
    fun `record viewRecord with inner RecordWithMedia maps quotedPost_embed to QuotedEmbedUi_Unsupported`() {
        // Synthetic minimal recordWithMedia view — inner shape doesn't matter
        // for the dispatch test, only the top-level $type does.
        val innerRwmJson =
            wrapAsTimelineJson(
                """
                "embed": {
                  "${'$'}type": "app.bsky.embed.record#view",
                  "record": {
                    "${'$'}type": "app.bsky.embed.record#viewRecord",
                    "uri": "at://did:plc:fake/app.bsky.feed.post/quoted",
                    "cid": "bafyreifakequotedcid000000000000000000000000000",
                    "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
                    "indexedAt": "2026-04-26T12:00:00Z",
                    "value": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "quoted text",
                      "createdAt": "2026-04-26T12:00:00Z"
                    },
                    "embeds": [
                      {
                        "${'$'}type": "app.bsky.embed.recordWithMedia#view",
                        "record": {
                          "${'$'}type": "app.bsky.embed.record#view",
                          "record": {
                            "${'$'}type": "app.bsky.embed.record#viewNotFound",
                            "uri": "at://did:plc:fake/app.bsky.feed.post/inner",
                            "notFound": true
                          }
                        },
                        "media": {
                          "${'$'}type": "app.bsky.embed.images#view",
                          "images": []
                        }
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        val mapped = decodeAndMapSingle(innerRwmJson)
        val quoted = (mapped.embed as EmbedUi.Record).quotedPost
        assertInstanceOf(QuotedEmbedUi.Unsupported::class.java, quoted.embed)
        assertEquals("app.bsky.embed.recordWithMedia", (quoted.embed as QuotedEmbedUi.Unsupported).typeUri)
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

    // ---------- RecordWithMedia (umn) ----------

    @Test
    fun `recordWithMedia with resolved record + Images media maps to EmbedUi_RecordWithMedia`() {
        val mapped = decodeAndMapSingle(recordWithMediaJson(media = imagesMediaSnippet(2)))
        val rwm = mapped.embed as EmbedUi.RecordWithMedia
        val record = rwm.record as EmbedUi.Record
        assertEquals("at://did:plc:fake/app.bsky.feed.post/quoted", record.quotedPost.uri)
        assertEquals("quoted text", record.quotedPost.text)
        val media = rwm.media as EmbedUi.Images
        assertEquals(2, media.items.size)
    }

    @Test
    fun `recordWithMedia with resolved record + External media maps with precomputed domain`() {
        val mapped = decodeAndMapSingle(recordWithMediaJson(media = externalMediaSnippet()))
        val rwm = mapped.embed as EmbedUi.RecordWithMedia
        val media = rwm.media as EmbedUi.External
        assertEquals("https://www.example.com/article", media.uri)
        assertEquals("example.com", media.domain)
    }

    @Test
    fun `recordWithMedia with resolved record + Video media maps to EmbedUi_RecordWithMedia`() {
        val mapped = decodeAndMapSingle(recordWithMediaJson(media = videoMediaSnippet()))
        val rwm = mapped.embed as EmbedUi.RecordWithMedia
        val media = rwm.media as EmbedUi.Video
        assertEquals("https://video.bsky.app/m.m3u8", media.playlistUrl)
        assertEquals(1920f / 1080f, media.aspectRatio, 0.0001f)
    }

    @Test
    fun `recordWithMedia with viewNotFound record + Images media maps to RecordUnavailable + Images`() {
        val mapped =
            decodeAndMapSingle(
                recordWithMediaJson(
                    recordSnippet = unavailableRecordSnippet("viewNotFound", extra = "\"notFound\": true"),
                    media = imagesMediaSnippet(1),
                ),
            )
        val rwm = mapped.embed as EmbedUi.RecordWithMedia
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
            rwm.record,
        )
        assertInstanceOf(EmbedUi.Images::class.java, rwm.media)
    }

    @Test
    fun `recordWithMedia with viewBlocked record + External media maps to Blocked + External`() {
        val mapped =
            decodeAndMapSingle(
                recordWithMediaJson(
                    recordSnippet =
                        unavailableRecordSnippet(
                            "viewBlocked",
                            extra =
                                """"blocked": true, "author": { "did": "did:plc:fake", "viewer": {} }""",
                        ),
                    media = externalMediaSnippet(),
                ),
            )
        val rwm = mapped.embed as EmbedUi.RecordWithMedia
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Blocked),
            rwm.record,
        )
        assertInstanceOf(EmbedUi.External::class.java, rwm.media)
    }

    @Test
    fun `recordWithMedia with malformed quoted-record value maps to RecordUnavailable_Unknown record + media still renders`() {
        // Quoted record's value JSON missing the required `text` field.
        val malformedRecord =
            """
            "record": {
              "${'$'}type": "app.bsky.embed.record#view",
              "record": {
                "${'$'}type": "app.bsky.embed.record#viewRecord",
                "uri": "at://did:plc:fake/app.bsky.feed.post/quoted",
                "cid": "bafyreifakequotedcid000000000000000000000000000",
                "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
                "indexedAt": "2026-04-26T12:00:00Z",
                "value": {
                  "${'$'}type": "app.bsky.feed.post",
                  "createdAt": "2026-04-26T12:00:00Z"
                }
              }
            }
            """.trimIndent()
        val mapped =
            decodeAndMapSingle(
                recordWithMediaJson(recordSnippet = malformedRecord, media = imagesMediaSnippet(1)),
            )
        val rwm = mapped.embed as EmbedUi.RecordWithMedia
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Unknown),
            rwm.record,
        )
        assertInstanceOf(EmbedUi.Images::class.java, rwm.media)
        // Parent post NEVER dropped because of malformed quoted record.
        assertEquals("parent post text", mapped.text)
    }

    @Test
    fun `recordWithMedia with empty video playlist falls through to EmbedUi_Unsupported`() {
        val emptyPlaylistMedia =
            """
            "media": {
              "${'$'}type": "app.bsky.embed.video#view",
              "cid": "bafkreifakevidcid0000000000000000000000000000",
              "playlist": ""
            }
            """.trimIndent()
        val mapped = decodeAndMapSingle(recordWithMediaJson(media = emptyPlaylistMedia))
        assertEquals(
            EmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia"),
            mapped.embed,
        )
    }

    @Test
    fun `recordWithMedia with unknown media variant falls through to EmbedUi_Unsupported`() {
        val unknownMedia =
            """
            "media": {
              "${'$'}type": "app.bsky.embed.somethingNew#view"
            }
            """.trimIndent()
        val mapped = decodeAndMapSingle(recordWithMediaJson(media = unknownMedia))
        assertEquals(
            EmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia"),
            mapped.embed,
        )
    }

    // ---------- FeedItemUi (cross-author thread cluster) ----------

    @Test
    fun `toFeedItemUiOrNull with reply == null produces Single`() {
        // timeline_typical entry [1] is a standalone post (no top-level reply).
        val response = decodeFixture("timeline_typical.json")
        val standalone = response.feed[1]
        val item = standalone.toFeedItemUiOrNull()
        assertInstanceOf(FeedItemUi.Single::class.java, item)
        val single = item as FeedItemUi.Single
        assertEquals(standalone.post.uri.raw, single.post.id)
    }

    @Test
    fun `toFeedItemUiOrNull with PostView parent and null grandparent produces ReplyCluster with hasEllipsis = false`() {
        val response = decodeFixture("timeline_with_reply.json")
        val item = response.feed.single().toFeedItemUiOrNull()
        assertInstanceOf(FeedItemUi.ReplyCluster::class.java, item)
        val cluster = item as FeedItemUi.ReplyCluster
        assertEquals("leaf.bsky.social", cluster.leaf.author.handle)
        assertEquals("parent.bsky.social", cluster.parent.author.handle)
        assertEquals("root.bsky.social", cluster.root.author.handle)
        assertFalse(cluster.hasEllipsis)
    }

    @Test
    fun `toFeedItemUiOrNull when grandparentAuthor did matches root author did produces ReplyCluster with hasEllipsis = false`() {
        // Fixture entry [0]: grandparentAuthor.did == root.author.did → no fold.
        val response = decodeFixture("timeline_with_reply_grandparent.json")
        val item = response.feed[0].toFeedItemUiOrNull()
        val cluster = assertInstanceOf(FeedItemUi.ReplyCluster::class.java, item)
        assertFalse(cluster.hasEllipsis)
    }

    @Test
    fun `toFeedItemUiOrNull when grandparentAuthor did differs from root author did produces ReplyCluster with hasEllipsis = true`() {
        // Fixture entry [1]: grandparentAuthor.did distinct from root.author.did → fold.
        val response = decodeFixture("timeline_with_reply_grandparent.json")
        val item = response.feed[1].toFeedItemUiOrNull()
        val cluster = assertInstanceOf(FeedItemUi.ReplyCluster::class.java, item)
        assertTrue(cluster.hasEllipsis)
    }

    @Test
    fun `toFeedItemUiOrNull with BlockedPost parent falls back to Single`() {
        val response = decodeFixture("timeline_with_reply_blocked_parent.json")
        val item = response.feed.single().toFeedItemUiOrNull()
        val single = assertInstanceOf(FeedItemUi.Single::class.java, item)
        assertEquals("leaf.bsky.social", single.post.author.handle)
    }

    @Test
    fun `toFeedItemUiOrNull with NotFoundPost parent falls back to Single`() {
        val response = decodeFixture("timeline_with_reply_notfound_parent.json")
        val item = response.feed.single().toFeedItemUiOrNull()
        val single = assertInstanceOf(FeedItemUi.Single::class.java, item)
        assertEquals("leaf.bsky.social", single.post.author.handle)
    }

    @Test
    fun `toFeedItemUiOrNull with direct reply to root produces ReplyCluster where parent_id equals root_id`() {
        // Wire shape where replyRef.parent.uri == replyRef.root.uri (any
        // direct reply to a root post — common for self-threads). The
        // mapper still produces both fields; the renderer (ThreadCluster)
        // is responsible for collapsing the duplicate slot. This test
        // locks the data-layer contract: parent.id and root.id are
        // the same string after projection.
        val response = decodeFixture("timeline_with_direct_reply_to_root.json")
        val item = response.feed.single().toFeedItemUiOrNull()
        val cluster = assertInstanceOf(FeedItemUi.ReplyCluster::class.java, item)
        assertEquals(cluster.root.id, cluster.parent.id)
        assertFalse(cluster.hasEllipsis)
    }

    /**
     * Builds a `getTimeline` JSON whose post embed is a
     * `recordWithMedia#view` carrying the supplied [recordSnippet] (a
     * `"record": {...}` block) and [media] (a `"media": {...}` block).
     * Defaults to a resolved viewRecord + standard text content for the
     * common happy-path tests.
     */
    private fun recordWithMediaJson(
        recordSnippet: String = resolvedRecordSnippet(),
        media: String,
    ): String =
        wrapAsTimelineJson(
            """
            "embed": {
              "${'$'}type": "app.bsky.embed.recordWithMedia#view",
              $recordSnippet,
              $media
            }
            """.trimIndent(),
        )

    private fun resolvedRecordSnippet(): String =
        """
        "record": {
          "${'$'}type": "app.bsky.embed.record#view",
          "record": {
            "${'$'}type": "app.bsky.embed.record#viewRecord",
            "uri": "at://did:plc:fake/app.bsky.feed.post/quoted",
            "cid": "bafyreifakequotedcid000000000000000000000000000",
            "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
            "indexedAt": "2026-04-26T12:00:00Z",
            "value": {
              "${'$'}type": "app.bsky.feed.post",
              "text": "quoted text",
              "createdAt": "2026-04-26T12:00:00Z"
            }
          }
        }
        """.trimIndent()

    private fun unavailableRecordSnippet(
        viewType: String,
        extra: String,
    ): String =
        """
        "record": {
          "${'$'}type": "app.bsky.embed.record#view",
          "record": {
            "${'$'}type": "app.bsky.embed.record#$viewType",
            "uri": "at://did:plc:fake/app.bsky.feed.post/missing",
            $extra
          }
        }
        """.trimIndent()

    private fun imagesMediaSnippet(count: Int): String {
        val images =
            (1..count).joinToString(",") { i ->
                """{ "thumb": "https://cdn/t$i.jpg", "fullsize": "https://cdn/f$i.jpg", "alt": "" }"""
            }
        return """
            "media": {
              "${'$'}type": "app.bsky.embed.images#view",
              "images": [$images]
            }
            """.trimIndent()
    }

    private fun externalMediaSnippet(): String =
        """
        "media": {
          "${'$'}type": "app.bsky.embed.external#view",
          "external": {
            "uri": "https://www.example.com/article",
            "title": "Article title",
            "description": "A short description"
          }
        }
        """.trimIndent()

    private fun videoMediaSnippet(): String =
        """
        "media": {
          "${'$'}type": "app.bsky.embed.video#view",
          "cid": "bafkreifakevidcid0000000000000000000000000000",
          "playlist": "https://video.bsky.app/m.m3u8",
          "aspectRatio": { "width": 1920, "height": 1080 }
        }
        """.trimIndent()

    private fun decodeFixture(name: String): GetTimelineResponse {
        val classLoader = checkNotNull(this::class.java.classLoader) { "test class loader missing" }
        val text =
            requireNotNull(classLoader.getResourceAsStream("fixtures/$name")) {
                "fixture $name not found on test classpath"
            }.bufferedReader().use { it.readText() }
        return json.decodeFromString(GetTimelineResponse.serializer(), text)
    }

    /**
     * Decode an inline `getTimeline` JSON string, then map the single
     * (and only) post via `toPostUiOrNull`, asserting non-null.
     * Centralizes the boilerplate the synthetic-JSON tests would
     * otherwise duplicate.
     */
    private fun decodeAndMapSingle(timelineJson: String): net.kikin.nubecita.data.models.PostUi {
        val response = json.decodeFromString(GetTimelineResponse.serializer(), timelineJson)
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        return mapped!!
    }

    /**
     * Builds a minimal one-post timeline whose post.embed slot is the
     * provided JSON snippet. The parent post itself carries text
     * "parent post text" — we assert this in the malformed-quoted-record
     * test to confirm the parent isn't dropped.
     */
    private fun wrapAsTimelineJson(embedJsonSnippet: String): String =
        """
        {
          "feed": [{
            "post": {
              "uri": "at://did:plc:fakeparent/app.bsky.feed.post/parent",
              "cid": "bafyreifakeparentcid00000000000000000000000000",
              "author": {
                "did": "did:plc:fakeparent",
                "handle": "parent.bsky.social"
              },
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "parent post text",
                "createdAt": "2026-04-26T12:00:00Z"
              },
              $embedJsonSnippet
            }
          }]
        }
        """.trimIndent()

    /**
     * Builds a timeline whose post embed is one of the lexicon's
     * `app.bsky.embed.record#view{NotFound,Blocked,Detached}` shapes.
     * [viewType] is the bare type fragment (e.g. "viewNotFound").
     */
    private fun quotedPostEmbedJson(viewType: String): String {
        val variantFields =
            when (viewType) {
                "viewNotFound" -> "\"notFound\": true"
                "viewBlocked" ->
                    """"blocked": true, "author": { "did": "did:plc:fake", "viewer": {} }"""
                "viewDetached" -> "\"detached\": true"
                else -> error("unknown viewType: $viewType")
            }
        return wrapAsTimelineJson(
            """
            "embed": {
              "${'$'}type": "app.bsky.embed.record#view",
              "record": {
                "${'$'}type": "app.bsky.embed.record#$viewType",
                "uri": "at://did:plc:fake/app.bsky.feed.post/missing",
                $variantFields
              }
            }
            """.trimIndent(),
        )
    }
}
