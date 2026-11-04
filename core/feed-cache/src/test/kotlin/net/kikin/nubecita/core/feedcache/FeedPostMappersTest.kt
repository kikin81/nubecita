package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.LabelVisibility
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.EmbedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Build-config Json mirroring the production cache Json (decode helper). */
private val testJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

private fun recordObject(text: String): JsonObject =
    buildJsonObject {
        put("\$type", "app.bsky.feed.post")
        put("text", text)
        put("createdAt", "2026-04-15T19:51:12.861Z")
        putJsonArray("langs") { add("en") }
    }

/**
 * Build a wire [PostView] with optional images embed and optional labels.
 */
private fun postViewJson(
    uri: String = "at://did:plc:abc/app.bsky.feed.post/3kpost",
    authorDid: String = "did:plc:abc",
    text: String = "hello world",
    withImages: Boolean = true,
    labels: List<Pair<String, String>> = emptyList(),
): JsonObject {
    val wire =
        buildJsonObject {
            put("\$type", "app.bsky.feed.defs#postView")
            put("uri", uri)
            put("cid", "bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq")
            putJsonObject("author") {
                put("did", authorDid)
                put("handle", "alice.bsky.social")
                put("displayName", "Alice")
            }
            put("record", recordObject(text))
            if (withImages) {
                putJsonObject("embed") {
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
            }
            put("indexedAt", "2026-04-15T19:51:13.000Z")
            put("replyCount", 2)
            put("repostCount", 5)
            put("likeCount", 9)
            put("quoteCount", 1)
            putJsonObject("viewer") {
                put("like", "at://did:plc:me/app.bsky.feed.like/3klike")
            }
            if (labels.isNotEmpty()) {
                putJsonArray("labels") {
                    labels.forEach { (src, value) ->
                        addJsonObject {
                            put("src", src)
                            put("uri", uri)
                            put("val", value)
                            put("cts", "2026-04-15T19:51:13.000Z")
                        }
                    }
                }
            }
        }
    return wire
}

/**
 * The decoded form of [postViewJson].
 *
 * The two are kept separate because a union slot (`reply.root`,
 * `reply.parent`) is matched on its `$type` discriminator, and `$type` is not a
 * declared property of `PostView` — so re-encoding an already-decoded
 * `PostView` drops it and the slot deserializes to the open-union Unknown
 * variant instead. Union slots must be given the raw JSON.
 */
private fun postView(
    uri: String = "at://did:plc:abc/app.bsky.feed.post/3kpost",
    authorDid: String = "did:plc:abc",
    text: String = "hello world",
    withImages: Boolean = true,
    labels: List<Pair<String, String>> = emptyList(),
): PostView =
    testJson.decodeFromJsonElement(
        PostView.serializer(),
        postViewJson(uri, authorDid, text, withImages, labels),
    )

private fun feedViewPost(post: PostView): FeedViewPost = FeedViewPost(post = post)

/**
 * Build a wire [FeedViewPost] through real JSON so the per-entry `reason` and
 * `reply` unions go through actual deserialization rather than being
 * hand-constructed — the union variants are what the denormalized columns and
 * the grouping projection read, so a fixture that skipped them would not
 * exercise the thing under test.
 *
 * [repostedBy] adds a `reasonRepost`; [replyRoot]/[replyParent] add a
 * `replyRef` whose slots are readable posts. [blockedParent] instead makes the
 * parent a `blockedPost` tombstone, which has a URI but is not a readable post.
 */
private fun feedViewPostWire(
    post: JsonObject = postViewJson(),
    repostedBy: String? = null,
    replyRoot: String? = null,
    replyParent: String? = null,
    blockedParent: String? = null,
): FeedViewPost {
    val wire =
        buildJsonObject {
            put("\$type", "app.bsky.feed.defs#feedViewPost")
            put("post", post)
            if (repostedBy != null) {
                putJsonObject("reason") {
                    put("\$type", "app.bsky.feed.defs#reasonRepost")
                    putJsonObject("by") {
                        put("did", repostedBy)
                        put("handle", "reposter.bsky.social")
                        put("displayName", "Reposter")
                    }
                    put("indexedAt", "2026-04-15T20:00:00.000Z")
                }
            }
            if (replyRoot != null || replyParent != null || blockedParent != null) {
                putJsonObject("reply") {
                    if (replyRoot != null) {
                        put("root", postViewJson(uri = replyRoot))
                    }
                    if (blockedParent != null) {
                        putJsonObject("parent") {
                            put("\$type", "app.bsky.feed.defs#blockedPost")
                            put("uri", blockedParent)
                            put("blocked", true)
                            putJsonObject("author") {
                                put("did", "did:plc:blocked")
                            }
                        }
                    } else if (replyParent != null) {
                        put("parent", postViewJson(uri = replyParent))
                    }
                }
            }
        }
    return testJson.decodeFromJsonElement(FeedViewPost.serializer(), wire)
}

private val key = FeedKey.following("did:plc:me")

internal class FeedPostMappersTest {
    @Test
    fun `toFeedPostEntity extracts queryable columns`() {
        val fvp = feedViewPost(postView())
        val entity = fvp.toFeedPostEntity(key, position = 3)

        assertEquals("did:plc:me", entity.accountDid)
        assertEquals("FOLLOWING", entity.feedType)
        assertEquals("", entity.feedUri)
        assertEquals(3, entity.position)
        assertEquals("at://did:plc:abc/app.bsky.feed.post/3kpost", entity.uri)
        assertEquals("bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq", entity.cid)
        assertEquals("did:plc:abc", entity.authorDid)
        assertEquals("hello world", entity.text)
        // indexedAt comes from the post's indexedAt (2026-04-15T19:51:13Z),
        // NOT the record createdAt (2026-04-15T19:51:12.861Z).
        assertEquals(
            kotlin.time.Instant.parse("2026-04-15T19:51:13.000Z"),
            entity.indexedAt,
        )
        assertNotNull(entity.entryBlob)
    }

    @Test
    fun `plain entry leaves the per-entry columns null`() {
        val entity = feedViewPostWire().toFeedPostEntity(key, position = 0)

        assertNull(entity.repostedByDid)
        assertNull(entity.replyRootUri)
        assertNull(entity.replyParentUri)
    }

    @Test
    fun `toFeedPostEntity denormalizes repost attribution`() {
        val entity = feedViewPostWire(repostedBy = "did:plc:reposter").toFeedPostEntity(key, position = 0)

        assertEquals("did:plc:reposter", entity.repostedByDid)
    }

    @Test
    fun `toFeedPostEntity denormalizes reply root and parent`() {
        val entity =
            feedViewPostWire(
                replyRoot = "at://did:plc:abc/app.bsky.feed.post/3kroot",
                replyParent = "at://did:plc:abc/app.bsky.feed.post/3kparent",
            ).toFeedPostEntity(key, position = 0)

        assertEquals("at://did:plc:abc/app.bsky.feed.post/3kroot", entity.replyRootUri)
        assertEquals("at://did:plc:abc/app.bsky.feed.post/3kparent", entity.replyParentUri)
    }

    @Test
    fun `a blocked reply parent denormalizes to null but survives in the blob`() {
        // The column points at readable posts only, so a tombstone parent is
        // null there. The blob must still carry it, or the projection could not
        // render the Blocked variant on read.
        val entity =
            feedViewPostWire(
                replyRoot = "at://did:plc:abc/app.bsky.feed.post/3kroot",
                blockedParent = "at://did:plc:blocked/app.bsky.feed.post/3kgone",
            ).toFeedPostEntity(key, position = 0)

        assertNull(entity.replyParentUri)
        assertEquals("at://did:plc:abc/app.bsky.feed.post/3kroot", entity.replyRootUri)

        val decoded = testJson.decodeFromString(FeedViewPost.serializer(), requireNotNull(entity.entryBlob))
        assertNotNull(decoded.reply?.parent)
    }

    @Test
    fun `entry blob round-trips the repost and reply context`() {
        // The regression this widening exists to prevent: the mapper used to
        // serialize the bare PostView, so reason and reply were discarded and a
        // cached partition could not rebuild a ReplyCluster or a chain.
        val entity =
            feedViewPostWire(
                repostedBy = "did:plc:reposter",
                replyRoot = "at://did:plc:abc/app.bsky.feed.post/3kroot",
                replyParent = "at://did:plc:abc/app.bsky.feed.post/3kparent",
            ).toFeedPostEntity(key, position = 0)

        val decoded = testJson.decodeFromString(FeedViewPost.serializer(), requireNotNull(entity.entryBlob))

        assertNotNull(decoded.reason)
        assertNotNull(decoded.reply)
        assertEquals(
            "at://did:plc:abc/app.bsky.feed.post/3kroot",
            (decoded.reply?.root as? PostView)?.uri?.raw,
        )
        assertEquals(
            "at://did:plc:abc/app.bsky.feed.post/3kparent",
            (decoded.reply?.parent as? PostView)?.uri?.raw,
        )
        assertEquals("at://did:plc:abc/app.bsky.feed.post/3kpost", decoded.post.uri.raw)
    }

    @Test
    fun `write-then-read round-trip preserves author text embed stats viewer`() {
        val fvp = feedViewPost(postView())
        val entity = fvp.toFeedPostEntity(key, position = 0)

        val postUi = entity.toPostUi(viewerDid = "did:plc:me", prefs = ModerationPrefs.DEFAULT)

        assertNotNull(postUi)
        requireNotNull(postUi)
        assertEquals("at://did:plc:abc/app.bsky.feed.post/3kpost", postUi.id)
        assertEquals("did:plc:abc", postUi.author.did)
        assertEquals("Alice", postUi.author.displayName)
        assertEquals("hello world", postUi.text)
        assertEquals(2, postUi.stats.replyCount)
        assertEquals(5, postUi.stats.repostCount)
        assertEquals(9, postUi.stats.likeCount)
        assertEquals(1, postUi.stats.quoteCount)
        assertEquals(true, postUi.viewer.isLikedByViewer)
        assertInstanceOf(EmbedUi.Images::class.java, postUi.embed)
    }

    @Test
    fun `malformed blob maps to null`() {
        val entity =
            FeedViewPost(post = postView()).toFeedPostEntity(key, position = 0).copy(entryBlob = "{ not valid json")
        val postUi = entity.toPostUi(viewerDid = null, prefs = ModerationPrefs.DEFAULT)
        assertNull(postUi)
    }

    @Test
    fun `null blob maps to null`() {
        val entity = FeedViewPost(post = postView()).toFeedPostEntity(key, position = 0).copy(entryBlob = null)
        val postUi = entity.toPostUi(viewerDid = null, prefs = ModerationPrefs.DEFAULT)
        assertNull(postUi)
    }

    @Test
    fun `adult-labeled post is dropped on read when adult filtering hides it`() {
        // A porn-labeled post; DEFAULT prefs have adult content disabled, which
        // forces a non-overridable HIDE -> dropFiltered = true returns null.
        val labeled =
            postView(
                authorDid = "did:plc:other",
                labels = listOf("did:plc:labeler" to "porn"),
            )
        val entity = FeedViewPost(post = labeled).toFeedPostEntity(key, position = 0)

        val postUi = entity.toPostUi(viewerDid = "did:plc:me", prefs = ModerationPrefs.DEFAULT)
        assertNull(postUi)
    }

    @Test
    fun `adult-labeled post is covered (not dropped) when adult content is enabled`() {
        val prefs =
            ModerationPrefs.DEFAULT.copy(
                adultContentEnabled = true,
                visibilities = ModerationPrefs.DEFAULT.visibilities + (ContentLabel.PORN to LabelVisibility.WARN),
            )
        val labeled =
            postView(
                authorDid = "did:plc:other",
                labels = listOf("did:plc:labeler" to "porn"),
            )
        val entity = FeedViewPost(post = labeled).toFeedPostEntity(key, position = 0)

        val postUi = entity.toPostUi(viewerDid = "did:plc:me", prefs = prefs)
        assertNotNull(postUi)
        requireNotNull(postUi)
        // Media is covered with a content warning rather than dropped.
        val embed = postUi.embed
        assertInstanceOf(EmbedUi.Images::class.java, embed)
        assertNotNull((embed as EmbedUi.Images).contentWarning)
    }
}
