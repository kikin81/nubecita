package net.kikin.nubecita.feature.notifications.impl.data

import io.github.kikin81.atproto.app.bsky.notification.ListNotificationsNotification
import io.github.kikin81.atproto.app.bsky.notification.ListNotificationsResponse
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.feedmapping.toAuthorUi
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.data.models.NotificationReason
import net.kikin.nubecita.data.models.PostUi
import kotlin.time.Instant

/**
 * One paginated page of notifications. Carries the per-row UI projection
 * and the cursor for the next request. Internal to
 * `:feature:notifications:impl`; not exposed to any other module.
 */
internal data class NotificationsPage(
    val items: ImmutableList<NotificationItemUi>,
    val nextCursor: String?,
)

/**
 * Map a [ListNotificationsResponse] page into a [NotificationsPage] for the
 * UI. Subject-post hydration is supplied externally via [hydratedPosts] —
 * the repository batches a single `getPosts` call per page covering the
 * `reasonSubject` URIs (engagement reasons) and the notification `uri`
 * (content-bearing reasons). Missing URIs render with `subjectPost = null`
 * and the row still shows (per the deleted-subject scenario in the spec).
 *
 * Aggregation is **reason-conditional** per the design D3:
 *
 * - Engagement reasons (`like`, `like-via-repost`, `repost`,
 *   `repost-via-repost`) group by `(reason, reasonSubject)` into one
 *   [NotificationItemUi.Aggregated] row.
 * - `follow` (no `reasonSubject`) groups by `(reason, sameCalendarDay)`,
 *   where the day is the UTC `YYYY-MM-DD` prefix of `indexedAt`.
 * - Content-bearing reasons (`reply`, `quote`, `mention`,
 *   `subscribed-post`) and rare per-actor reasons (`starterpack-joined`,
 *   `verified`, `unverified`, `contact-match`, `Unknown`) always render
 *   as their own [NotificationItemUi.Single] — even when `reasonSubject`
 *   would collide — because the row's load-bearing content is the
 *   actor's new post (or verification record), not the shared subject.
 *
 * Output is sorted by `indexedAt` descending (newest first). Aggregation
 * runs per page; no cross-page merging in slice 1.
 */
internal fun ListNotificationsResponse.toNotificationsPage(
    hydratedPosts: Map<String, PostUi> = emptyMap(),
): NotificationsPage =
    NotificationsPage(
        items = notifications.toUiItems(hydratedPosts),
        nextCursor = cursor,
    )

private fun List<ListNotificationsNotification>.toUiItems(
    hydratedPosts: Map<String, PostUi>,
): ImmutableList<NotificationItemUi> {
    if (isEmpty()) return persistentListOf()

    val decoded = map { it.decode() }
    val out = ArrayList<NotificationItemUi>(decoded.size)
    val aggregatable = LinkedHashMap<AggregationKey, MutableList<DecodedNotification>>()

    for (n in decoded) {
        val key = n.aggregationKeyOrNull()
        if (key == null) {
            out += n.toSingle(hydratedPosts)
        } else {
            aggregatable.getOrPut(key) { mutableListOf() }.add(n)
        }
    }

    for ((_, group) in aggregatable) {
        out +=
            if (group.size == 1) {
                group.single().toSingle(hydratedPosts)
            } else {
                group.toAggregated(hydratedPosts)
            }
    }

    // Stable sort preserves wire-arrival order within ties of `indexedAt`.
    out.sortByDescending { it.indexedAt }
    return out.toImmutableList()
}

private data class AggregationKey(
    val reason: NotificationReason,
    /**
     * `reasonSubject.raw` for engagement reasons; UTC `YYYY-MM-DD` prefix
     * of `indexedAt.raw` for follow.
     */
    val groupingValue: String,
)

private data class DecodedNotification(
    val source: ListNotificationsNotification,
    val reason: NotificationReason,
    val actor: AuthorUi,
    val indexedAtInstant: Instant,
)

