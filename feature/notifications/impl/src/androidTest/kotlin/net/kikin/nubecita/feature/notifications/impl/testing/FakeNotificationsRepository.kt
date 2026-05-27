package net.kikin.nubecita.feature.notifications.impl.testing

import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.data.models.NotificationItemUiFixtures
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsPage
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * In-memory [NotificationsRepository] for instrumentation tests. Hilt-
 * injected via [TestNotificationsRepositoryModule]'s
 * `@TestInstallIn(replaces = [NotificationsRepositoryModule::class])`.
 *
 * Returns a fixed page of mixed-reason notifications by default. Tests
 * that need a different shape can mutate [pageByFilter] or [failureToReturn]
 * before triggering a fetch — the screen's `init` fires on first
 * composition and reads through here.
 *
 * Honors [NotificationFilter] by delegating into [pageByFilter] so a
 * filter-chip switch in the screen produces a different result set.
 * Defaults to a single shared page for every filter.
 *
 * Records `fetchPage` + `markSeen` invocations on per-instance lists
 * for behavioral assertions in the test layer. `unreadCount()` is not
 * recorded — the polling observer fires it on every cold launch and
 * the call shape carries no useful payload to assert against.
 */
@Singleton
internal class FakeNotificationsRepository
    @Inject
    constructor() : NotificationsRepository {
        @Volatile
        var pageByFilter: Map<NotificationFilter, NotificationsPage> =
            NotificationFilter.entries.associateWith { DEFAULT_PAGE }

        @Volatile
        var failureToReturn: Throwable? = null

        @Volatile
        var unreadCountToReturn: Int = 0

        val fetchPageCalls: MutableList<FetchCall> = mutableListOf()
        val markSeenCalls: MutableList<Instant> = mutableListOf()

        override suspend fun fetchPage(
            filter: NotificationFilter,
            cursor: String?,
        ): Result<NotificationsPage> {
            fetchPageCalls += FetchCall(filter, cursor)
            failureToReturn?.let { return Result.failure(it) }
            return Result.success(pageByFilter[filter] ?: DEFAULT_PAGE)
        }

        override suspend fun markSeen(seenAt: Instant): Result<Unit> {
            markSeenCalls += seenAt
            return Result.success(Unit)
        }

        override suspend fun unreadCount(): Result<Int> = Result.success(unreadCountToReturn)

        data class FetchCall(
            val filter: NotificationFilter,
            val cursor: String?,
        )

        companion object {
            /**
             * Mixed-reason fixture page covering the most-visually-distinct
             * row shapes the screen renders: a 3-actor aggregated likes row
             * (avatar stack + chevron), a 2-actor aggregated follows row,
             * single replies / mentions / quotes (each with their actor's
             * new post hydrated), and a follow row with no subject post.
             * Stable enough to make `assertIsDisplayed` assertions on the
             * actor headlines reliable across runs.
             */
            val DEFAULT_PAGE: NotificationsPage =
                NotificationsPage(
                    items =
                        listOf<NotificationItemUi>(
                            NotificationItemUiFixtures.aggregatedLikes(
                                actorCount = 3,
                                itemKey = "fake-likes-agg-1",
                            ),
                            NotificationItemUiFixtures.singleReply(
                                itemKey = "fake-reply-1",
                            ),
                            NotificationItemUiFixtures.aggregatedFollows(
                                actorCount = 2,
                                itemKey = "fake-follows-agg-1",
                            ),
                            NotificationItemUiFixtures.singleMention(
                                itemKey = "fake-mention-1",
                            ),
                            NotificationItemUiFixtures.singleQuote(
                                itemKey = "fake-quote-1",
                            ),
                            NotificationItemUiFixtures.singleFollow(
                                itemKey = "fake-follow-1",
                            ),
                        ).toImmutableList(),
                    nextCursor = null,
                )

            /** Empty page — drives the screen's `Empty` view-state branch. */
            val EMPTY_PAGE: NotificationsPage =
                NotificationsPage(
                    items = emptyList<NotificationItemUi>().toImmutableList(),
                    nextCursor = null,
                )
        }
    }
