package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GeneratorView
import io.github.kikin81.atproto.app.bsky.feed.GetFeedRequest
import io.github.kikin81.atproto.app.bsky.unspecced.TrendView
import io.github.kikin81.atproto.com.atproto.identity.IdentityService
import io.github.kikin81.atproto.com.atproto.identity.ResolveHandleRequest
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.XrpcClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.auth.viewerDidOrNull
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.feature.search.impl.DiscoverFeedUi
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus
import net.kikin.nubecita.feature.search.impl.FeedPreviewStatus
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [ExploreRepository].
 *
 * Three of the four endpoints live in Bluesky's `unspecced` namespace, which has no
 * generated service in the SDK, so — like [DefaultSearchFeedsRepository] — they are
 * called through [XrpcClient.query] with the literal NSID and hand-written
 * `@Serializable` envelopes (private, below), reusing the generated [TrendView],
 * [GeneratorView] and [ProfileView] for the items:
 *
 *  - `app.bsky.unspecced.getTrends` (limit [TRENDS_LIMIT])
 *  - `app.bsky.unspecced.getSuggestedFeeds` (limit [FEEDS_LIMIT])
 *  - `app.bsky.unspecced.getSuggestedUsersForExplore` (limit [ACCOUNTS_LIMIT])
 *
 * plus the generated `app.bsky.feed.getFeed` for a feed's preview strip and
 * `com.atproto.identity.resolveHandle` for [resolveDid].
 *
 * ## Shared, per-process content
 * One singleton holds [content]; presenters only project it. A load is shared by
 * whoever asks while it runs, runs in the application scope (so a presenter going away
 * mid-load does not cancel it for the other), and writes its result only if the account
 * is still the one it started for.
 *
 * ## Account changes
 * [content] resets when the session leaves signed-in or its DID changes, with the same
 * synchronous-baseline approach as [SearchFirstPages]. Every write also checks, at write
 * time, that the signed-in DID is still the one the work began under, so a result for the
 * old account can never reach the new one even before the reset has run.
 */
@Singleton
internal class DefaultExploreRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : ExploreRepository {
        /** The account and reset generation a piece of work started under. */
        private data class Token(
            val epoch: Int,
            val did: String?,
        )

        private val session = sessionStateProvider.state

        private fun currentDid(): String? = (session.value as? SessionState.SignedIn)?.did

        private val lock = Any()
        private val mutableContent = MutableStateFlow(ExploreContent())
        private var epoch = 0
        private var inFlight: Deferred<Unit>? = null
        private var inFlightForced = false
        private val dismissedDids = mutableSetOf<String>()

        override val content: StateFlow<ExploreContent> = mutableContent.asStateFlow()

