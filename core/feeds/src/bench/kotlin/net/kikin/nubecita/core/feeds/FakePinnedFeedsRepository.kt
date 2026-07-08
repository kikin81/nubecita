package net.kikin.nubecita.core.feeds

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor in-memory fake. Backed by a [MutableStateFlow] so
 * `reorderPinnedFeeds` / `unpinFeed` actually mutate the emitted list — the
 * manage-feeds screen is round-trip smoke-testable on `:app:assembleBenchDebug`
 * (reorder + remove survive re-opening the screen), not just visually.
 */
@Singleton
public class FakePinnedFeedsRepository
    @Inject
    constructor() : PinnedFeedsRepository {
        private val feeds: MutableStateFlow<ImmutableList<PinnedFeedUi>> =
            MutableStateFlow(
                persistentListOf(
                    PinnedFeedUi(
                        id = PinnedFeedsRepository.FOLLOWING_FEED_URI,
                        uri = PinnedFeedsRepository.FOLLOWING_FEED_URI,
                        kind = FeedKind.Following,
                        displayName = "Following",
                        avatarUrl = null,
                    ),
                    PinnedFeedUi(
                        id = "whats-hot",
                        uri = PinnedFeedsRepository.DISCOVER_FEED_URI,
                        kind = FeedKind.Generator,
                        displayName = "Discover",
                        avatarUrl = null,
                    ),
                    PinnedFeedUi(
                        id = "list-id-1",
                        uri = "at://did:plc:fake/app.bsky.graph.list/1",
                        kind = FeedKind.List,
                        displayName = "Cool Friends",
                        avatarUrl = null,
                    ),
                    PinnedFeedUi(
                        id = "list-id-2",
                        uri = "at://did:plc:fake/app.bsky.graph.list/2",
                        kind = FeedKind.List,
                        displayName = "Tech News",
                        avatarUrl = null,
                    ),
                ),
            )

        override fun observePinnedFeeds(): Flow<PinnedFeedsResult> = feeds.map { PinnedFeedsResult(feeds = it, usedFallback = false, error = null) }

        override suspend fun refresh(): Result<Unit> = Result.success(Unit)

        override fun validateSelectedFeedUri(
            uri: String?,
            pinned: List<PinnedFeedUi>,
        ): String =
            uri
                ?.takeIf { candidate -> pinned.any { it.uri == candidate } }
                ?: PinnedFeedsRepository.FOLLOWING_FEED_URI

        override suspend fun pinFeed(uri: String): Result<Unit> = Result.success(Unit)

        override suspend fun unpinFeed(uri: String): Result<Unit> {
            feeds.update { list -> list.filterNot { it.uri == uri }.toImmutableList() }
            return Result.success(Unit)
        }

        override suspend fun reorderPinnedFeeds(orderedPinnedUris: List<String>): Result<Unit> {
            feeds.update { list ->
                val byUri = list.associateBy { it.uri }
                val ordered = orderedPinnedUris.mapNotNull { byUri[it] }
                val extra = list.filterNot { it.uri in orderedPinnedUris }
                (ordered + extra).toImmutableList()
            }
            return Result.success(Unit)
        }
    }
