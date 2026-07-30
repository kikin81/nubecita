package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.AdultContentPref
import io.github.kikin81.atproto.app.bsky.actor.FeedViewPref
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.SavedFeed
import io.github.kikin81.atproto.app.bsky.actor.SavedFeedsPrefV2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [mergeFeedViewPrefs] and the [ReplyVisibility] mapping.
 *
 * The merge is the highest-risk piece of `nubecita-1fmx.2`: a read-modify-write
 * that drops the wrong entry would silently destroy the viewer's saved feeds,
 * moderation settings, or another feed's own view preferences. These tests pin
 * that it replaces ONLY the `home` entry.
 */
internal class FeedViewPrefsMergeTest {
    private val otherFeed = "at://did:plc:x/app.bsky.feed.generator/whats-hot"

    // ---------- merge ----------

    @Test
    fun `replaces the home entry with the supplied prefs`() {
        val original: List<GetPreferencesResponsePreferencesUnion> =
            listOf(FeedViewPref(feed = "home", hideReposts = false))

        val merged =
            mergeFeedViewPrefs(
                original,
                FeedViewPrefs(hideReplies = true, hideRepliesByUnfollowed = true, hideReposts = true, hideQuotePosts = true),
            )

        val home = merged.filterIsInstance<FeedViewPref>().single { it.feed == "home" }
        assertTrue(home.hideReplies == true)
        assertTrue(home.hideRepliesByUnfollowed == true)
        assertTrue(home.hideReposts == true)
        assertTrue(home.hideQuotePosts == true)
    }

    @Test
    fun `writes all four fields explicitly so the stored set is deterministic`() {
        val merged = mergeFeedViewPrefs(emptyList(), FeedViewPrefs.DEFAULT)

        val home = merged.filterIsInstance<FeedViewPref>().single { it.feed == "home" }
        assertEquals(false, home.hideReplies)
        assertEquals(true, home.hideRepliesByUnfollowed)
        assertEquals(false, home.hideReposts)
        assertEquals(false, home.hideQuotePosts)
    }

    @Test
    fun `appends a home entry when the account has none`() {
        val original: List<GetPreferencesResponsePreferencesUnion> = listOf(AdultContentPref(enabled = true))

        val merged = mergeFeedViewPrefs(original, FeedViewPrefs.DEFAULT)

        assertEquals(1, merged.filterIsInstance<FeedViewPref>().count { it.feed == "home" })
    }

    /**
     * The destructive-bug guard. A merge that dropped every `FeedViewPref`, or
     * filtered by type alone rather than by `feed`, would wipe the viewer's
     * per-generator view preferences.
     */
    @Test
    fun `preserves feedViewPref entries belonging to other feeds`() {
        val original: List<GetPreferencesResponsePreferencesUnion> =
            listOf(
                FeedViewPref(feed = otherFeed, hideReposts = true, hideQuotePosts = true),
                FeedViewPref(feed = "home", hideReposts = false),
            )

        val merged = mergeFeedViewPrefs(original, FeedViewPrefs.DEFAULT)

        val other = merged.filterIsInstance<FeedViewPref>().single { it.feed == otherFeed }
        assertEquals(true, other.hideReposts)
        assertEquals(true, other.hideQuotePosts)
    }

    @Test
    fun `preserves foreign preference kinds`() {
        val savedFeeds =
            SavedFeedsPrefV2(
                items = listOf(SavedFeed(id = "a", type = "timeline", value = "following", pinned = true)),
            )
        val original: List<GetPreferencesResponsePreferencesUnion> =
            listOf(AdultContentPref(enabled = true), savedFeeds, FeedViewPref(feed = "home"))

        val merged = mergeFeedViewPrefs(original, FeedViewPrefs.DEFAULT)

        assertEquals(1, merged.filterIsInstance<AdultContentPref>().size)
        assertTrue(merged.filterIsInstance<AdultContentPref>().single().enabled)
        assertEquals(
            listOf("a"),
            merged
                .filterIsInstance<SavedFeedsPrefV2>()
                .single()
                .items
                .map(SavedFeed::id),
        )
    }