private fun ListNotificationsNotification.decode(): DecodedNotification =
    DecodedNotification(
        source = this,
        reason = NotificationReason.fromWireValue(reason),
        actor = author.toAuthorUi(),
        // Bypass the lexicon's `Datetime.toInstant()` helper because it
        // returns `kotlinx.datetime.Instant` while UI models standardize
        // on `kotlin.time.Instant` (mirrors PostUi.createdAt's mapping in
        // :core:feed-mapping). Server-emitted RFC 3339 timestamps parse
        // reliably; a malformed value bubbles up as IAE.
        indexedAtInstant = Instant.parse(indexedAt.raw),
    )

private fun DecodedNotification.aggregationKeyOrNull(): AggregationKey? =
    when (reason) {
        NotificationReason.Like,
        NotificationReason.LikeViaRepost,
        NotificationReason.Repost,
        NotificationReason.RepostViaRepost,
        -> source.reasonSubject?.raw?.let { AggregationKey(reason, it) }

        NotificationReason.Follow ->
            AggregationKey(reason, source.indexedAt.raw.take(DAY_PREFIX_LENGTH))

        // Content-bearing reasons carry per-actor unique content; never aggregate.
        NotificationReason.Reply,
        NotificationReason.Quote,
        NotificationReason.Mention,
        NotificationReason.SubscribedPost,
        // Rare per-actor reasons; aggregation would collapse semantically
        // distinct events.
        NotificationReason.StarterpackJoined,
        NotificationReason.Verified,
        NotificationReason.Unverified,
        NotificationReason.ContactMatch,
        NotificationReason.Unknown,
        -> null
    }

private fun DecodedNotification.toSingle(
    hydratedPosts: Map<String, PostUi>,
): NotificationItemUi.Single =
    NotificationItemUi.Single(
        itemKey = source.uri.raw,
        reason = reason,
        indexedAt = indexedAtInstant,
        isRead = source.isRead,
        actors = persistentListOf(actor),
        subjectPost = resolveSubjectPost(hydratedPosts),
    )

private fun List<DecodedNotification>.toAggregated(
    hydratedPosts: Map<String, PostUi>,
): NotificationItemUi.Aggregated {
    val sortedDesc = sortedByDescending { it.indexedAtInstant }
    val newest = sortedDesc.first()
    val itemKey =
        buildString {
            append("agg:")
            append(newest.reason.name)
            append(':')
            append(
                newest.source.reasonSubject?.raw
                    ?: newest.source.indexedAt.raw
                        .take(DAY_PREFIX_LENGTH),
            )
        }
    return NotificationItemUi.Aggregated(
        itemKey = itemKey,
        reason = newest.reason,
        indexedAt = newest.indexedAtInstant,
        // Aggregated row is read only when every contributor is read; a
        // single unread event in the cluster keeps the row unread.
        isRead = all { it.source.isRead },
        actors = sortedDesc.map { it.actor }.toImmutableList(),
        subjectPost = newest.resolveSubjectPost(hydratedPosts),
    )
}

private fun DecodedNotification.resolveSubjectPost(
    hydratedPosts: Map<String, PostUi>,
): PostUi? =
    when (reason) {
        // Engagement reasons: the row references the user's own post via
        // `reasonSubject`. Same subject across all aggregated actors.
        NotificationReason.Like,
        NotificationReason.LikeViaRepost,
        NotificationReason.Repost,
        NotificationReason.RepostViaRepost,
        NotificationReason.SubscribedPost,
        -> source.reasonSubject?.raw?.let(hydratedPosts::get)

        // Content-bearing reasons: the row references the actor's new
        // post via the notification `uri`. Distinct per event.
        NotificationReason.Reply,
        NotificationReason.Quote,
        NotificationReason.Mention,
        -> hydratedPosts[source.uri.raw]

        // No associated post for these reasons.
        NotificationReason.Follow,
        NotificationReason.StarterpackJoined,
        NotificationReason.Verified,
        NotificationReason.Unverified,
        NotificationReason.ContactMatch,
        NotificationReason.Unknown,
        -> null
    }

/** Length of the UTC `YYYY-MM-DD` prefix of an RFC 3339 datetime string. */
private const val DAY_PREFIX_LENGTH = 10
