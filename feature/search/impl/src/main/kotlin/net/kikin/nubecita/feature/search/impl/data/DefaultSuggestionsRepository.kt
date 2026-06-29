package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.GetSuggestionsRequest
import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GeneratorView
import io.github.kikin81.atproto.app.bsky.feed.GetFeedRequest
import io.github.kikin81.atproto.app.bsky.feed.GetSuggestedFeedsRequest
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.data.models.EmbedUi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SuggestionsRepository] backed by the atproto-kotlin SDK.
 *
 * Three XRPC methods:
 * - `app.bsky.actor.getSuggestions` → [SuggestedAccountUi] list
 * - `app.bsky.feed.getSuggestedFeeds` → [SuggestedFeedUi] list
 * - `app.bsky.feed.getFeed` (limit ≤ 3) → [FeedPreviewPostUi] list
 *
 * Follows the same error-handling shape as [DefaultSearchFeedsRepository]:
 * - [CancellationException] is re-thrown before the Timber log so the
 *   caller's coroutine scope can cancel cleanly.
 * - All other [Throwable]s are caught, logged, and wrapped in
 *   [Result.failure].
 *
 * `viewer.following` field paths (confirmed from SDK 9.5.0 sources):
 * - `ViewerState.following: AtUri?` — non-null iff the viewer follows this actor.
 * - `ViewerState.knownFollowers: KnownFollowers?` — count + followers list.
 * - `KnownFollowers.count: Long` — total mutual count.
 * - `KnownFollowers.followers: List<ProfileViewBasic>` — avatar-stack subset.
 */
@Singleton
internal class DefaultSuggestionsRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SuggestionsRepository {
        override suspend fun getSuggestedAccounts(limit: Int): Result<List<SuggestedAccountUi>> =
            withContext(dispatcher) {
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ActorService(client)
                            .getSuggestions(GetSuggestionsRequest(limit = limit.toLong()))
                    Result.success(response.actors.map { it.toSuggestedAccountUi() })
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "getSuggestions failed: %s", t.javaClass.name)
                    Result.failure(t)
                }
            }

        override suspend fun getSuggestedFeeds(limit: Int): Result<List<SuggestedFeedUi>> =
            withContext(dispatcher) {
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client)
                            .getSuggestedFeeds(GetSuggestedFeedsRequest(limit = limit.toLong()))
                    Result.success(response.feeds.map { it.toSuggestedFeedUi() })
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "getSuggestedFeeds failed: %s", t.javaClass.name)
                    Result.failure(t)
                }
            }

        override suspend fun getFeedPreview(
            feedUri: String,
            limit: Int,
        ): Result<List<FeedPreviewPostUi>> =
            withContext(dispatcher) {
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client)
                            .getFeed(GetFeedRequest(feed = AtUri(feedUri), limit = limit.toLong()))
                    Result.success(
                        response.feed
                            .take(limit)
                            .mapNotNull { feedViewPost ->
                                feedViewPost.post.toPostUiCore()?.let { postUi ->
                                    FeedPreviewPostUi(
                                        authorHandle = postUi.author.handle,
                                        authorAvatarUrl = postUi.author.avatarUrl,
                                        text = postUi.text,
                                        thumbnailUrl = postUi.embed.firstThumbnailUrl(),
                                    )
                                }
                            },
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "getFeedPreview(uri=%s) failed: %s", feedUri, t.javaClass.name)
                    Result.failure(t)
                }
            }

        private companion object {
            const val TAG = "SuggestionsRepo"
        }
    }

/**
 * Extracts the first thumbnail URL from an [EmbedUi].
 *
 * - [EmbedUi.ImageContainerEmbed] ([EmbedUi.Images] or [EmbedUi.Gallery]):
 *   the first image's thumb URL.
 * - [EmbedUi.External]: the link-card thumb URL.
 * - [EmbedUi.Gif]: the static poster thumb URL.
 * - [EmbedUi.Video]: the video poster URL.
 * - [EmbedUi.RecordWithMedia]: the media slot's thumbnail (same dispatch,
 *   one level deeper).
 * - All other variants ([EmbedUi.Empty], [EmbedUi.Record],
 *   [EmbedUi.RecordUnavailable], [EmbedUi.Unsupported]): null.
 */
private fun EmbedUi.firstThumbnailUrl(): String? =
    when (this) {
        is EmbedUi.ImageContainerEmbed -> items.firstOrNull()?.thumbUrl
        is EmbedUi.External -> thumbUrl
        is EmbedUi.Gif -> thumbUrl
        is EmbedUi.Video -> posterUrl
        is EmbedUi.RecordWithMedia ->
            when (val m = media) {
                is EmbedUi.ImageContainerEmbed -> m.items.firstOrNull()?.thumbUrl
                is EmbedUi.External -> m.thumbUrl
                is EmbedUi.Gif -> m.thumbUrl
                is EmbedUi.Video -> m.posterUrl
            }
        else -> null
    }

private fun ProfileView.toSuggestedAccountUi(): SuggestedAccountUi =
    SuggestedAccountUi(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeIf { it.isNotBlank() },
        avatarUrl = avatar?.raw,
        isFollowing = viewer?.following != null,
        followUri = viewer?.following?.raw,
        mutualsCount = viewer?.knownFollowers?.count?.toInt() ?: 0,
        mutualAvatarUrls =
            viewer
                ?.knownFollowers
                ?.followers
                ?.mapNotNull { it.avatar?.raw }
                ?.toImmutableList()
                ?: persistentListOf(),
    )

private fun GeneratorView.toSuggestedFeedUi(): SuggestedFeedUi =
    SuggestedFeedUi(
        uri = uri.raw,
        displayName = displayName,
        creatorHandle = creator.handle.raw,
        avatarUrl = avatar?.raw,
        description = description?.takeIf { it.isNotBlank() },
    )
