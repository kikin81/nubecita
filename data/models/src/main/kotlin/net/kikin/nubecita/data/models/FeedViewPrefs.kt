package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

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
@Immutable
public data class FeedViewPrefs(
    /** Hide ALL replies, regardless of who wrote them. */
    val hideReplies: Boolean = false,
    /**
     * Hide replies unless the viewer follows (or is) the author AND the thread
     * being replied into involves someone the viewer follows (or is).
     *
     * Note this is stricter than the lexicon's one-line description — see
     * `shouldDisplayInFollowingFeed` in `:core:feed-mapping` for the exact
     * predicate.
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
