package net.kikin.nubecita.feature.notifications.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.data.models.NotificationReason
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Bench-flavor stand-in for [NotificationsRepository]. Returns a fixed,
 * marketing-quality set of notifications constructed in-process — no XRPC
 * call, no asset I/O — so the Notifications screen renders deterministic
 * content under the `bench` flavor (and the macrobench / screenshot
 * journeys see identical pixels on every run).
 *
 * Named with the `Bench` prefix to disambiguate from the androidTest
 * `FakeNotificationsRepository` in
 * `feature/notifications/impl/src/androidTest/.../testing/FakeNotificationsRepository.kt`
 * — they live in different packages (`data` vs `testing`) and source sets.
 *
 * Threading:
 *
 * - [fetchPage] hops onto [IoDispatcher] before assembling the page —
 *   the production [DefaultNotificationsRepository] does the same with the
 *   same qualifier. The fixtures are pre-built constants so the body is
 *   cheap, but the hop keeps the bench path's threading shape identical to
 *   production and off the `viewModelScope` (Main.immediate) coroutine.
 *
 * Filtering:
 *
 * - [fetchPage] honors [NotificationFilter] by keeping only the rows whose
 *   reason matches the filter's `reasons` wire values (null `reasons` ==
 *   `All` == keep everything). This mirrors the production impl, which sends
 *   `reasons[]` to `listNotifications` and lets the server filter — here the
 *   filter runs locally over the in-memory fixture so the chip row visibly
 *   narrows the list in the bench journey.
 *
 * Pagination:
 *
 * - The full fixture set is returned in one page (`nextCursor = null`), so
 *   `NotificationsViewModel.loadMore` short-circuits before issuing a
 *   second-page call. The `cursor` parameter is accepted (to satisfy the
 *   [NotificationsRepository] interface) but ignored.
 *
 * Unread count:
 *
 * - [unreadCount] reports the number of unread single/aggregated rows in the
 *   unfiltered fixture so the tab badge renders a stable, non-zero value.
 * - [markSeen] is a no-op `Result.success` — the fixture's read flags stay
 *   fixed for the lifetime of the process so repeated bench runs are
 *   deterministic.
 */
