package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.feed.GetAuthorFeedResponse
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.LabelVisibility
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AuthorFeedMapper]'s small public surface — the
 * per-tab filter mapping + the request-construction helper.
 *
 * The wire-to-`TabItemUi` projection itself delegates to
 * `:core:feed-mapping`'s `toPostUiCore` — that mapping is exercised
 * by `:feature:feed:impl`'s `FeedViewPostMapperTest` and we DON'T
 * duplicate those assertions here. Constructing
 * [io.github.kikin81.atproto.app.bsky.feed.PostView] fixtures by
 * hand would require carrying ~25 fields per fixture with their
 * own value-class wrappers; the regression risk that justifies
 * a duplicated suite is low.
 */
internal class AuthorFeedMapperTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `toTabItems keeps an authors original post and their own repost as distinct entries`() {
        // Synthetic JSON with the same post twice: once as the original
        // (no reason) and once as the author's own repost (reasonRepost).
        // getAuthorFeed returns both, and bsky.app renders both. They share
        // one postUri but MUST produce two entries with distinct LazyColumn
        // keys — folding the reposter DID into `key` keeps Compose from
        // crashing on a duplicate slot key without dropping the repost.
        val duplicateJson =
            """
            {
              "feed": [
                {
                  "post": {
                    "uri": "at://did:plc:alice/app.bsky.feed.post/1",
                    "cid": "cid1",
                    "author": { "did": "did:plc:alice", "handle": "alice.bsky.social" },
                    "indexedAt": "2026-06-02T12:00:00Z",
                    "record": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "original post",
                      "createdAt": "2026-06-02T12:00:00Z"
                    }
                  }
                },
                {
                  "post": {
                    "uri": "at://did:plc:alice/app.bsky.feed.post/1",
                    "cid": "cid1",
                    "author": { "did": "did:plc:alice", "handle": "alice.bsky.social" },
                    "indexedAt": "2026-06-02T13:00:00Z",
                    "record": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "original post",
                      "createdAt": "2026-06-02T12:00:00Z"
                    }
                  },
                  "reason": {
                    "${'$'}type": "app.bsky.feed.defs#reasonRepost",
                    "by": { "did": "did:plc:alice", "handle": "alice.bsky.social" },
                    "indexedAt": "2026-06-02T13:00:00Z"
                  }
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString(GetAuthorFeedResponse.serializer(), duplicateJson)
        val items = response.feed.toTabItems(ProfileTab.Posts, ModerationPrefs.DEFAULT, viewerDid = null)

        // Both entries survive — same post, distinct slot keys.
        assertEquals(2, items.size)
        assertEquals(
            listOf(
                "at://did:plc:alice/app.bsky.feed.post/1",
                "at://did:plc:alice/app.bsky.feed.post/1",
            ),
            items.map { it.postUri },
        )
        assertEquals(
            listOf(
                "at://did:plc:alice/app.bsky.feed.post/1",
                "repost:did:plc:alice:at://did:plc:alice/app.bsky.feed.post/1",
            ),
            items.map { it.key },
        )
    }

    @Test
    fun `toTabItems Media tab keeps an authors original media post and their own repost as distinct entries`() {
        // Synthetic JSON with the same media post twice: original + the
        // author's own repost. Both render in the grid (matching bsky.app);
        // distinct keys keep the grid from crashing on a duplicate slot key.
        val duplicateMediaJson =
            """
            {
              "feed": [
                {
                  "post": {
                    "uri": "at://did:plc:alice/app.bsky.feed.post/m1",
                    "cid": "cid1",
                    "author": { "did": "did:plc:alice", "handle": "alice.bsky.social" },
                    "indexedAt": "2026-06-02T12:00:00Z",
                    "record": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "post with image",
                      "createdAt": "2026-06-02T12:00:00Z"
                    },
                    "embed": {
                      "${'$'}type": "app.bsky.embed.images#view",
                      "images": [{ "thumb": "https://cdn/t1.jpg", "fullsize": "https://cdn/f1.jpg", "alt": "" }]
                    }
                  }
                },
                {
                  "post": {
                    "uri": "at://did:plc:alice/app.bsky.feed.post/m1",
                    "cid": "cid1",
                    "author": { "did": "did:plc:alice", "handle": "alice.bsky.social" },
                    "indexedAt": "2026-06-02T13:00:00Z",
                    "record": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "post with image",
                      "createdAt": "2026-06-02T12:00:00Z"
                    },
                    "embed": {
                      "${'$'}type": "app.bsky.embed.images#view",
                      "images": [{ "thumb": "https://cdn/t1.jpg", "fullsize": "https://cdn/f1.jpg", "alt": "" }]
                    }
                  },
                  "reason": {
                    "${'$'}type": "app.bsky.feed.defs#reasonRepost",
                    "by": { "did": "did:plc:alice", "handle": "alice.bsky.social" },
                    "indexedAt": "2026-06-02T13:00:00Z"
                  }
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString(GetAuthorFeedResponse.serializer(), duplicateMediaJson)
        val items = response.feed.toTabItems(ProfileTab.Media, ModerationPrefs.DEFAULT, viewerDid = null)

        // Both media entries survive — same post, distinct slot keys.
        assertEquals(2, items.size)
        assertTrue(items.all { it is TabItemUi.MediaCell })
        assertEquals(
            listOf(
                "at://did:plc:alice/app.bsky.feed.post/m1",
                "repost:did:plc:alice:at://did:plc:alice/app.bsky.feed.post/m1",
            ),
            items.map { it.key },
        )
    }