        init {
            // The baseline is read now, and the collector starts undispatched,
            // so a change between construction and the first collection is seen.
            var knownDid = currentDid()
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                session
                    .map { (it as? SessionState.SignedIn)?.did }
                    .distinctUntilChanged()
                    .collect { did ->
                        if (did != knownDid) {
                            knownDid = did
                            reset()
                        }
                    }
            }
        }

        private fun reset() {
            synchronized(lock) {
                epoch++
                // Not cancelled: a presenter may be awaiting it. Its writes are dropped by
                // the token check, and forgetting it makes the next load start fresh.
                inFlight = null
                dismissedDids.clear()
                mutableContent.value = ExploreContent()
            }
        }

        /** Applies [transform] unless the account or generation moved on since [token]. */
        private fun commit(
            token: Token,
            transform: (ExploreContent) -> ExploreContent,
        ) {
            synchronized(lock) {
                if (token.epoch == epoch && token.did == currentDid()) mutableContent.update(transform)
            }
        }

        override suspend fun load(force: Boolean) {
            while (true) {
                val running: Deferred<Unit>?
                val runningIsForced: Boolean
                val started: Deferred<Unit>?
                synchronized(lock) {
                    running = inFlight?.takeIf { it.isActive }
                    runningIsForced = inFlightForced
                    started =
                        when {
                            running != null -> null
                            !force && mutableContent.value.isFullyLoaded() -> null
                            else ->
                                startLoad(force).also {
                                    inFlight = it
                                    inFlightForced = force
                                }
                        }
                }
                when {
                    started != null -> {
                        started.await()
                        return
                    }
                    running == null -> return
                    // Someone else's load covers this caller, unless it is a forced caller meeting
                    // an unforced load (which skips loaded sections): wait it out, then go again.
                    !force || runningIsForced -> {
                        running.await()
                        return
                    }
                    else -> running.await()
                }
            }
        }

        private fun ExploreContent.isFullyLoaded(): Boolean =
            trendsStatus == DiscoverSectionStatus.Loaded &&
                feedsStatus == DiscoverSectionStatus.Loaded &&
                accountsStatus == DiscoverSectionStatus.Loaded

        /** Called under [lock]. */
        private fun startLoad(force: Boolean): Deferred<Unit> {
            val before = mutableContent.value
            val loadTrends = force || before.trendsStatus != DiscoverSectionStatus.Loaded
            val loadFeeds = force || before.feedsStatus != DiscoverSectionStatus.Loaded
            val loadAccounts = force || before.accountsStatus != DiscoverSectionStatus.Loaded
            val token = Token(epoch, currentDid())
            mutableContent.update {
                it.copy(
                    trendsStatus = if (loadTrends) DiscoverSectionStatus.Loading else it.trendsStatus,
                    feedsStatus = if (loadFeeds) DiscoverSectionStatus.Loading else it.feedsStatus,
                    accountsStatus = if (loadAccounts) DiscoverSectionStatus.Loading else it.accountsStatus,
                )
            }
            return scope.async {
                coroutineScope {
                    if (loadTrends) launch { loadTrends(token) }
                    if (loadFeeds) launch { loadFeeds(token) }
                    if (loadAccounts) launch { loadAccounts(token) }
                }
            }
        }

        private suspend fun loadTrends(token: Token) {
            val result =
                request("getTrends") { client ->
                    client
                        .query(
                            nsid = NSID_TRENDS,
                            params = LimitRequest(limit = TRENDS_LIMIT),
                            paramsSerializer = LimitRequest.serializer(),
                            responseSerializer = GetTrendsResponse.serializer(),
                        ).trends
                        .map { it.toTrendUi() }
                        .usableTrends()
                        .toImmutableList()
                }
            commit(token) { current ->
                result.fold(
                    onSuccess = { list ->
                        current.copy(
                            trends = list,
                            trendsStatus = if (list.isEmpty()) DiscoverSectionStatus.Empty else DiscoverSectionStatus.Loaded,
                        )
                    },
                    onFailure = { current.copy(trendsStatus = DiscoverSectionStatus.Error) },
                )
            }
        }

        private suspend fun loadFeeds(token: Token) {
            val result =
                request("getSuggestedFeeds") { client ->
                    client
                        .query(
                            nsid = NSID_SUGGESTED_FEEDS,
                            params = LimitRequest(limit = FEEDS_LIMIT),
                            paramsSerializer = LimitRequest.serializer(),
                            responseSerializer = GetSuggestedFeedsResponse.serializer(),
                        ).feeds
                        .map { it.toSuggestedFeedUi() }
                }
            commit(token) { current ->
                result.fold(
                    onSuccess = { list ->
                        // Keep a preview that is already loaded for a feed that is still suggested.
                        val priorByUri = current.feeds.associateBy { it.feed.uri }
                        current.copy(
                            feeds =
                                list
                                    .map { feed ->
                                        val prior = priorByUri[feed.uri]
                                        DiscoverFeedUi(
                                            feed = feed,
                                            preview = prior?.preview,
                                            previewStatus = prior?.previewStatus ?: FeedPreviewStatus.Idle,
                                        )
                                    }.toImmutableList(),
                            feedsStatus = if (list.isEmpty()) DiscoverSectionStatus.Empty else DiscoverSectionStatus.Loaded,
                        )
                    },
                    onFailure = { current.copy(feedsStatus = DiscoverSectionStatus.Error) },
                )
            }
        }

        private suspend fun loadAccounts(token: Token) {
            val result =
                request("getSuggestedUsersForExplore") { client ->
                    client
                        .query(
                            nsid = NSID_SUGGESTED_USERS,
                            params = LimitRequest(limit = ACCOUNTS_LIMIT),
                            paramsSerializer = LimitRequest.serializer(),
                            responseSerializer = GetSuggestedUsersResponse.serializer(),
                        ).actors
                        .map { it.toSuggestedAccountUi() }
                }
            commit(token) { current ->
                result.fold(
                    onSuccess = { list ->
                        val visible = list.filter { it.did !in dismissedDids }
                        current.copy(
                            accounts = visible.toImmutableList(),
                            accountsStatus = if (visible.isEmpty()) DiscoverSectionStatus.Empty else DiscoverSectionStatus.Loaded,
                        )
                    },
                    onFailure = { current.copy(accountsStatus = DiscoverSectionStatus.Error) },
                )
            }
        }

        override suspend fun loadFeedPreview(feedUri: String) {
            val token =
                synchronized(lock) {
                    val feed = mutableContent.value.feeds.find { it.feed.uri == feedUri } ?: return
                    // Idle is the only state that triggers a fetch.
                    if (feed.previewStatus != FeedPreviewStatus.Idle) return
                    mutableContent.update { it.withFeed(feedUri) { f -> f.copy(previewStatus = FeedPreviewStatus.Loading) } }
                    Token(epoch, currentDid())
                }
            val result =
                request("getFeed") { client ->
                    // Read once per page, not per post: a session change midway through would
                    // otherwise mark part of one page as the viewer's own and the rest not.
                    val viewerDid = sessionStateProvider.viewerDidOrNull
                    FeedService(client)
                        .getFeed(GetFeedRequest(feed = AtUri(feedUri), limit = PREVIEW_LIMIT))
                        .feed
                        .take(PREVIEW_LIMIT.toInt())
                        .mapNotNull { feedViewPost ->
                            feedViewPost.post.toPostUiCore(viewerDid)?.let { postUi ->
                                FeedPreviewPostUi(
                                    authorHandle = postUi.author.handle,
                                    authorAvatarUrl = postUi.author.avatarUrl,
                                    text = postUi.text,
                                    thumbnailUrl = postUi.embed.firstThumbnailUrl(),
                                )
                            }
                        }.toImmutableList()
                }
            commit(token) { current ->
                current.withFeed(feedUri) { feed ->
                    result.fold(
                        onSuccess = { posts -> feed.copy(preview = posts, previewStatus = FeedPreviewStatus.Loaded) },
                        onFailure = { feed.copy(previewStatus = FeedPreviewStatus.Error) },
                    )
                }
            }
        }

        private fun ExploreContent.withFeed(
            uri: String,
            transform: (DiscoverFeedUi) -> DiscoverFeedUi,
        ): ExploreContent = copy(feeds = feeds.map { if (it.feed.uri == uri) transform(it) else it }.toImmutableList())

        override fun dismissAccount(did: String) {
            synchronized(lock) {
                dismissedDids += did
                mutableContent.update { it.copy(accounts = it.accounts.filter { account -> account.did != did }.toImmutableList()) }
            }
        }

        override suspend fun resolveDid(actor: String): Result<String> {
            if (actor.startsWith("did:")) return Result.success(actor)
            return request("resolveHandle") { client ->
                IdentityService(client)
                    .resolveHandle(ResolveHandleRequest(handle = Handle(actor)))
                    .did.raw
            }
        }

        /** Runs [block] with the authenticated client off the main thread; a failure becomes a [Result.failure]. */
        private suspend fun <T> request(
            what: String,
            block: suspend (XrpcClient) -> T,
        ): Result<T> =
            withContext(dispatcher) {
                try {
                    Result.success(block(xrpcClientProvider.authenticated()))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "%s failed: %s", what, t.javaClass.name)
                    Result.failure(t)
                }
            }

        private companion object {
            const val TAG = "ExploreRepo"
            const val NSID_TRENDS = "app.bsky.unspecced.getTrends"
            const val NSID_SUGGESTED_FEEDS = "app.bsky.unspecced.getSuggestedFeeds"
            const val NSID_SUGGESTED_USERS = "app.bsky.unspecced.getSuggestedUsersForExplore"
            const val TRENDS_LIMIT = 10L
            const val FEEDS_LIMIT = 8L
            const val ACCOUNTS_LIMIT = 12L
            const val PREVIEW_LIMIT = 3L
        }
    }