@Singleton
internal class BenchFakeNotificationsRepository
    @Inject
    constructor(
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : NotificationsRepository {
        override suspend fun fetchPage(
            filter: NotificationFilter,
            cursor: String?,
        ): Result<NotificationsPage> =
            withContext(dispatcher) {
                val reasons = filter.reasons
                val items =
                    NOTIFICATIONS
                        .filter { reasons == null || reasonWireValue(it.reason) in reasons }
                        .toImmutableList()
                Result.success(NotificationsPage(items = items, nextCursor = null))
            }

        // No-op: the bench fixture's read flags are fixed so repeated runs stay
        // deterministic. Returns success so NotificationsViewModel's mark-seen
        // path completes without surfacing an error effect.
        override suspend fun markSeen(seenAt: Instant): Result<Unit> = Result.success(Unit)

        override suspend fun unreadCount(): Result<Int> = Result.success(UNREAD_COUNT)

        private companion object {
            /**
             * Map a [NotificationReason] back to its lexicon wire string so the
             * fixture can be matched against [NotificationFilter.reasons] (which
             * carries wire values). Mirrors the forward mapping in
             * `NotificationReason.fromWireValue`.
             */
            private fun reasonWireValue(reason: NotificationReason): String =
                when (reason) {
                    NotificationReason.Like -> "like"
                    NotificationReason.Repost -> "repost"
                    NotificationReason.Follow -> "follow"
                    NotificationReason.Mention -> "mention"
                    NotificationReason.Reply -> "reply"
                    NotificationReason.Quote -> "quote"
                    NotificationReason.StarterpackJoined -> "starterpack-joined"
                    NotificationReason.Verified -> "verified"
                    NotificationReason.Unverified -> "unverified"
                    NotificationReason.LikeViaRepost -> "like-via-repost"
                    NotificationReason.RepostViaRepost -> "repost-via-repost"
                    NotificationReason.SubscribedPost -> "subscribed-post"
                    NotificationReason.ContactMatch -> "contact-match"
                    NotificationReason.Unknown -> "unknown"
                }

            private fun avatar(name: String): String = "file:///android_asset/img/avatars/$name.jpg"

            private fun author(
                did: String,
                handle: String,
                displayName: String,
                avatarName: String,
            ): AuthorUi =
                AuthorUi(
                    did = did,
                    handle = handle,
                    displayName = displayName,
                    avatarUrl = avatar(avatarName),
                )

            private val ALICE =
                author("did:plc:bench00alice", "alice.bsky.social", "Alice Rivera", "alice")
            private val BOB =
                author("did:plc:bench00bob000", "bob.dev", "Bob Tanaka", "bob")
            private val CARMEN =
                author("did:plc:bench0carmen0", "carmen.art", "Carmen Ortiz", "carmen")
            private val DIEGO =
                author("did:plc:bench00diego0", "diego.bsky.social", "Diego Fuentes", "diego")
            private val ELENA =
                author("did:plc:bench00elena0", "elena.photos", "Elena Voss", "elena")
            private val FEDE =
                author("did:plc:bench000fede0", "fede.bsky.social", "Fede Marín", "fede")
            private val GABE =
                author("did:plc:bench000gabe0", "gabe.codes", "Gabe Nakamura", "gabe")
            private val HUGO =
                author("did:plc:bench000hugo0", "hugo.bsky.social", "Hugo Bauer", "hugo")

            /** The viewer's own account — author of the posts being engaged with. */
            private val VIEWER =
                author("did:plc:bench00viewer", "you.bsky.social", "You", "alice")

            /**
             * Build a subject post owned by [VIEWER] (the user's own post that
             * a like/repost/reply references). Engagement rows attach this; the
             * text varies so each card reads distinctly.
             */
            private fun subjectPost(
                id: String,
                text: String,
                indexedAt: Instant,
                stats: PostStatsUi = PostStatsUi(),
            ): PostUi =
                PostUi(
                    id = "at://did:plc:bench00viewer/app.bsky.feed.post/$id",
                    cid = "bafyreibench$id",
                    author = VIEWER,
                    createdAt = indexedAt,
                    text = text,
                    facets = persistentListOf(),
                    embed = EmbedUi.Empty,
                    stats = stats,
                    viewer = ViewerStateUi(),
                    repostedBy = null,
                )

            /**
             * Build a content post authored by [actor] (the actor's new post
             * that a reply/quote/mention row references).
             */
            private fun actorPost(
                id: String,
                actor: AuthorUi,
                text: String,
                indexedAt: Instant,
            ): PostUi =
                PostUi(
                    id = "at://${actor.did}/app.bsky.feed.post/$id",
                    cid = "bafyreibench$id",
                    author = actor,
                    createdAt = indexedAt,
                    text = text,
                    facets = persistentListOf(),
                    embed = EmbedUi.Empty,
                    stats = PostStatsUi(),
                    viewer = ViewerStateUi(),
                    repostedBy = null,
                )

            private fun at(value: String): Instant = Instant.parse(value)

            /**
             * Deterministic notification set, newest first. Spans every variety
             * the UI supports: aggregated likes, aggregated follows, single
             * reply / mention / quote / repost / follow, plus the rarer
             * starterpack-joined and verified rows that only surface under the
             * `All` filter.
             */
            private val NOTIFICATIONS: List<NotificationItemUi> =
                listOf(
                    NotificationItemUi.Aggregated(
                        itemKey = "agg:Like:welcome",
                        reason = NotificationReason.Like,
                        indexedAt = at("2026-05-30T14:32:00Z"),
                        isRead = false,
                        actors = persistentListOf(ALICE, CARMEN, DIEGO, ELENA),
                        subjectPost =
                            subjectPost(
                                id = "welcome",
                                text = "Shipped the new feed. 120hz scroll the whole way down — feels unreal.",
                                indexedAt = at("2026-05-30T09:00:00Z"),
                                stats = PostStatsUi(likeCount = 42, repostCount = 6, replyCount = 4),
                            ),
                    ),
                    NotificationItemUi.Single(
                        itemKey = "reply:bob",
                        reason = NotificationReason.Reply,
                        indexedAt = at("2026-05-30T13:10:00Z"),
                        isRead = false,
                        actors = persistentListOf(BOB),
                        subjectPost =
                            actorPost(
                                id = "reply-bob",
                                actor = BOB,
                                text = "This is gorgeous. How did you keep the jank off during fling?",
                                indexedAt = at("2026-05-30T13:10:00Z"),
                            ),
                    ),
                    NotificationItemUi.Single(
                        itemKey = "mention:carmen",
                        reason = NotificationReason.Mention,
                        indexedAt = at("2026-05-30T11:45:00Z"),
                        isRead = false,
                        actors = persistentListOf(CARMEN),
                        subjectPost =
                            actorPost(
                                id = "mention-carmen",
                                actor = CARMEN,
                                text = "Drawing the @you.bsky.social UI for my design class — clean work.",
                                indexedAt = at("2026-05-30T11:45:00Z"),
                            ),
                    ),
                    NotificationItemUi.Aggregated(
                        itemKey = "agg:Follow:2026-05-30",
                        reason = NotificationReason.Follow,
                        indexedAt = at("2026-05-30T10:20:00Z"),
                        isRead = true,
                        actors = persistentListOf(FEDE, GABE, HUGO),
                        subjectPost = null,
                    ),
                    NotificationItemUi.Single(
                        itemKey = "quote:diego",
                        reason = NotificationReason.Quote,
                        indexedAt = at("2026-05-29T18:05:00Z"),
                        isRead = true,
                        actors = persistentListOf(DIEGO),
                        subjectPost =
                            actorPost(
                                id = "quote-diego",
                                actor = DIEGO,
                                text = "Native Android clients are back. Quoting because this thread nails why.",
                                indexedAt = at("2026-05-29T18:05:00Z"),
                            ),
                    ),
                    NotificationItemUi.Single(
                        itemKey = "repost:elena",
                        reason = NotificationReason.Repost,
                        indexedAt = at("2026-05-29T16:40:00Z"),
                        isRead = true,
                        actors = persistentListOf(ELENA),
                        subjectPost =
                            subjectPost(
                                id = "perf-note",
                                text = "Cold start under 400ms with the new baseline profile. Numbers in the thread.",
                                indexedAt = at("2026-05-29T08:00:00Z"),
                                stats = PostStatsUi(likeCount = 88, repostCount = 19, replyCount = 7),
                            ),
                    ),
                    NotificationItemUi.Aggregated(
                        itemKey = "agg:Repost:perf-note",
                        reason = NotificationReason.Repost,
                        indexedAt = at("2026-05-29T16:35:00Z"),
                        isRead = true,
                        actors = persistentListOf(FEDE, GABE),
                        subjectPost =
                            subjectPost(
                                id = "perf-note",
                                text = "Cold start under 400ms with the new baseline profile. Numbers in the thread.",
                                indexedAt = at("2026-05-29T08:00:00Z"),
                                stats = PostStatsUi(likeCount = 88, repostCount = 19, replyCount = 7),
                            ),
                    ),
                    NotificationItemUi.Single(
                        itemKey = "follow:bob",
                        reason = NotificationReason.Follow,
                        indexedAt = at("2026-05-29T12:00:00Z"),
                        isRead = true,
                        actors = persistentListOf(BOB),
                        subjectPost = null,
                    ),
                    NotificationItemUi.Single(
                        itemKey = "starterpack:gabe",
                        reason = NotificationReason.StarterpackJoined,
                        indexedAt = at("2026-05-28T20:15:00Z"),
                        isRead = true,
                        actors = persistentListOf(GABE),
                        subjectPost = null,
                    ),
                    NotificationItemUi.Single(
                        itemKey = "verified:hugo",
                        reason = NotificationReason.Verified,
                        indexedAt = at("2026-05-28T09:30:00Z"),
                        isRead = true,
                        actors = persistentListOf(HUGO),
                        subjectPost = null,
                    ),
                )

            /** Unread rows in the unfiltered fixture, for the tab badge. */
            private val UNREAD_COUNT: Int = NOTIFICATIONS.count { !it.isRead }
        }
    }