    @Test
    fun `Posts tab maps to posts_no_replies`() {
        assertEquals("posts_no_replies", ProfileTab.Posts.toAuthorFeedFilter())
    }

    @Test
    fun `Replies tab maps to posts_with_replies`() {
        assertEquals("posts_with_replies", ProfileTab.Replies.toAuthorFeedFilter())
    }

    @Test
    fun `Media tab maps to posts_with_media`() {
        assertEquals("posts_with_media", ProfileTab.Media.toAuthorFeedFilter())
    }

    @Test
    fun `buildAuthorFeedRequest carries actor cursor and limit through`() {
        val req =
            buildAuthorFeedRequest(
                actor = "did:plc:alice",
                tab = ProfileTab.Replies,
                cursor = "cursor-page-2",
                limit = 25,
            )
        assertEquals("did:plc:alice", req.actor.raw)
        assertEquals("posts_with_replies", req.filter)
        assertEquals("cursor-page-2", req.cursor)
        assertEquals(25L, req.limit)
    }

    @Test
    fun `buildAuthorFeedRequest forwards null cursor unchanged`() {
        val req =
            buildAuthorFeedRequest(
                actor = "did:plc:alice",
                tab = ProfileTab.Posts,
                cursor = null,
                limit = 30,
            )
        assertEquals(null, req.cursor)
    }

    @Test
    fun `buildGetProfileRequest wraps actor`() {
        val req = buildGetProfileRequest(actor = "did:plc:alice")
        assertEquals("did:plc:alice", req.actor.raw)
    }

    // --- moderation wiring (twmt.4) ---

