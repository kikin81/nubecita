package net.kikin.nubecita.core.feeds

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class FakePinnedFeedsRepository
    @Inject
    constructor() : PinnedFeedsRepository {
        override suspend fun loadPinnedFeeds(): PinnedFeedsResult {
            val feeds =
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
                )
            return PinnedFeedsResult(feeds = feeds, usedFallback = false, error = null)
        }

        override fun validateSelectedFeedUri(
            uri: String?,
            pinned: List<PinnedFeedUi>,
        ): String =
            uri
                ?.takeIf { candidate -> pinned.any { it.uri == candidate } }
                ?: PinnedFeedsRepository.FOLLOWING_FEED_URI
    }
