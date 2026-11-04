package net.kikin.nubecita.core.feedcache

/**
 * The kind of feed a cached partition holds. Persisted as its enum NAME in the
 * `feed_post` / `feed_remote_keys` tables' `feed_type` column (the entity layer
 * stores a `String`; this enum lives here so `:core:database` stays free of any
 * `:core:feed-cache` dependency).
 *
 * - [FOLLOWING] — the home timeline (`getTimeline`); has no feed URI, so its
 *   partition is keyed with the empty-string sentinel.
 * - [DISCOVER] — the default algorithmic discover feed (`getFeed` against a
 *   generator AT-URI). Modeled distinctly from [CUSTOM] so the widget / refresh
 *   scheduler can treat the always-present discover feed specially.
 * - [CUSTOM] — any user-pinned custom feed generator (`getFeed`).
 * - [LIST] — a user list rendered as a feed (`getListFeed`). Modeled now even
 *   though no surface consumes it yet — zero-cost (the wire call already exists)
 *   and it avoids a later schema/enum migration.
 */
enum class FeedType {
    FOLLOWING,
    DISCOVER,
    CUSTOM,
    LIST,
    ;

    /**
     * Whether the viewer's feed-view preferences — the reply, repost and quote
     * tuners — apply to this feed.
     *
     * True for the feeds the viewer's own follow graph composes. A generator
     * curates its own output, so applying reply tuners to it would strip out
     * most of what the algorithm deliberately selected; the official client
     * applies these to `following` and `list…` descriptors only, never to
     * `feedgen…`.
     *
     * Written as an exhaustive `when` rather than a property on each constant
     * so that adding a feed type is a compile error here. Guessing wrong in
     * either direction silently changes what a feed shows.
     */
    val isFollowScoped: Boolean
        get() =
            when (this) {
                FOLLOWING, LIST -> true
                DISCOVER, CUSTOM -> false
            }
}

/**
 * Identity of one cached feed partition: the signed-in account, the feed kind,
 * and the feed's AT-URI. Rows in `feed_post` / `feed_remote_keys` are
 * partitioned by exactly this triple.
 *
 * [feedUri] holds the generator/list AT-URI for [FeedType.DISCOVER] /
 * [FeedType.CUSTOM] / [FeedType.LIST]; for [FeedType.FOLLOWING] it is the
 * **empty-string sentinel** (`""`) because the home timeline has no URI.
 */
data class FeedKey(
    val accountDid: String,
    val feedType: FeedType,
    val feedUri: String,
) {
    companion object {
        /**
         * Convenience constructor for the home timeline partition, which uses
         * the empty-string [feedUri] sentinel.
         */
        fun following(accountDid: String): FeedKey = FeedKey(accountDid, FeedType.FOLLOWING, "")
    }
}
