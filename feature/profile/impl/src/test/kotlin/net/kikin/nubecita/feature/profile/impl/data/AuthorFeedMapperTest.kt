package net.kikin.nubecita.feature.profile.impl.data

import net.kikin.nubecita.feature.profile.impl.ProfileTab
import org.junit.jupiter.api.Assertions.assertEquals
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
        ProfileTab.entries.forEach { tab ->
            assertTrue(
                tab.toAuthorFeedFilter() in legal,
                "ProfileTab.$tab MUST map to one of the lexicon filter values; got ${tab.toAuthorFeedFilter()}",
            )
        }
    }
}
