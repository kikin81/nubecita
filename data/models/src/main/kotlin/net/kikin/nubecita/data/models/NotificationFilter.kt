package net.kikin.nubecita.data.models

/**
 * Reason-grouped filter applied to the notifications surface's filter chip
 * row. Each value maps to a `reasons[]` array sent on
 * `app.bsky.notification.listNotifications`.
 *
 * The chip set is intentionally collapsed from the lexicon's 13 known
 * reasons into 5 mobile-friendly groups (see
 * `openspec/changes/add-feature-notifications/design.md` decision D7). The
 * collapse means:
 *
 * - [Mentions] is the "someone is talking to you" bucket (mention + reply + quote).
 * - [Reposts] coalesces direct and via-repost variants.
 * - [Likes] coalesces direct and via-repost variants.
 * - Rare reasons (`starterpack-joined`, `verified`, `unverified`,
 *   `subscribed-post`, `contact-match`) only surface under [All] —
 *   intentionally not chip-addressable.
 *
 * [reasons] is `null` for [All] (the lexicon `reasons` parameter is omitted
 * — the server returns everything).
 */
public enum class NotificationFilter(
    public val reasons: List<String>?,
) {
    All(reasons = null),
    Mentions(reasons = listOf("mention", "reply", "quote")),
    Reposts(reasons = listOf("repost", "repost-via-repost")),
    Follows(reasons = listOf("follow")),
    Likes(reasons = listOf("like", "like-via-repost")),
}
