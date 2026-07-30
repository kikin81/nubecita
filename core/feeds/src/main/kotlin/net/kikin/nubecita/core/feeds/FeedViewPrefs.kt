package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.FeedViewPref
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequestPreferencesUnion

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

/**
 * How replies appear in a follow-scoped feed, as a single mutually-exclusive
 * choice.
 *
 * The wire format carries two independent booleans — `hideReplies` and
 * `hideRepliesByUnfollowed` — but they are not independent in behaviour: the
 * official client branches on them (`hideReplies ? removeReplies :
 * followedRepliesOnly`), so `hideReplies` wins and the pair
 * `(hideReplies = true, hideRepliesByUnfollowed = …)` has only one meaning.
 * Collapsing them into one enum for the UI makes the meaningless combination
 * unrepresentable, per CLAUDE.md's rule for mutually-exclusive modes.
 *
 * [FeedViewPrefs] itself stays wire-faithful — the reply filter reads the
 * booleans directly and is untouched by this projection.
 */
enum class ReplyVisibility {
    /** Show every reply the feed returns. */
    ALL,

    /** Only replies involving the viewer or accounts they follow. */
    FOLLOWED_ONLY,

    /** No replies at all. */
    NONE,
}

/**
 * Reads the reply preference as a single choice. `hideReplies` takes precedence,
 * matching the official client's branch order, so an account carrying an odd
 * combination still resolves deterministically.
 */
val FeedViewPrefs.replyVisibility: ReplyVisibility
    get() =
        when {
            hideReplies -> ReplyVisibility.NONE
            hideRepliesByUnfollowed -> ReplyVisibility.FOLLOWED_ONLY
            else -> ReplyVisibility.ALL
        }

/**
 * Applies a reply choice, leaving [FeedViewPrefs.hideReposts] and
 * [FeedViewPrefs.hideQuotePosts] untouched.
 *
 * [ReplyVisibility.NONE] deliberately sets BOTH flags. `hideRepliesByUnfollowed`
 * is redundant while `hideReplies` is on, but writing it keeps the value
 * round-tripping through [replyVisibility] and leaves the account in the state
 * another client would also read as "hide replies".
 */
fun FeedViewPrefs.withReplyVisibility(visibility: ReplyVisibility): FeedViewPrefs =
    when (visibility) {
        ReplyVisibility.ALL -> copy(hideReplies = false, hideRepliesByUnfollowed = false)
        ReplyVisibility.FOLLOWED_ONLY -> copy(hideReplies = false, hideRepliesByUnfollowed = true)
        ReplyVisibility.NONE -> copy(hideReplies = true, hideRepliesByUnfollowed = true)
    }

/**
 * Pure merge: produce a new `preferences` array that replaces ONLY the
 * [FeedViewPrefs.HOME_FEED_KEY] entry while preserving everything else in place.
 *
 * The `feed`-keyed filter is load-bearing. Dropping every [FeedViewPref] — or
 * filtering by type alone — would destroy the viewer's per-generator view
 * preferences. Foreign preference kinds (saved feeds, moderation, and unmodelled
 * future kinds carried as the union's `Unknown` member) pass through verbatim via
 * [asPutPreference]. All four owned fields are written explicitly so the stored
 * entry is deterministic. No I/O.
 */
fun mergeFeedViewPrefs(
    original: List<GetPreferencesResponsePreferencesUnion>,
    prefs: FeedViewPrefs,
): List<PutPreferencesRequestPreferencesUnion> {
    val preserved =
        original
            .filterNot { member -> member is FeedViewPref && member.feed == FeedViewPrefs.HOME_FEED_KEY }
            .map { it.asPutPreference() }

    val owned =
        FeedViewPref(
            feed = FeedViewPrefs.HOME_FEED_KEY,
            hideReplies = prefs.hideReplies,
            hideRepliesByUnfollowed = prefs.hideRepliesByUnfollowed,
            hideReposts = prefs.hideReposts,
            hideQuotePosts = prefs.hideQuotePosts,
        )

    return preserved + owned
}
