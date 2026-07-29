package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.AdultContentPref
import io.github.kikin81.atproto.app.bsky.actor.FeedViewPref
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [parseFeedViewPrefs] — the pure projection of the typed
 * `app.bsky.actor.getPreferences` array into [FeedViewPrefs].
 *
 * `feedViewPref` entries are keyed by their `feed` field. The Following
 * timeline's entry is keyed `"home"`, matching `@atproto/api`, whose
 * `getPreferences()` builds a `Record<feed, FeedViewPreference>` that
 * the official client reads as `feedViewPrefs.home`.
 */
internal class FeedViewPrefsParsingTest {
    @Test
    fun `an empty preferences array yields the defaults`() {
        assertEquals(FeedViewPrefs.DEFAULT, parseFeedViewPrefs(emptyList()))
    }

    /**
     * The single most important assertion in this file: an account that has
     * never touched the setting must still hide stranger-to-stranger replies,
     * because that is what every other client does by default. Flipping this
     * to `false` reintroduces the bug reported in nubecita-1fmx.
     */
    @Test
    fun `hideRepliesByUnfollowed defaults to true when no preference exists`() {
        assertTrue(parseFeedViewPrefs(emptyList()).hideRepliesByUnfollowed)
    }

    @Test
    fun `reads the home feed entry`() {
        val prefs =
            parseFeedViewPrefs(
                listOf(
                    FeedViewPref(
                        feed = "home",
                        hideReplies = true,
                        hideRepliesByUnfollowed = false,
                        hideReposts = true,
                        hideQuotePosts = true,
                    ),
                ),
            )

        assertTrue(prefs.hideReplies)
        assertFalse(prefs.hideRepliesByUnfollowed)
        assertTrue(prefs.hideReposts)
        assertTrue(prefs.hideQuotePosts)
    }

    @Test
    fun `absent fields on the home entry fall back to defaults`() {
        val prefs = parseFeedViewPrefs(listOf(FeedViewPref(feed = "home")))

        assertEquals(FeedViewPrefs.DEFAULT, prefs)
    }

    /**
     * A per-feed entry for some custom feed must not leak into the Following
     * feed's settings — otherwise a stray generator preference would silently
     * reconfigure the user's home timeline.
     */
    @Test
    fun `an entry for a different feed is ignored`() {
        val prefs =
            parseFeedViewPrefs(
                listOf(
                    FeedViewPref(
                        feed = "at://did:plc:x/app.bsky.feed.generator/whats-hot",
                        hideRepliesByUnfollowed = false,
                        hideReposts = true,
                    ),
                ),
            )

        assertEquals(FeedViewPrefs.DEFAULT, prefs)
    }

    @Test
    fun `foreign preference kinds are ignored`() {
        val preferences: List<GetPreferencesResponsePreferencesUnion> =
            listOf(
                AdultContentPref(enabled = true),
                FeedViewPref(feed = "home", hideReposts = true),
            )

        val prefs = parseFeedViewPrefs(preferences)

        assertTrue(prefs.hideReposts)
        assertTrue(prefs.hideRepliesByUnfollowed)
    }

    @Test
    fun `the last home entry wins when the server returns duplicates`() {
        val prefs =
            parseFeedViewPrefs(
                listOf(
                    FeedViewPref(feed = "home", hideReposts = true),
                    FeedViewPref(feed = "home", hideReposts = false),
                ),
            )

        assertFalse(prefs.hideReposts)
    }
}