/**
 * Hand-written mirror of the params block shared by the three `unspecced` queries
 * (`limit` only; `getSuggestedUsersForExplore`'s optional `category` is never sent).
 */
@Serializable
private data class LimitRequest(
    val limit: Long? = null,
)

/**
 * Response envelopes. The list each lexicon marks `required` has no default, so a
 * response that drops it fails to decode (kotlinx-serialization throws
 * `MissingFieldException`) and marks just that section as errored, rather than
 * rendering as an empty section.
 */
@Serializable
private data class GetTrendsResponse(
    val trends: List<TrendView>,
    val recIdStr: String? = null,
)

@Serializable
private data class GetSuggestedFeedsResponse(
    val feeds: List<GeneratorView>,
)

@Serializable
private data class GetSuggestedUsersResponse(
    val actors: List<ProfileView>,
    val recIdStr: String? = null,
)

private fun TrendView.toTrendUi(): TrendUi =
    TrendUi(
        topic = topic,
        displayName = displayName,
        link = link,
        category = category?.takeIf { it.isNotBlank() },
        postCount = postCount,
        isHot = status == "hot",
    )

private fun GeneratorView.toSuggestedFeedUi(): SuggestedFeedUi =
    SuggestedFeedUi(
        uri = uri.raw,
        displayName = displayName,
        creatorHandle = creator.handle.raw,
        avatarUrl = avatar?.raw,
        description = description?.takeIf { it.isNotBlank() },
    )

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

/**
 * Extracts the first thumbnail URL from an [EmbedUi]: the first image of an image
 * container, a link card's thumb, a GIF's poster, a video's poster, or (for a
 * record-with-media) the media slot's. Every other variant has none.
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