    @Test
    fun `a merged array round-trips back through the parser`() {
        val prefs = FeedViewPrefs(hideReplies = false, hideRepliesByUnfollowed = false, hideReposts = true, hideQuotePosts = false)

        // The PUT-side union members are the same concrete types as the GET side,
        // so a merged array can be re-parsed — which is what makes the optimistic
        // reconcile in the repository meaningful.
        val merged = mergeFeedViewPrefs(emptyList(), prefs)
        val reparsed = parseFeedViewPrefs(merged.filterIsInstance<GetPreferencesResponsePreferencesUnion>())

        assertEquals(prefs, reparsed)
    }

    // ---------- ReplyVisibility mapping ----------

    @Test
    fun `hideReplies wins over hideRepliesByUnfollowed when reading`() {
        val prefs = FeedViewPrefs(hideReplies = true, hideRepliesByUnfollowed = false)

        assertEquals(ReplyVisibility.NONE, prefs.replyVisibility)
    }

    @Test
    fun `hideRepliesByUnfollowed maps to FOLLOWED_ONLY`() {
        val prefs = FeedViewPrefs(hideReplies = false, hideRepliesByUnfollowed = true)

        assertEquals(ReplyVisibility.FOLLOWED_ONLY, prefs.replyVisibility)
    }

    @Test
    fun `neither flag set maps to ALL`() {
        val prefs = FeedViewPrefs(hideReplies = false, hideRepliesByUnfollowed = false)

        assertEquals(ReplyVisibility.ALL, prefs.replyVisibility)
    }

    @Test
    fun `the shipped default reads as FOLLOWED_ONLY`() {
        assertEquals(ReplyVisibility.FOLLOWED_ONLY, FeedViewPrefs.DEFAULT.replyVisibility)
    }

    /**
     * Starts from BOTH flags cleared, not from [FeedViewPrefs.DEFAULT]. DEFAULT
     * already carries `hideRepliesByUnfollowed = true`, so a mapping that forgot
     * to set that flag would still round-trip from DEFAULT and this test would
     * pass while the behaviour was wrong — verified by mutation.
     */
    @Test
    fun `every ReplyVisibility value round-trips from a cleared state`() {
        val cleared = FeedViewPrefs(hideReplies = false, hideRepliesByUnfollowed = false)

        ReplyVisibility.entries.forEach { visibility ->
            val applied = cleared.withReplyVisibility(visibility)
            assertEquals(visibility, applied.replyVisibility, "round-trip failed for $visibility")
        }
    }

    @Test
    fun `NONE sets both reply flags even when neither was set`() {
        val cleared = FeedViewPrefs(hideReplies = false, hideRepliesByUnfollowed = false)

        val applied = cleared.withReplyVisibility(ReplyVisibility.NONE)

        assertTrue(applied.hideReplies)
        // Redundant while hideReplies is on, but written so the value round-trips
        // and other clients read the account the same way.
        assertTrue(applied.hideRepliesByUnfollowed)
    }

    @Test
    fun `ALL clears both reply flags`() {
        val applied = FeedViewPrefs.DEFAULT.withReplyVisibility(ReplyVisibility.ALL)

        assertFalse(applied.hideReplies)
        assertFalse(applied.hideRepliesByUnfollowed)
    }

    @Test
    fun `changing reply visibility leaves the repost and quote flags alone`() {
        val prefs = FeedViewPrefs.DEFAULT.copy(hideReposts = true, hideQuotePosts = true)

        val applied = prefs.withReplyVisibility(ReplyVisibility.NONE)

        assertTrue(applied.hideReposts)
        assertTrue(applied.hideQuotePosts)
    }
}
