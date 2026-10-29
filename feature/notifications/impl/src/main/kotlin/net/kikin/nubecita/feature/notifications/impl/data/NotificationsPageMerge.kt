package net.kikin.nubecita.feature.notifications.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.NotificationItemUi

/**
 * Append the next page's rows after the already-loaded ones, keeping every
 * [NotificationItemUi.itemKey] unique — the list renders through a keyed
 * `LazyColumn`, which throws on a repeated key.
 *
 * Aggregation in [toNotificationsPage] runs per page, so a group that
 * straddles a page boundary (a day's follows, a post's likes) arrives as two
 * rows sharing the same `agg:<reason>:<subject|day>` key. A same-key row is
 * folded into the row already on screen instead of appended:
 *
 * - Either side aggregated → one [NotificationItemUi.Aggregated] in the
 *   existing slot. The newer page's actors stay first, the newest
 *   `indexedAt` wins, and one unread contributor keeps the row unread
 *   (mirroring the in-page aggregation rule).
 * - Both [NotificationItemUi.Single] → the same notification served twice
 *   by an overlapping page; the already-rendered row is kept.
 */
internal fun ImmutableList<NotificationItemUi>.appendPage(page: List<NotificationItemUi>): ImmutableList<NotificationItemUi> {
    if (page.isEmpty()) return this
    val merged = ArrayList<NotificationItemUi>(size + page.size)
    val indexByKey = HashMap<String, Int>(size + page.size)
    for (item in this + page) {
        val existingIndex = indexByKey[item.itemKey]
        if (existingIndex == null) {
            indexByKey[item.itemKey] = merged.size
            merged += item
        } else {
            merged[existingIndex] = merged[existingIndex].absorb(item)
        }
    }
    return merged.toImmutableList()
}

private fun NotificationItemUi.absorb(other: NotificationItemUi): NotificationItemUi =
    if (this is NotificationItemUi.Single && other is NotificationItemUi.Single) {
        this
    } else {
        NotificationItemUi.Aggregated(
            itemKey = itemKey,
            reason = reason,
            indexedAt = maxOf(indexedAt, other.indexedAt),
            isRead = isRead && other.isRead,
            actors = (actors + other.actors).toImmutableList(),
            subjectPost = subjectPost ?: other.subjectPost,
        )
    }
