package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.FeedViewPref
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion

/**
 * The viewer's `app.bsky.actor.defs#feedViewPref` settings for a feed, as a
 * flat domain model.
 *
 * Defaults deliberately mirror `FEED_VIEW_PREF_DEFAULTS` in the official
 * `@atproto/api` package (`packages/api/src/agent.ts`) — in particular
 * [hideRepliesByUnfollowed] defaults to **`true`**. An account that has never
 * touched the setting still expects stranger-to-stranger replies to be hidden
 * from its Following feed, because that is what every other AT Protocol client
 * does. Defaulting this to `false` would reproduce the bug reported in
 * `nubecita-1fmx` for every user with no stored preference.
 */
data class FeedViewPrefs(
    /** Hide ALL replies, regardless of who wrote them. */
    val hideReplies: Boolean = false,
    /**
     * Hide replies unless the viewer follows (or is) the author AND the thread
     * being replied into involves someone the viewer follows (or is).
     *
     * Note this is stricter than the lexicon's one-line description — see
     * `shouldDisplayInFollowingFeed` for the exact predicate.
     */
    val hideRepliesByUnfollowed: Boolean = true,
    /** Hide reposted entries. */
    val hideReposts: Boolean = false,
    /** Hide posts that quote another post. */
    val hideQuotePosts: Boolean = false,
) {
    companion object {
        /**
         * Fail-safe defaults, matching the official client. Read by any
         * consumer that observes before the first preference fetch completes.
         */
        val DEFAULT = FeedViewPrefs()

        /**
         * The `feed` key under which the Following timeline's preference is
         * stored. Matches `@atproto/api`, whose `getPreferences()` exposes a
         * `Record<feed, FeedViewPreference>` that the official client reads as
         * `feedViewPrefs.home`.
         */
        const val HOME_FEED_KEY = "home"
    }
}

/**
 * Pure projection of the typed `preferences` list into [FeedViewPrefs].
 *
 * Only the entry keyed [FeedViewPrefs.HOME_FEED_KEY] configures the Following
 * feed; per-feed entries for generators are ignored so a stray custom-feed
 * preference cannot silently reconfigure the home timeline. Last entry wins if
 * the server returns duplicates, mirroring `parseModerationPrefs`. Fields the
 * server omits fall back to [FeedViewPrefs.DEFAULT] — notably
 * `hideRepliesByUnfollowed`, which defaults to **`true`**. No I/O.
 */
fun parseFeedViewPrefs(preferences: List<GetPreferencesResponsePreferencesUnion>): FeedViewPrefs {
    val home =
        preferences
            .filterIsInstance<FeedViewPref>()
            .lastOrNull { it.feed == FeedViewPrefs.HOME_FEED_KEY }
            ?: return FeedViewPrefs.DEFAULT

    return FeedViewPrefs(
        hideReplies = home.hideReplies ?: FeedViewPrefs.DEFAULT.hideReplies,
        hideRepliesByUnfollowed = home.hideRepliesByUnfollowed ?: FeedViewPrefs.DEFAULT.hideRepliesByUnfollowed,
        hideReposts = home.hideReposts ?: FeedViewPrefs.DEFAULT.hideReposts,
        hideQuotePosts = home.hideQuotePosts ?: FeedViewPrefs.DEFAULT.hideQuotePosts,
    )
}
