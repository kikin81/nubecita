package net.kikin.nubecita.feature.notifications.impl.data

import net.kikin.nubecita.data.models.NotificationFilter
import kotlin.time.Instant

/**
 * Fetch surface for `app.bsky.notification.*` scoped to
 * `:feature:notifications:impl`. The interface is package-internal: no
 * other module imports it. If a second consumer (a future widget or a
 * background sync job) needs the same fetch, the change that adds the
 * consumer also promotes this interface to a new `:core:notifications`
 * module.
 *
 * All three operations return Kotlin stdlib [Result] — the convention
 * established by [net.kikin.nubecita.feature.feed.impl.data.FeedRepository].
 * Errors propagate as `Result.failure(throwable)` for the caller to
 * inspect and map to UI state.
 */
internal interface NotificationsRepository {
    /**
     * Fetch one page of notifications filtered by [filter], starting at
     * [cursor] (null for the first page). The repository also hydrates
     * subject posts via a batched `app.bsky.feed.getPosts` so the
     * mapper can attach `PostUi` previews per the
     * `feature-notifications` capability spec.
     */
    suspend fun fetchPage(
        filter: NotificationFilter,
        cursor: String?,
    ): Result<NotificationsPage>

    /**
     * Mark all notifications seen up to [seenAt] via
     * `app.bsky.notification.updateSeen`. Server uses the sentinel to
     * compute `getUnreadCount`'s response on the next poll.
     */
    suspend fun markSeen(seenAt: Instant): Result<Unit>

    /**
     * Return the server-reported unread count via
     * `app.bsky.notification.getUnreadCount`. The badge polling layer
     * (a `ProcessLifecycleOwner`-scoped observer landing in bd issue
     * nubecita-1fy.1.7) calls this on a 60-second cadence while the
     * app is foregrounded.
     */
    suspend fun unreadCount(): Result<Int>
}

/**
 * Page size for `listNotifications` requests. The lexicon caps at 100;
 * 50 matches Bluesky's official client and keeps the per-page memory
 * footprint modest while reducing the chance of two `getPosts` batches
 * per page (the cap is 25 URIs per batch).
 */
internal const val NOTIFICATIONS_PAGE_LIMIT: Int = 50

/**
 * Maximum URIs per `app.bsky.feed.getPosts` call. The lexicon caps at
 * 25; the repository chunks the per-page hydration set when it exceeds
 * this number.
 */
internal const val GET_POSTS_BATCH_LIMIT: Int = 25
