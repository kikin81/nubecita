package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.embed.ImagesView
import io.github.kikin81.atproto.app.bsky.feed.PostView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * STEP 0 GO/NO-GO SPIKE for `add-offline-feed-cache` PR2.
 *
 * The cache stores the serialized wire [PostView] (Option 1) and re-maps to
 * `PostUi` on read. That only works if a fully-populated [PostView] — crucially
 * one carrying a KNOWN open-union `embed` variant (e.g. [ImagesView]) plus the
 * raw `record` JsonObject — round-trips through `encodeToString` /
 * `decodeFromString` with full fidelity.
 *
 * The open union ([io.github.kikin81.atproto.app.bsky.feed.PostViewEmbedUnion])
 * is the risk: its `OpenUnionSerializer` re-injects the `$type` discriminator on
 * encode from the member serializer's `serialName`, and dispatches on `$type`
 * on decode. This test proves both directions survive for the known-variant
 * path (the dangerous one) — the Unknown variant is lossless by construction
 * (it stores the raw JsonObject verbatim).
 *
 * [cacheJson] mirrors `XrpcClient.DefaultJson` (`ignoreUnknownKeys = true`,
 * `explicitNulls = false`) — the same config `FeedMapping.recordJson` uses — so
 * encode drops nulls and decode tolerates server schema additions. We do NOT
 * reuse the `XrpcClient` instance's `json` (it isn't publicly reachable from a
 * decoded `PostView`); a locally-built `Json` with the same config is the
 * supported path and is what the production mappers will use.
 */
class WirePostSerializationSpikeTest {
    private val cacheJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    /** A minimal but realistic `app.bsky.feed.post` record JsonObject. */
    private fun recordJsonObject(text: String): JsonObject =
        buildJsonObject {
            put("\$type", "app.bsky.feed.post")
            put("text", text)
            put("createdAt", "2026-04-15T19:51:12.861Z")
            putJsonArray("langs") { add("en") }
        }

    /** Build a wire PostView with a KNOWN ImagesView embed + raw record. */
    private fun imagesPostView(): PostView {
        val wire =
            buildJsonObject {
                put("\$type", "app.bsky.feed.defs#postView")
                put("uri", "at://did:plc:abc/app.bsky.feed.post/3kpost")
                put("cid", "bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq")
                putJsonObject("author") {
                    put("did", "did:plc:abc")
                    put("handle", "alice.bsky.social")
                    put("displayName", "Alice")
                }
                put("record", recordJsonObject("hello with an image"))
                putJsonObject("embed") {
                    // KNOWN variant — carries the view-shape $type discriminator.
                    put("\$type", "app.bsky.embed.images#view")
                    putJsonArray("images") {
                        addJsonObject {
                            put("thumb", "https://cdn.example/thumb.jpg")
                            put("fullsize", "https://cdn.example/full.jpg")
                            put("alt", "tree of life art")
                            putJsonObject("aspectRatio") {
                                put("width", 980)
                                put("height", 1262)
                            }
                        }
                    }
                }
                put("indexedAt", "2026-04-15T19:51:13.000Z")
                put("replyCount", 2)
                put("repostCount", 5)
                put("likeCount", 9)
                putJsonObject("viewer") {
                    put("like", "at://did:plc:me/app.bsky.feed.like/3klike")
                }
            }
        return cacheJson.decodeFromJsonElement(PostView.serializer(), wire)
    }

    @Test
    fun `known-embed PostView round-trips through encode then decode`() {
        val original = imagesPostView()
        // Sanity: the embed decoded as the KNOWN variant, not Unknown.
        assertInstanceOf(ImagesView::class.java, original.embed)

        val encoded = cacheJson.encodeToString(PostView.serializer(), original)
        val decoded = cacheJson.decodeFromString(PostView.serializer(), encoded)

        // Scalars intact.
        assertEquals(original.uri.raw, decoded.uri.raw)
        assertEquals(original.cid.raw, decoded.cid.raw)
        assertEquals(original.author.did.raw, decoded.author.did.raw)
        assertEquals(original.author.handle.raw, decoded.author.handle.raw)
        assertEquals(original.indexedAt.raw, decoded.indexedAt.raw)
        assertEquals(original.replyCount, decoded.replyCount)
        assertEquals(original.repostCount, decoded.repostCount)
        assertEquals(original.likeCount, decoded.likeCount)

        // Raw record JsonObject intact (text/createdAt preserved verbatim).
        assertEquals(original.record, decoded.record)

        // Viewer like URI intact.
        assertEquals(original.viewer?.like?.raw, decoded.viewer?.like?.raw)

        // The embed survived as the SAME known variant with its payload.
        val decodedEmbed = assertInstanceOf(ImagesView::class.java, decoded.embed)
        assertEquals(1, decodedEmbed.images.size)
        assertEquals(
            "https://cdn.example/full.jpg",
            decodedEmbed.images
                .first()
                .fullsize.raw,
        )
        assertEquals("tree of life art", decodedEmbed.images.first().alt)
    }

    @Test
    fun `re-encoding the decoded PostView is byte-stable (idempotent)`() {
        val original = imagesPostView()
        val once = cacheJson.encodeToString(PostView.serializer(), original)
        val twice =
            cacheJson.encodeToString(
                PostView.serializer(),
                cacheJson.decodeFromString(PostView.serializer(), once),
            )
        assertEquals(once, twice)
    }

    @Test
    fun `null-embed PostView round-trips (no embed key emitted)`() {
        val wire =
            buildJsonObject {
                put("\$type", "app.bsky.feed.defs#postView")
                put("uri", "at://did:plc:abc/app.bsky.feed.post/3knoembed")
                put("cid", "bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq")
                putJsonObject("author") {
                    put("did", "did:plc:abc")
                    put("handle", "alice.bsky.social")
                }
                put("record", recordJsonObject("plain text post"))
                put("indexedAt", "2026-04-15T19:51:13.000Z")
            }
        val original = cacheJson.decodeFromJsonElement(PostView.serializer(), wire)
        assertTrue(original.embed == null)

        val encoded = cacheJson.encodeToString(PostView.serializer(), original)
        // explicitNulls = false drops the absent embed.
        assertTrue(!encoded.contains("\"embed\""))
        val decoded = cacheJson.decodeFromString(PostView.serializer(), encoded)
        assertTrue(decoded.embed == null)
        assertEquals(original.record, decoded.record)
    }
}
