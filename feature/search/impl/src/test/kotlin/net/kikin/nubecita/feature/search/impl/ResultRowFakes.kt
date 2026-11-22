package net.kikin.nubecita.feature.search.impl

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsResult
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi

/**
 * Hand-written fakes for the follow / pin primitives, shared by the Discover,
 * People and Feeds presenter tests (all three use the same repositories).
 */
internal class FakeFollowRepository(
    var followResult: Result<String> = Result.success("at://did:plc:me/app.bsky.graph.follow/new"),
    var unfollowResult: Result<Unit> = Result.success(Unit),
) : FollowRepository {
    val followCallDids = mutableListOf<String>()
    val unfollowCallUris = mutableListOf<String>()

    override suspend fun follow(subjectDid: String): Result<String> {
        followCallDids += subjectDid
        return followResult
    }

    override suspend fun unfollow(followUri: String): Result<Unit> {
        unfollowCallUris += followUri
        return unfollowResult
    }
}

internal class FakePinnedFeedsRepository(
    var pinResult: Result<Unit> = Result.success(Unit),
    var unpinResult: Result<Unit> = Result.success(Unit),
) : PinnedFeedsRepository {
    val pinCallUris = mutableListOf<String>()
    val unpinCallUris = mutableListOf<String>()

    private val pinnedFlow = MutableStateFlow(PinnedFeedsResult(persistentListOf(), usedFallback = false))

    fun setPinnedUris(uris: Set<String>) {
        val feeds =
            uris
                .map { uri ->
                    PinnedFeedUi(
                        id = uri,
                        uri = uri,
                        kind = FeedKind.Generator,
                        displayName = "Feed",
                        avatarUrl = null,
                    )
                }.toImmutableList()
        pinnedFlow.value = PinnedFeedsResult(feeds = feeds, usedFallback = false)
    }

    override fun observePinnedFeeds(): Flow<PinnedFeedsResult> = pinnedFlow

    override suspend fun refresh(): Result<Unit> = Result.success(Unit)

    override fun validateSelectedFeedUri(
        uri: String?,
        pinned: List<PinnedFeedUi>,
    ): String = uri ?: PinnedFeedsRepository.FOLLOWING_FEED_URI

    override suspend fun pinFeed(uri: String): Result<Unit> {
        pinCallUris += uri
        return pinResult
    }

    override suspend fun unpinFeed(uri: String): Result<Unit> {
        unpinCallUris += uri
        return unpinResult
    }

    override suspend fun reorderPinnedFeeds(orderedPinnedUris: List<String>): Result<Unit> = Result.success(Unit)
}
