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
import net.kikin.nubecita.core.database.model.CachedFeedEntry
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
    reposterDisplayName: String? = "Reposter",
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
                        if (reposterDisplayName != null) put("displayName", reposterDisplayName)
                    }
                    put("indexedAt", "2026-04-15T20:00:00.000Z")
                }
            }
            if (replyRoot != null || replyParent != null || blockedParent != null) {
                // `replyRef.parent` is REQUIRED by the lexicon, so a reply entry
                // always carries both slots — a root with no parent is not a
                // shape the server can send, and emitting one here would only
                // test the fixture. A root given alone becomes a direct reply to
                // itself, which is what a top-level reply actually looks like.
                putJsonObject("reply") {
                    val root = replyRoot ?: replyParent ?: blockedParent
                    put("root", postViewJson(uri = requireNotNull(root)))
                    if (blockedParent == null && replyParent == null) {
                        put("parent", postViewJson(uri = requireNotNull(root)))
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
    /**
     * The joined read shape for one wire entry: its envelope paired with the
     * stored copy of its own post.
     *
     * Built from the two production write mappers rather than hand-assembled,
     * so a test cannot accidentally pair an envelope with a post the write path
     * would never have produced.
     */
    private fun FeedViewPost.asCachedEntry(position: Int = 0): CachedFeedEntry {
        val envelope = toFeedPostEntity(key, position)
        val stored = toCachedPosts(key).first { it.uri == envelope.uri }
        return CachedFeedEntry(
            position = envelope.position,
            uri = envelope.uri,
            repostedByDid = envelope.repostedByDid,
            repostedByLabel = envelope.repostedByLabel,
            replyRootUri = envelope.replyRootUri,
            replyParentUri = envelope.replyParentUri,
            post = stored,
        )
    }

    @Test
    fun `toFeedPostEntity builds the entry envelope and nothing else`() {
        val entity = feedViewPostWire().toFeedPostEntity(key, position = 3)

        assertEquals("did:plc:me", entity.accountDid)
        assertEquals("FOLLOWING", entity.feedType)
        assertEquals("", entity.feedUri)
        assertEquals(3, entity.position)
        // The envelope references the post; it does not contain it.
        assertEquals("at://did:plc:abc/app.bsky.feed.post/3kpost", entity.uri)
    }

    @Test
    fun `toCachedPosts stores the entry's own post with its queryable columns`() {
        val stored = feedViewPostWire().toCachedPosts(key).single()

        assertEquals("did:plc:me", stored.accountDid)
        assertEquals("at://did:plc:abc/app.bsky.feed.post/3kpost", stored.uri)
        assertEquals("bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq", stored.cid)
        assertEquals("did:plc:abc", stored.authorDid)
        assertEquals("hello world", stored.text)
        // indexedAt comes from the post's indexedAt (2026-04-15T19:51:13Z),
        // NOT the record createdAt (2026-04-15T19:51:12.861Z).
        assertEquals(kotlin.time.Instant.parse("2026-04-15T19:51:13.000Z"), stored.indexedAt)
        assertNotNull(stored.postBlob)
        assertNull(stored.unavailable)
    }

    @Test
    fun `toCachedPosts also stores a reply's root and parent`() {
        // These usually are not timeline entries of their own, so if this
        // mapper does not capture them nothing does, and the cluster projection
        // would have a root it cannot render.
        val posts =
            feedViewPostWire(
                replyRoot = "at://did:plc:abc/app.bsky.feed.post/3kroot",
                replyParent = "at://did:plc:abc/app.bsky.feed.post/3kparent",
            ).toCachedPosts(key)

        assertEquals(
            listOf(
                "at://did:plc:abc/app.bsky.feed.post/3kparent",
                "at://did:plc:abc/app.bsky.feed.post/3kpost",
                "at://did:plc:abc/app.bsky.feed.post/3kroot",
            ),
            posts.map { it.uri }.sorted(),
        )
    }

    @Test
    fun `an unreadable reply parent is stored as a tombstone row`() {
        // Omitting it would leave the projection unable to tell "this reply had
        // a parent you cannot see" from "this is a root post".
        val posts =
            feedViewPostWire(
                replyRoot = "at://did:plc:abc/app.bsky.feed.post/3kroot",
                blockedParent = "at://did:plc:blocked/app.bsky.feed.post/3kgone",
            ).toCachedPosts(key)

        val tombstone = posts.single { it.uri == "at://did:plc:blocked/app.bsky.feed.post/3kgone" }
        assertEquals("blocked", tombstone.unavailable)
        assertNull(tombstone.postBlob)
        assertNull(tombstone.toPostViewOrNull())
    }

    @Test
    fun `plain entry leaves the per-entry columns null`() {
        val entity = feedViewPostWire().toFeedPostEntity(key, position = 0)

        assertNull(entity.repostedByDid)
        assertNull(entity.repostedByLabel)
        assertNull(entity.replyRootUri)
        assertNull(entity.replyParentUri)
    }

    @Test
    fun `toFeedPostEntity denormalizes repost attribution`() {
        val entity = feedViewPostWire(repostedBy = "did:plc:reposter").toFeedPostEntity(key, position = 0)

        assertEquals("did:plc:reposter", entity.repostedByDid)
        assertEquals("Reposter", entity.repostedByLabel)
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
    fun `a blocked reply parent contributes no URI column but still a stored row`() {
        val entry =
            feedViewPostWire(
                replyRoot = "at://did:plc:abc/app.bsky.feed.post/3kroot",
                blockedParent = "at://did:plc:blocked/app.bsky.feed.post/3kgone",
            )

        // The column points at posts the viewer can read.
        assertNull(entry.toFeedPostEntity(key, position = 0).replyParentUri)
        // The row exists regardless, so the tombstone is still renderable.
        assertNotNull(entry.toCachedPosts(key).singleOrNull { it.unavailable == "blocked" })
    }

    @Test
    fun `write-then-read round-trip preserves author text embed stats viewer`() {
        val postUi = feedViewPostWire().asCachedEntry().toPostUi("did:plc:me", ModerationPrefs.DEFAULT)

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
    fun `read surfaces repost attribution preferring display name`() {
        val postUi =
            feedViewPostWire(repostedBy = "did:plc:reposter")
                .asCachedEntry()
                .toPostUi("did:plc:me", ModerationPrefs.DEFAULT)

        assertEquals("Reposter", requireNotNull(postUi).repostedBy)
    }

    @Test
    fun `read falls back to the handle when the reposter has no display name`() {
        // displayName ?: handle must mirror the network path's derivation, or
        // the same post would change its label when the feed refreshes.
        val postUi =
            feedViewPostWire(repostedBy = "did:plc:reposter", reposterDisplayName = null)
                .asCachedEntry()
                .toPostUi("did:plc:me", ModerationPrefs.DEFAULT)

        assertEquals("reposter.bsky.social", requireNotNull(postUi).repostedBy)
    }

    @Test
    fun `read leaves repost attribution null for a plain entry`() {
        val postUi = feedViewPostWire().asCachedEntry().toPostUi("did:plc:me", ModerationPrefs.DEFAULT)

        assertNull(requireNotNull(postUi).repostedBy)
    }

    @Test
    fun `malformed blob maps to null`() {
        val entry = feedViewPostWire().asCachedEntry()
        val broken = entry.copy(post = entry.post.copy(postBlob = "{ not valid json"))

        assertNull(broken.toPostUi(viewerDid = null, prefs = ModerationPrefs.DEFAULT))
    }

    @Test
    fun `null blob maps to null`() {
        val entry = feedViewPostWire().asCachedEntry()
        val broken = entry.copy(post = entry.post.copy(postBlob = null))

        assertNull(broken.toPostUi(viewerDid = null, prefs = ModerationPrefs.DEFAULT))
    }

    @Test
    fun `a tombstone entry maps to null`() {
        val entry = feedViewPostWire().asCachedEntry()
        val tombstone = entry.copy(post = entry.post.copy(unavailable = "blocked", postBlob = null))

        assertNull(tombstone.toPostUi(viewerDid = null, prefs = ModerationPrefs.DEFAULT))
    }

    @Test
    fun `toPostViewOrNull is the single decode seam and skips a bad row`() {
        val entry = feedViewPostWire().asCachedEntry()

        assertNotNull(entry.post.toPostViewOrNull())
        assertNull(entry.post.copy(postBlob = null).toPostViewOrNull())
        assertNull(entry.post.copy(postBlob = "{ not valid json").toPostViewOrNull())
        assertNull(entry.post.copy(unavailable = "notfound").toPostViewOrNull())
    }

    @Test
    fun `adult-labeled post is dropped on read when adult filtering hides it`() {
        // A porn-labeled post; DEFAULT prefs have adult content disabled, which
        // forces a non-overridable HIDE -> dropFiltered = true returns null.
        val entry =
            feedViewPostWire(
                post = postViewJson(authorDid = "did:plc:other", labels = listOf("did:plc:labeler" to "porn")),
            ).asCachedEntry()

        assertNull(entry.toPostUi(viewerDid = "did:plc:me", prefs = ModerationPrefs.DEFAULT))
    }

    @Test
    fun `adult-labeled post is covered (not dropped) when adult content is enabled`() {
        val entry =
            feedViewPostWire(
                post = postViewJson(authorDid = "did:plc:other", labels = listOf("did:plc:labeler" to "porn")),
            ).asCachedEntry()

        val prefs =
            ModerationPrefs(
                adultContentEnabled = true,
                visibilities = mapOf(ContentLabel.PORN to LabelVisibility.WARN),
            )

        val postUi = entry.toPostUi(viewerDid = "did:plc:me", prefs = prefs)

        assertNotNull(postUi)
    }
}
