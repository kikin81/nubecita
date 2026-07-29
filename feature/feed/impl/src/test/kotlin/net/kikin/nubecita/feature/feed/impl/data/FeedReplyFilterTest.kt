package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.GetTimelineResponse
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.feeds.FeedViewPrefs
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for the Following-feed reply filter in `FeedReplyFilter.kt`.
 *
 * Mirrors `shouldDisplayReplyInFollowing` from the official client
 * (bluesky-social/social-app `src/lib/api/feed-manip.ts`):
 *
 *   isSelfOrFollowing(p) = p.did == viewerDid || p.viewer.following != null
 *
 *   1. !isSelfOrFollowing(author)                       -> HIDE
 *   2. parent/root/grandparent all absent or == author  -> SHOW (self-thread)
 *   3. any of parent/grandparent/root != author
 *      AND isSelfOrFollowing(that one)                  -> SHOW
 *   4. otherwise                                        -> HIDE
 *
 * Branch 3 is the one the lexicon prose ("hide replies in the feed if they
 * are not by followed users") does NOT describe, and is precisely what both
 * user reports were asking for: a person you follow replying to a stranger
 * must be hidden. See nubecita-1fmx.
 */
internal class FeedReplyFilterTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val viewer = "did:plc:viewer00000000000000000000"
    private val followed = "did:plc:followed000000000000000000"
    private val followed2 = "did:plc:followedtwo00000000000000"
    private val stranger = "did:plc:stranger00000000000000000"
    private val stranger2 = "did:plc:strangertwo00000000000000"

    // ---------- branch 1: author must be self or followed ----------

    @Test
    fun `a non-reply post is always shown`() {
        val entry = decode(feedEntry(uri = post(stranger, "1"), author = author(stranger)))
        assertTrue(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    @Test
    fun `a reply authored by someone the viewer does not follow is hidden`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(stranger, "1"),
                    author = author(stranger),
                    root = author(followed, post(followed, "root")),
                    parent = author(followed, post(followed, "parent")),
                ),
            )
        assertFalse(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    // ---------- branch 3: the reported bug ----------

    @Test
    fun `a reply by a followed account to a stranger is hidden`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    root = author(stranger, post(stranger, "root")),
                    parent = author(stranger, post(stranger, "parent")),
                ),
            )
        assertFalse(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    @Test
    fun `a reply by a followed account into a thread rooted by another followed account is shown`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    root = author(followed2, post(followed2, "root"), following = true),
                    parent = author(stranger, post(stranger, "parent")),
                ),
            )
        assertTrue(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    @Test
    fun `a reply by a followed account to another followed account is shown`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    root = author(followed2, post(followed2, "root"), following = true),
                    parent = author(followed2, post(followed2, "parent"), following = true),
                ),
            )
        assertTrue(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    @Test
    fun `a reply by a followed account to the viewer is shown`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    root = author(viewer, post(viewer, "root")),
                    parent = author(viewer, post(viewer, "parent")),
                ),
            )
        assertTrue(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    // ---------- branch 2: self-threads ----------

    @Test
    fun `a followed account replying to itself is shown`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "2"),
                    author = author(followed, following = true),
                    root = author(followed, post(followed, "1"), following = true),
                    parent = author(followed, post(followed, "1"), following = true),
                ),
            )
        assertTrue(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    /**
     * Matches the official client: passing step 1 (the author is the viewer)
     * is NOT sufficient on its own — a distinct ancestor author still has to be
     * self-or-followed. So the viewer's own reply into a stranger's thread is
     * hidden from the Following feed. The viewer already knows they wrote it,
     * and it would otherwise drag an unrelated thread into the timeline.
     */
    @Test
    fun `the viewer's own reply to a stranger is hidden`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(viewer, "1"),
                    author = author(viewer),
                    root = author(stranger, post(stranger, "root")),
                    parent = author(stranger, post(stranger, "parent")),
                ),
            )
        assertFalse(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    @Test
    fun `the viewer's own self-thread reply is shown`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(viewer, "2"),
                    author = author(viewer),
                    root = author(viewer, post(viewer, "1")),
                    parent = author(viewer, post(viewer, "1")),
                ),
            )
        assertTrue(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    // ---------- grandparent leg of branch 3 ----------

    @Test
    fun `a reply is shown when only the grandparent author is followed`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    root = author(stranger, post(stranger, "root")),
                    parent = author(stranger2, post(stranger2, "parent")),
                    grandparent = author(followed2, following = true),
                ),
            )
        assertTrue(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    // ---------- reposts are exempt ----------

    @Test
    fun `a repost of a stranger-to-stranger reply is shown`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(stranger, "1"),
                    author = author(stranger),
                    root = author(stranger2, post(stranger2, "root")),
                    parent = author(stranger2, post(stranger2, "parent")),
                    reposterDid = followed,
                ),
            )
        assertTrue(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    // ---------- non-PostView parent/root degrade to absent ----------

    @Test
    fun `a reply by a followed account whose parent is not found is hidden`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    root = author(stranger, post(stranger, "root")),
                    parentNotFound = true,
                ),
            )
        assertFalse(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    // ---------- preference gating ----------

    @Test
    fun `hideRepliesByUnfollowed false shows a followed reply to a stranger`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    root = author(stranger, post(stranger, "root")),
                    parent = author(stranger, post(stranger, "parent")),
                ),
            )
        val prefs = FeedViewPrefs.DEFAULT.copy(hideRepliesByUnfollowed = false)
        assertTrue(entry.shouldDisplayInFollowingFeed(prefs, viewer))
    }

    @Test
    fun `hideReplies true hides even a reply between two followed accounts`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    root = author(followed2, post(followed2, "root"), following = true),
                    parent = author(followed2, post(followed2, "parent"), following = true),
                ),
            )
        val prefs = FeedViewPrefs.DEFAULT.copy(hideReplies = true)
        assertFalse(entry.shouldDisplayInFollowingFeed(prefs, viewer))
    }

    @Test
    fun `hideReposts true hides a reposted entry`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    reposterDid = followed2,
                ),
            )
        val prefs = FeedViewPrefs.DEFAULT.copy(hideReposts = true)
        assertFalse(entry.shouldDisplayInFollowingFeed(prefs, viewer))
    }

    @Test
    fun `hideQuotePosts true hides a post quoting another post`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    embedJson = quoteEmbed(),
                ),
            )
        val prefs = FeedViewPrefs.DEFAULT.copy(hideQuotePosts = true)
        assertFalse(entry.shouldDisplayInFollowingFeed(prefs, viewer))
    }

    /**
     * `app.bsky.embed.record#view` also carries embedded feed generators, lists,
     * starter packs and labelers. Only an embedded POST is a quote post — hiding
     * a shared feed generator because "hide quote posts" is on would be wrong.
     */
    @Test
    fun `hideQuotePosts true does not hide a post embedding a feed generator`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    embedJson = generatorEmbed(),
                ),
            )
        val prefs = FeedViewPrefs.DEFAULT.copy(hideQuotePosts = true)
        assertTrue(entry.shouldDisplayInFollowingFeed(prefs, viewer))
    }

    @Test
    fun `a quote post is shown by default`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    embedJson = quoteEmbed(),
                ),
            )
        assertTrue(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewer))
    }

    @Test
    fun `a signed-out viewer with a null did still filters on follow state`() {
        val entry =
            decode(
                feedEntry(
                    uri = post(followed, "1"),
                    author = author(followed, following = true),
                    root = author(stranger, post(stranger, "root")),
                    parent = author(stranger, post(stranger, "parent")),
                ),
            )
        assertFalse(entry.shouldDisplayInFollowingFeed(FeedViewPrefs.DEFAULT, viewerDid = null))
    }

    // ---------- helpers ----------

    private fun post(
        did: String,
        rkey: String,
    ) = "at://$did/app.bsky.feed.post/$rkey"

    private data class Actor(
        val did: String,
        val uri: String?,
        val following: Boolean,
    )

    private fun author(
        did: String,
        uri: String? = null,
        following: Boolean = false,
    ) = Actor(did, uri, following)

    private fun Actor.profileJson(): String {
        val viewerBlock = if (following) """, "viewer": { "following": "at://$viewer/app.bsky.graph.follow/x" }""" else ""
        return """{ "did": "$did", "handle": "a${did.takeLast(4)}.bsky.social" $viewerBlock }"""
    }

    private fun Actor.postViewJson(): String =
        """
        {
          "${'$'}type": "app.bsky.feed.defs#postView",
          "uri": "$uri",
          "cid": "bafyreifakecid000000000000000000000000000000000",
          "author": ${profileJson()},
          "indexedAt": "2026-04-26T12:00:00Z",
          "record": {
            "${'$'}type": "app.bsky.feed.post",
            "text": "text",
            "createdAt": "2026-04-26T12:00:00Z"
          }
        }
        """.trimIndent()

    private fun quoteEmbed(): String =
        """
        {
          "${'$'}type": "app.bsky.embed.record#view",
          "record": {
            "${'$'}type": "app.bsky.embed.record#viewRecord",
            "uri": "at://$stranger/app.bsky.feed.post/quoted",
            "cid": "bafyreifakecid000000000000000000000000000000000",
            "author": { "did": "$stranger", "handle": "quoted.bsky.social" },
            "value": {
              "${'$'}type": "app.bsky.feed.post",
              "text": "quoted text",
              "createdAt": "2026-04-26T12:00:00Z"
            },
            "indexedAt": "2026-04-26T12:00:00Z"
          }
        }
        """.trimIndent()

    private fun generatorEmbed(): String =
        """
        {
          "${'$'}type": "app.bsky.embed.record#view",
          "record": {
            "${'$'}type": "app.bsky.feed.defs#generatorView",
            "uri": "at://$stranger/app.bsky.feed.generator/cool",
            "cid": "bafyreifakecid000000000000000000000000000000000",
            "did": "did:web:feed.example.com",
            "creator": { "did": "$stranger", "handle": "creator.bsky.social" },
            "displayName": "Cool feed",
            "indexedAt": "2026-04-26T12:00:00Z"
          }
        }
        """.trimIndent()

    private fun decode(entryJson: String): io.github.kikin81.atproto.app.bsky.feed.FeedViewPost {
        val payload = """{ "feed": [$entryJson] }"""
        return json.decodeFromString(GetTimelineResponse.serializer(), payload).feed.single()
    }

    private fun feedEntry(
        uri: String,
        author: Actor,
        root: Actor? = null,
        parent: Actor? = null,
        grandparent: Actor? = null,
        parentNotFound: Boolean = false,
        reposterDid: String? = null,
        embedJson: String? = null,
    ): String {
        val parentJson =
            when {
                parentNotFound ->
                    """{ "${'$'}type": "app.bsky.feed.defs#notFoundPost", "uri": "at://x/app.bsky.feed.post/gone", "notFound": true }"""
                parent != null -> parent.postViewJson()
                else -> null
            }
        val replyBlock =
            if (root == null && parentJson == null) {
                ""
            } else {
                val parts =
                    buildList {
                        root?.let { add(""""root": ${it.postViewJson()}""") }
                        parentJson?.let { add(""""parent": $it""") }
                        grandparent?.let { add(""""grandparentAuthor": ${it.profileJson()}""") }
                    }
                """"reply": { ${parts.joinToString(",")} },"""
            }
        val reasonBlock =
            reposterDid?.let {
                """
                "reason": {
                  "${'$'}type": "app.bsky.feed.defs#reasonRepost",
                  "by": { "did": "$it", "handle": "reposter.bsky.social" },
                  "indexedAt": "2026-04-26T12:00:00Z"
                },
                """.trimIndent()
            } ?: ""
        return """
            {
              "post": {
                "uri": "$uri",
                "cid": "bafyreifakecid000000000000000000000000000000000",
                "author": ${author.profileJson()},
                "indexedAt": "2026-04-26T12:00:00Z",
                "record": {
                  "${'$'}type": "app.bsky.feed.post",
                  "text": "text",
                  "createdAt": "2026-04-26T12:00:00Z"
                }${embedJson?.let { ",\n                \"embed\": $it" } ?: ""}
              },
              $replyBlock
              $reasonBlock
              "indexedAt": "2026-04-26T12:00:00Z"
            }
            """.trimIndent()
    }
}
