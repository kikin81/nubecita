package net.kikin.nubecita.data.models

/**
 * Why a notification was emitted. Mirrors the `reason` field on
 * `app.bsky.notification.listNotifications#notification`.
 *
 * The lexicon's `reason` is a `string` with documented `knownValues` —
 * the AppView may grow new reasons over time and a client that hard-fails
 * on an unrecognized value bricks itself. [Unknown] is the forward-compat
 * fallback so a new reason value at the AppView still renders (with a
 * generic icon and no deep-link).
 *
 * Translate wire strings via [fromWireValue]; do not pattern-match on raw
 * strings outside the mapper.
 */
public enum class NotificationReason {
    Like,
    Repost,
    Follow,
    Mention,
    Reply,
    Quote,
    StarterpackJoined,
    Verified,
    Unverified,
    LikeViaRepost,
    RepostViaRepost,
    SubscribedPost,
    ContactMatch,

    /**
     * Reason values not yet recognized by this client. Rows with this
     * reason render with a generic fallback icon and emit no deep-link
     * effect on tap. See `:feature:notifications:impl/NotificationsMapper`
     * for the wire-to-enum translation site.
     */
    Unknown,
    ;

    public companion object {
        /**
         * Translate a lexicon `reason` string to its [NotificationReason] enum value.
         * Unrecognized values map to [Unknown] for forward compatibility.
         */
        public fun fromWireValue(value: String): NotificationReason =
            when (value) {
                "like" -> Like
                "repost" -> Repost
                "follow" -> Follow
                "mention" -> Mention
                "reply" -> Reply
                "quote" -> Quote
                "starterpack-joined" -> StarterpackJoined
                "verified" -> Verified
                "unverified" -> Unverified
                "like-via-repost" -> LikeViaRepost
                "repost-via-repost" -> RepostViaRepost
                "subscribed-post" -> SubscribedPost
                "contact-match" -> ContactMatch
                else -> Unknown
            }
    }
}