    // A single image post carrying one self-label. `labelValue` drives the
    // moderation decision against the prefs the test supplies.
    private fun labeledImagePostFeed(labelValue: String): String =
        """
        {
          "feed": [
            {
              "post": {
                "uri": "at://did:plc:bob/app.bsky.feed.post/m2",
                "cid": "cid2",
                "author": { "did": "did:plc:bob", "handle": "bob.bsky.social" },
                "indexedAt": "2026-06-02T12:00:00Z",
                "record": {
                  "${'$'}type": "app.bsky.feed.post",
                  "text": "labeled media",
                  "createdAt": "2026-06-02T12:00:00Z"
                },
                "embed": {
                  "${'$'}type": "app.bsky.embed.images#view",
                  "images": [{ "thumb": "https://cdn/t2.jpg", "fullsize": "https://cdn/f2.jpg", "alt": "" }]
                },
                "labels": [
                  {
                    "src": "did:plc:labeler",
                    "uri": "at://did:plc:bob/app.bsky.feed.post/m2",
                    "val": "$labelValue",
                    "cts": "2026-06-02T12:00:00Z"
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `Media tab drops a warned media post rather than show an uncovered thumbnail`() {
        // nudity is NOT adult-gated, so a per-category WARN override resolves to a
        // cover (kept-but-warned), not a hard filter — the case that exercises the
        // grid's covered-drop path specifically (not the applyModeration drop).
        val prefs =
            ModerationPrefs(
                adultContentEnabled = false,
                visibilities = ModerationPrefs.DEFAULT.visibilities + (ContentLabel.NUDITY to LabelVisibility.WARN),
            )
        val response = json.decodeFromString(GetAuthorFeedResponse.serializer(), labeledImagePostFeed("nudity"))

        val items = response.feed.toTabItems(ProfileTab.Media, prefs, viewerDid = null)

        assertTrue(items.isEmpty(), "a covered media post must be dropped from the grid, not shown uncovered")
    }

    @Test
    fun `Posts tab keeps a warned media post and covers its media`() {
        val prefs =
            ModerationPrefs(
                adultContentEnabled = false,
                visibilities = ModerationPrefs.DEFAULT.visibilities + (ContentLabel.NUDITY to LabelVisibility.WARN),
            )
        val response = json.decodeFromString(GetAuthorFeedResponse.serializer(), labeledImagePostFeed("nudity"))

        val items = response.feed.toTabItems(ProfileTab.Posts, prefs, viewerDid = null)

        assertEquals(1, items.size, "post-list surfaces cover warned media, they don't drop it")
        val embed = (items[0] as TabItemUi.Post).post.embed
        assertNotNull(
            (embed as? EmbedUi.Images)?.contentWarning,
            "the warned image embed must carry a content-warning cover",
        )
    }

    @Test
    fun `Posts tab drops a hard-filtered post`() {
        // porn under the default (adult-off) gate resolves to a forced hide →
        // dropFiltered = true removes it from the list surface entirely.
        val response = json.decodeFromString(GetAuthorFeedResponse.serializer(), labeledImagePostFeed("porn"))

        val items = response.feed.toTabItems(ProfileTab.Posts, ModerationPrefs.DEFAULT, viewerDid = null)

        assertTrue(items.isEmpty(), "a hard-filtered post must not appear in a profile list")
    }

    @Test
    fun `the author's own filtered post is exempt and kept`() {
        // Viewer == author: never filtered. Same porn label + adult-off prefs that
        // drop a stranger's post above must keep the viewer's own post.
        val response = json.decodeFromString(GetAuthorFeedResponse.serializer(), labeledImagePostFeed("porn"))

        val items = response.feed.toTabItems(ProfileTab.Posts, ModerationPrefs.DEFAULT, viewerDid = "did:plc:bob")

        assertEquals(1, items.size, "the viewer's own labeled content is exempt from moderation")
    }

    @Test
    fun `every ProfileTab maps to a known atproto filter value`() {
        // Per the lexicon, the legal filter strings are
        // posts_no_replies / posts_with_replies / posts_with_media /
        // posts_and_author_threads / posts_with_video. The mapper MUST
        // pick from this set so the appview accepts the request.
        val legal =
            setOf(
                "posts_no_replies",
                "posts_with_replies",
                "posts_with_media",
                "posts_and_author_threads",
                "posts_with_video",
            )
        // Likes is served by getActorLikes, not getAuthorFeed, so it has no
        // filter value (toAuthorFeedFilter() throws by design). Exclude it.
        ProfileTab.entries.filter { it != ProfileTab.Likes }.forEach { tab ->
            assertTrue(
                tab.toAuthorFeedFilter() in legal,
                "ProfileTab.$tab MUST map to one of the lexicon filter values; got ${tab.toAuthorFeedFilter()}",
            )
        }
    }

    @Test
    fun `Likes tab has no getAuthorFeed filter`() {
        // getActorLikes is a distinct endpoint; asking for a filter is a
        // programming error, guarded by toAuthorFeedFilter().
        assertThrows(IllegalStateException::class.java) {
            ProfileTab.Likes.toAuthorFeedFilter()
        }
    }

    @Test
    fun `Likes tab maps feed entries to post cards`() {
        // getActorLikes returns the same FeedViewPost shape as getAuthorFeed;
        // the Likes projection reuses the Posts path (every entry -> a Post).
        val likesJson =
            """
            {
              "feed": [
                {
                  "post": {
                    "uri": "at://did:plc:carol/app.bsky.feed.post/liked1",
                    "cid": "cidL1",
                    "author": { "did": "did:plc:carol", "handle": "carol.bsky.social" },
                    "indexedAt": "2026-06-02T12:00:00Z",
                    "record": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "a post the viewer liked",
                      "createdAt": "2026-06-02T12:00:00Z"
                    }
                  }
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString(GetAuthorFeedResponse.serializer(), likesJson)
        val items = response.feed.toTabItems(ProfileTab.Likes, ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(1, items.size, "liked posts MUST render as post cards")
        assertTrue(items.all { it is TabItemUi.Post }, "Likes projects every entry to a TabItemUi.Post")
    }
}
