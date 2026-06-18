package net.kikin.nubecita.feature.notifications.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetPostsRequest
import io.github.kikin81.atproto.app.bsky.notification.GetUnreadCountRequest
import io.github.kikin81.atproto.app.bsky.notification.ListNotificationsNotification
import io.github.kikin81.atproto.app.bsky.notification.ListNotificationsRequest
import io.github.kikin81.atproto.app.bsky.notification.NotificationService
import io.github.kikin81.atproto.app.bsky.notification.UpdateSeenRequest
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.XrpcClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.data.models.NotificationReason
import net.kikin.nubecita.data.models.PostUi
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Instant

internal class DefaultNotificationsRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : NotificationsRepository {
        override suspend fun fetchPage(
            filter: NotificationFilter,
            cursor: String?,
        ): Result<NotificationsPage> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        NotificationService(client).listNotifications(
                            ListNotificationsRequest(
                                limit = NOTIFICATIONS_PAGE_LIMIT.toLong(),
                                cursor = cursor,
                                reasons = filter.reasons,
                            ),
                        )
                    val urisToHydrate = response.notifications.collectHydrationUris()
                    val hydratedPosts =
                        if (urisToHydrate.isEmpty()) {
                            emptyMap()
                        } else {
                            hydratePosts(client, urisToHydrate)
                        }
                    response.toNotificationsPage(hydratedPosts)
                }.onFailure { logFailure("fetchPage", it) }
            }

        override suspend fun markSeen(seenAt: Instant): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    NotificationService(client).updateSeen(
                        // kotlin.time.Instant.toString() emits an ISO-8601 string
                        // with a trailing `Z` for UTC — RFC 3339 compliant, which
                        // is what the lexicon's Datetime requires. No timezone
                        // gymnastics needed.
                        UpdateSeenRequest(seenAt = Datetime(seenAt.toString())),
                    )
                }.onFailure { logFailure("markSeen", it) }
            }

        override suspend fun unreadCount(): Result<Int> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        NotificationService(client).getUnreadCount(
                            // Server uses defaults (no `priority` filter, no
                            // `seenAt` override). Slice 1 doesn't expose the
                            // priority toggle — that's a follow-up bd issue
                            // per design D7's "priority defer" decision.
                            GetUnreadCountRequest(),
                        )
                    // Lexicon's `count` is Long; UI carries Int (badge count is
                    // small in practice — even pathological cases stay under
                    // 100_000). Saturating cast guards against the unlikely
                    // edge where Long overflows Int.
                    response.count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                }.onFailure { logFailure("unreadCount", it) }
            }

        private suspend fun hydratePosts(
            client: XrpcClient,
            uris: List<String>,
        ): Map<String, PostUi> {
            val feedService = FeedService(client)
            val map = LinkedHashMap<String, PostUi>(uris.size)
            // Sequential batches: the lexicon caps `getPosts` at 25 URIs and a
            // 50-row page typically produces at most two batches. Parallelizing
            // would shave ~100ms in the worst case while adding async/awaitAll
            // ceremony; not worth it for slice 1.
            for (batch in uris.chunked(GET_POSTS_BATCH_LIMIT)) {
                val response =
                    feedService.getPosts(
                        GetPostsRequest(uris = batch.map { AtUri(it) }),
                    )
                for (postView in response.posts) {
                    val ui = postView.toPostUiCore() ?: continue
                    map[postView.uri.raw] = ui
                }
            }
            return map
        }

        private fun logFailure(
            operation: String,
            throwable: Throwable,
        ) {
            // javaClass.name (not ::class.qualifiedName) so anonymous / local
            // classes still produce a non-null identifier — mirrors
            // DefaultFeedRepository's diagnostic pattern.
            Timber.tag(TAG).w(
                throwable,
                "%s failed: %s",
                operation,
                throwable.javaClass.name,
            )
        }

        private companion object {
            const val TAG = "NotificationsRepository"
        }
    }

/**
 * Collect the set of post URIs that need `getPosts` hydration for this page.
 * Engagement reasons (like / repost / like-via-repost / repost-via-repost)
 * use `reasonSubject` (the user's own post — same across aggregated actors).
 * Content-bearing reasons (reply / quote / mention / subscribed-post) use
 * the notification `uri` (the actor's new post). All other reasons need no
 * hydration. Order is preserved (LinkedHashSet) so chunking into batches is
 * deterministic — same input page produces identical batch boundaries on
 * every call, which is what the chunking unit test asserts on.
 */
private fun List<ListNotificationsNotification>.collectHydrationUris(): List<String> {
    val unique = LinkedHashSet<String>()
    for (notification in this) {
        notification.hydrationUriOrNull()?.let(unique::add)
    }
    return unique.toList()
}

private fun ListNotificationsNotification.hydrationUriOrNull(): String? =
    when (NotificationReason.fromWireValue(reason)) {
        NotificationReason.Like,
        NotificationReason.LikeViaRepost,
        NotificationReason.Repost,
        NotificationReason.RepostViaRepost,
        -> reasonSubject?.raw

        NotificationReason.Reply,
        NotificationReason.Quote,
        NotificationReason.Mention,
        NotificationReason.SubscribedPost,
        -> uri.raw

        NotificationReason.Follow,
        NotificationReason.StarterpackJoined,
        NotificationReason.Verified,
        NotificationReason.Unverified,
        NotificationReason.ContactMatch,
        NotificationReason.Unknown,
        -> null
    }
