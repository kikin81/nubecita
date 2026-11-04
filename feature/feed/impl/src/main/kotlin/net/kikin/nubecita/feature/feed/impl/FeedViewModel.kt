package net.kikin.nubecita.feature.feed.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.MuteRepository
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.FeedType
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.analytics.ViewFeed
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feedmapping.dedupeByLeafPost
import net.kikin.nubecita.core.feedmapping.dedupeByThreadRoot
import net.kikin.nubecita.core.feedmapping.dedupeClusterContext
import net.kikin.nubecita.core.feedmapping.mergeChainBoundary
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.posting.OutgoingPost
import net.kikin.nubecita.core.posting.OutgoingPostStore
import net.kikin.nubecita.core.posting.toUi
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
import net.kikin.nubecita.core.preferences.AutoplayPolicy
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.OutgoingPostUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository
import net.kikin.nubecita.feature.feed.impl.data.TimelinePage
import net.kikin.nubecita.feature.search.api.Hashtag
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class FeedViewModel
    @Inject
    constructor(
        private val feedRepository: FeedRepository,
        private val postInteractionsCache: PostInteractionsCache,
        val sharedVideoPlayer: SharedVideoPlayer,
        private val analytics: AnalyticsClient,
        private val muteRepository: MuteRepository,
        private val handler: PostInteractionHandler,
        private val outgoingPostStore: OutgoingPostStore,
        autoplayPolicy: AutoplayPolicy,
    ) : MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState()),
        PostInteractionHandler by handler {
        /**
         * The feed this VM renders, bound once via [FeedEvent.Bind]
         * before the first load. Defaults to the Following timeline so a
         * pane that never receives an explicit bind (the pre-host
         * `FeedScreen`) keeps working. [boundFeedUri] is unused while
         * [boundKind] is [FeedKind.Following]; it carries the generator /
         * list AT URI for the other two kinds.
         */
        private var boundFeedUri: String = ""
        private var boundKind: FeedKind = FeedKind.Following

        /** [boundKind] as a flow, so the outgoing-post projection re-runs on a re-bind. */
        private val boundKindFlow = MutableStateFlow(FeedKind.Following)

        /**
         * The currently in-flight page fetch launched by [load] /
         * [refresh] / [loadMore], or `null` when no fetch is running. A
         * re-[bind] to a different feed cancels this before resetting the
         * slice so a suspended fetch from the prior binding can't land its
         * page into the new pane via [applyInitialPage] (and then have the
         * `feedItems.isNotEmpty()` guard short-circuit the new feed's
         * load). Cancellation only aborts the result-handling continuation;
         * none of the fetch paths flip [FeedLoadStatus] in a `finally`, so a
         * cancelled fetch leaves the status as [bind] resets it
         * ([FeedLoadStatus.Idle]) rather than a stale loading state.
         */
        private var loadJob: Job? = null

        init {
            // Bind the handler to Feed surface and this VM's scope first.
            handler.bind(PostSurface.Feed, viewModelScope)

            // Mirror the resolved autoplay answer rather than the raw
            // preference: the policy already folds in whether the connection is
            // metered, and it re-emits when that changes, so walking off wifi
            // stops autoplay without reopening the app.
            autoplayPolicy.videoAutoplayEnabled
                .onEach { enabled -> setState { copy(videoAutoplayEnabled = enabled) } }
                .launchIn(viewModelScope)

            // Mirror handler tap-markers into FeedState so FeedScreenContent
            // can animate the ±1 count transition before the network resolves.
            viewModelScope.launch {
                handler.tapMarkers.collect { markers ->
                    setState {
                        copy(
                            lastLikeTapPostUri = markers.lastLikeTapPostUri,
                            lastRepostTapPostUri = markers.lastRepostTapPostUri,
                        )
                    }
                }
            }

            // Top-level posts the user just submitted, above the Following
            // timeline until a loaded page includes them. Recomputed when the
            // store or the loaded slice changes (a re-bind resets the slice, so a
            // switch away from Following clears them).
            combine(
                outgoingPostStore.posts,
                uiState.map { it.feedItems }.distinctUntilChanged(),
                boundKindFlow,
            ) { outgoing, items, kind -> visibleOutgoing(outgoing, items, kind) }
                .distinctUntilChanged()
                .onEach { visible -> setState { copy(outgoingPosts = visible) } }
                .launchIn(viewModelScope)

            viewModelScope.launch {
                postInteractionsCache.state
                    .map { snapshot -> uiState.value.feedItems.applyInteractions(snapshot) }
                    .distinctUntilChanged()
                    .collect { merged -> setState { copy(feedItems = merged) } }
            }
        }

        override fun handleEvent(event: FeedEvent) {
            when (event) {
                is FeedEvent.Bind -> bind(event.feedUri, event.kind, event.surface)
                FeedEvent.Load -> load()
                FeedEvent.Refresh -> refresh()
                FeedEvent.LoadMore -> loadMore()
                FeedEvent.Retry -> load()
                FeedEvent.ClearError -> Unit
                is FeedEvent.OnPostTapped -> sendEffect(FeedEffect.NavigateToPost(event.post.id))
                is FeedEvent.OnAuthorTapped -> sendEffect(FeedEffect.NavigateToAuthor(event.authorDid))
                is FeedEvent.OnTagTapped -> sendEffect(FeedEffect.NavigateTo(Hashtag(event.tag, event.author)))
                is FeedEvent.OnImageTapped ->
                    sendEffect(FeedEffect.NavigateToMediaViewer(event.post.id, event.imageIndex))
                is FeedEvent.OnQuotedImageTapped ->
                    sendEffect(FeedEffect.NavigateToMediaViewer(event.quotedPostUri, event.imageIndex))
                is FeedEvent.OnQuotedPostTapped -> sendEffect(FeedEffect.NavigateToPost(event.quotedPostUri))
                is FeedEvent.OnVideoTapped -> sendEffect(FeedEffect.NavigateToVideoPlayer(event.postUri))
                is FeedEvent.RetryOutgoing -> outgoingPostStore.retry(event.localId)
                is FeedEvent.DiscardOutgoing -> outgoingPostStore.discard(event.localId)
                is FeedEvent.OnOverflowAction -> onOverflowAction(event.post, event.action)
            }
        }

        /**
         * Feed-specific override: MuteAuthor / UnmuteAuthor carry optimistic
         * feedItems mutations and a rollback on failure, which must stay in this
         * VM rather than in the shared [PostInteractionHandler]. All other
         * overflow actions are forwarded to the injected [handler] which emits the
         * appropriate [InteractionEffect] onto the channel consumed by [init].
         */
        override fun onOverflowAction(
            post: PostUi,
            action: PostOverflowAction,
        ) {
            when (action) {
                PostOverflowAction.MuteAuthor -> {
                    val authorDid = post.author.did
                    val previousItems = uiState.value.feedItems
                    setState { copy(feedItems = feedItems.removeItemsByAuthor(authorDid)) }
                    viewModelScope.launch {
                        muteRepository
                            .muteActor(authorDid)
                            .onFailure {
                                setState { copy(feedItems = previousItems) }
                                sendEffect(FeedEffect.ShowError(it.toFeedError()))
                            }
                    }
                }
                PostOverflowAction.UnmuteAuthor -> {
                    val authorDid = post.author.did
                    setState {
                        copy(
                            feedItems =
                                feedItems.updateViewerStateByAuthor(authorDid) {
                                    it.copy(isAuthorMutedByViewer = false)
                                },
                        )
                    }
                    viewModelScope.launch {
                        muteRepository
                            .unmuteActor(authorDid)
                            .onFailure {
                                setState {
                                    copy(
                                        feedItems =
                                            feedItems.updateViewerStateByAuthor(authorDid) {
                                                it.copy(isAuthorMutedByViewer = true)
                                            },
                                    )
                                }
                                sendEffect(FeedEffect.ShowError(it.toFeedError()))
                            }
                    }
                }
                else -> handler.onOverflowAction(post, action)
            }
        }

        private fun visibleOutgoing(
            outgoing: List<OutgoingPost>,
            items: List<FeedItemUi>,
            kind: FeedKind,
        ): ImmutableList<OutgoingPostUi> {
            if (kind != FeedKind.Following || outgoing.isEmpty()) return persistentListOf()
            // Every post the slice holds, not just each item's key: the user's
            // post can sit inside a self-thread chain or a reply cluster.
            val loaded = items.allPosts().mapTo(HashSet()) { it.id }
            return outgoing
                .filter { it.replyToUri == null && it.post.id !in loaded }
                .map { it.toUi() }
                .toImmutableList()
        }

        /**
         * Remember the `(feedUri, kind)` this VM renders. The host binds a
         * per-`feedUri`-keyed VM once, before the first [Load]; re-binding
         * to a *different* `(feedUri, kind)` cancels any in-flight page
         * fetch from the prior binding and resets the loaded slice +
         * pagination so the next [Load] fetches the new feed fresh.
         * Re-binding to the same pair is a no-op (the host re-emits `Bind`
         * on every recomposition of the pane's `LaunchedEffect(feedUri)`,
         * so idempotence is required to avoid wiping a loaded feed on tab
         * re-entry).
         *
         * Cancelling [loadJob] before the reset closes the race where a
         * suspended fetch from the old binding resumes after the slice is
         * cleared and writes its (now-stale) page into the new pane — the
         * `feedItems.isNotEmpty()` guard in [load] would then short-circuit
         * the new feed's load and leave the wrong content showing.
         */
        private fun bind(
            feedUri: String,
            kind: FeedKind,
            surface: PostSurface,
        ) {
            // surface always tracks the latest binding (cheap) even when the
            // feedUri/kind pair is unchanged — re-binding drives analytics surface
            // attribution for like/repost/share events via the handler.
            handler.bind(surface, viewModelScope)
            if (boundFeedUri == feedUri && boundKind == kind) return
            boundFeedUri = feedUri
            boundKind = kind
            boundKindFlow.value = kind
            // Abort any fetch launched under the prior binding so its result
            // continuation can't write a stale page after the reset below.
            loadJob?.cancel()
            loadJob = null
            // Drop any slice from a previously-bound feed and open on the
            // shimmer (InitialLoading), never the "No posts" empty screen, while
            // the new feed loads.
            setState {
                copy(
                    feedItems = persistentListOf(),
                    nextCursor = null,
                    endReached = false,
                    loadStatus = FeedLoadStatus.InitialLoading,
                )
            }
            // Drive the fetch from bind() itself. The screen ALSO dispatches a
            // `Load` from a `LaunchedEffect`, but the two effects fire in an
            // unspecified order at cold start; if `Load` ran first and bind()
            // then cancelled it, nothing would restart the fetch and the feed
            // would hang on an empty shimmer. Starting here makes bind the
            // single source of truth for the initial fetch; the screen's `Load`
            // no-ops (a job is already in flight).
            startInitialFetch()
        }

        /**
         * Dispatch a page fetch on the bound [FeedKind]. All three kinds
         * return the same [TimelinePage] shape, so every caller
         * (`load` / `refresh` / `loadMore`) handles the result identically
         * regardless of kind — the dispatch is the only kind-aware seam.
         */
        private suspend fun fetchPage(cursor: String?): Result<TimelinePage> =
            when (boundKind) {
                FeedKind.Following -> feedRepository.getTimeline(cursor = cursor)
                FeedKind.Generator -> feedRepository.getFeed(feedUri = boundFeedUri, cursor = cursor)
                FeedKind.List -> feedRepository.getListFeed(listUri = boundFeedUri, cursor = cursor)
            }

        private fun load() {
            // Don't re-fetch when a loaded slice is already present.
            // `LaunchedEffect(Unit) { Load }` in FeedScreen re-fires every
            // time the screen re-enters composition (composer route pops,
            // tab switch back, etc.); without this guard each re-entry
            // wholesale-replaces `feedItems` and erases scroll position,
            // any optimistic mutations (like / repost / replyCount), and
            // the cursor cluster-context dedupe state.
            if (uiState.value.feedItems.isNotEmpty()) return
            // A fetch is already in flight (the initial fetch started by bind(),
            // a refresh, or a load-more all share loadJob) — second Load is a
            // no-op so the UI can dispatch on every recomposition without N
            // concurrent fetches racing on setState. This is the single "in
            // flight" signal, independent of which status is showing. Retry from
            // InitialError still proceeds because that job has already finished.
            if (loadJob?.isActive == true) return
            startInitialFetch()
        }

        /**
         * Launch the initial-page fetch on the bound feed and project the
         * outcome. Shared by [bind] (cold start / feed switch) and [load]
         * (unbound pane + retry). Sets [FeedLoadStatus.InitialLoading] so the
         * shimmer shows for the whole fetch, then settles to [Idle] on success
         * or [FeedLoadStatus.InitialError] on failure.
         */
        private fun startInitialFetch() {
            setState { copy(loadStatus = FeedLoadStatus.InitialLoading) }
            loadJob =
                viewModelScope.launch {
                    fetchPage(cursor = null)
                        .onSuccess { page -> applyInitialPage(page) }
                        .onFailure { throwable ->
                            setState {
                                copy(loadStatus = FeedLoadStatus.InitialError(throwable.toFeedError()))
                            }
                        }
                }
        }

        private fun refresh() {
            // Refresh and append/initial-load are mutually exclusive per the
            // mvi-foundation spec: dispatching Refresh while another load is
            // in flight is a no-op (drop the event rather than queue, so the
            // back-pressure on a flapping pull-to-refresh gesture stays at the
            // user's wrist).
            if (uiState.value.loadStatus != FeedLoadStatus.Idle) return
            setState { copy(loadStatus = FeedLoadStatus.Refreshing) }
            loadJob =
                viewModelScope.launch {
                    fetchPage(cursor = null)
                        .onSuccess { page ->
                            // Replace head; cursor becomes the response cursor (may be null
                            // when the entire feed fits in one page).
                            val refreshed =
                                page.feedItems
                                    .dedupeByThreadRoot()
                                    .dedupeClusterContext()
                                    .dedupeByLeafPost()
                                    .toImmutableList()
                            postInteractionsCache.seed(refreshed.allPosts())
                            setState {
                                copy(
                                    feedItems = refreshed,
                                    nextCursor = page.nextCursor,
                                    endReached = page.nextCursor == null,
                                    loadStatus = FeedLoadStatus.Idle,
                                )
                            }
                        }.onFailure { throwable ->
                            // Preserve feedItems on refresh failure; surface as a snackbar.
                            setState { copy(loadStatus = FeedLoadStatus.Idle) }
                            sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                        }
                }
        }

        private fun loadMore() {
            val current = uiState.value
            // End-of-feed: do nothing. Idempotent — repeat LoadMore once
            // endReached has flipped is a no-op.
            if (current.endReached) return
            // Mutually exclusive with refresh / initial-load: if any fetch is
            // already in flight, drop the LoadMore event. Prevents the
            // overlapping-fetch race where two getTimeline calls both update
            // feedItems + nextCursor and the last writer wins non-deterministically.
            if (current.loadStatus != FeedLoadStatus.Idle) return
            setState { copy(loadStatus = FeedLoadStatus.Appending) }
            loadJob =
                viewModelScope.launch {
                    fetchPage(cursor = current.nextCursor)
                        .onSuccess { page ->
                            // Page-boundary chain merge: if the existing tail's
                            // last chainable PostUi links (via the strict rule)
                            // to the new page's first wire entry, absorb the
                            // tail into the new page's first item before
                            // appending. Per
                            // `add-feed-same-author-thread-chain` design
                            // Decision 3, arbitrary cursor cuts shouldn't
                            // visually break a self-thread chain.
                            val merge =
                                mergeChainBoundary(
                                    existing = uiState.value.feedItems,
                                    newPageItems = page.feedItems,
                                    newPageWirePosts = page.wirePosts,
                                )
                            // De-dupe by FeedItemUi.key so a server returning a page
                            // that overlaps the current tail (rare but possible during
                            // cursor resyncs) doesn't show the same item twice. The
                            // key is the leaf URI for ReplyCluster / SelfThreadChain
                            // and the post URI for Single — stable across paging.
                            val seen = merge.trimmedExisting.mapTo(HashSet()) { it.key }
                            val appended =
                                (merge.trimmedExisting + merge.pageWithAbsorbedHead.filter { seen.add(it.key) })
                                    .dedupeByThreadRoot()
                                    .dedupeClusterContext()
                                    .dedupeByLeafPost()
                                    .toImmutableList()
                            postInteractionsCache.seed(page.feedItems.allPosts())
                            setState {
                                copy(
                                    feedItems = appended,
                                    nextCursor = page.nextCursor,
                                    endReached = page.nextCursor == null,
                                    loadStatus = FeedLoadStatus.Idle,
                                )
                            }
                        }.onFailure { throwable ->
                            // Preserve feedItems AND cursor on append failure so the user
                            // can retry from the same page boundary.
                            setState { copy(loadStatus = FeedLoadStatus.Idle) }
                            sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                        }
                }
        }

        // Maps the bound feed to its analytics bucket. Generators split on the
        // default Discover feed vs any other (custom) generator; boundFeedUri
        // is used only for this comparison and is never logged.
        private fun FeedKind.toAnalyticsFeedType(uri: String): FeedType =
            when (this) {
                FeedKind.Following -> FeedType.Following
                FeedKind.List -> FeedType.List
                FeedKind.Generator ->
                    if (uri == PinnedFeedsRepository.DISCOVER_FEED_URI) FeedType.Discover else FeedType.Custom
            }

        private fun applyInitialPage(page: TimelinePage) {
            // view_feed fires once per successful initial load of the feed
            // surface (not on refresh or pagination). feed_type is derived
            // from the feed this VM is bound to, so Discover / lists / custom
            // generators report distinctly now that the surface can switch
            // feeds. No feed URI or other identifying value is ever attached.
            analytics.log(ViewFeed(boundKind.toAnalyticsFeedType(boundFeedUri)))
            val deduped =
                page.feedItems
                    .dedupeByThreadRoot()
                    .dedupeClusterContext()
                    .dedupeByLeafPost()
                    .toImmutableList()
            postInteractionsCache.seed(deduped.allPosts())
            setState {
                copy(
                    feedItems = deduped,
                    nextCursor = page.nextCursor,
                    endReached = page.nextCursor == null || deduped.isEmpty(),
                    loadStatus = FeedLoadStatus.Idle,
                )
            }
        }

        /**
         * Collapse a repository throwable into the UI-resolvable category the
         * error screen renders.
         *
         * The feed-generator cases discriminate on [XrpcError.errorName], the
         * parsed `error` field of the XRPC error body — never on the rendered
         * `message`, which is human-facing prose the appview is free to
         * reword. Ordering matters: `XrpcError` is a `RuntimeException`, so it
         * would otherwise fall through to [FeedError.Unknown].
         *
         * A blank upstream message normalises to null so the screen has one
         * "nothing to quote" signal instead of two — otherwise a `"message":
         * ""` body would render a dangling "Message from server:" label.
         */
        private fun Throwable.toFeedError(): FeedError =
            when (this) {
                is NoSessionException -> FeedError.Unauthenticated
                is IOException -> FeedError.Network
                is XrpcError ->
                    when (errorName) {
                        UPSTREAM_FAILURE, UPSTREAM_TIMEOUT ->
                            FeedError.FeedOffline(serverMessage = errorMessage?.ifBlank { null })
                        UNKNOWN_FEED -> FeedError.FeedNotFound
                        else -> FeedError.Unknown(cause = message)
                    }
                else -> FeedError.Unknown(cause = message)
            }

        private companion object {
            /** Appview could not reach the feed generator at all. */
            const val UPSTREAM_FAILURE = "UpstreamFailure"

            /** Generator answered too slowly for the appview's budget. */
            const val UPSTREAM_TIMEOUT = "UpstreamTimeout"

            /** No generator record exists at the requested AT URI. */
            const val UNKNOWN_FEED = "UnknownFeed"
        }
    }

/**
 * Remove every [FeedItemUi] from the list whose "primary" post is authored
 * by [did]. Used for the optimistic mute-hide in [FeedViewModel].
 *
 * Removal semantics per variant:
 * - [FeedItemUi.Single] — removed when its post's `author.did` matches.
 * - [FeedItemUi.ReplyCluster] — removed when the **leaf** post's author
 *   matches. The leaf is the "followed" post; root/parent are context.
 *   Keeping a cluster where the leaf is by someone else (replying to the
 *   muted author) is intentional — the viewer follows that other person.
 *   Muting hides the muted author's own posts and threads, but a
 *   non-muted user's reply to (or quote of) a muted author is kept as
 *   conversation context, matching the server's next-fetch behavior where
 *   muted accounts appear only as thread context rather than top-level
 *   feed entries.
 * - [FeedItemUi.SelfThreadChain] — removed when any post's author matches
 *   (all posts in a chain share the same author by construction).
 * - Tombstone variants carry no author — always kept.
 */
private fun ImmutableList<FeedItemUi>.removeItemsByAuthor(did: String): ImmutableList<FeedItemUi> =
    filter { item ->
        when (item) {
            is FeedItemUi.Single -> item.post.author.did != did
            is FeedItemUi.ReplyCluster -> item.leaf.author.did != did
            is FeedItemUi.SelfThreadChain -> item.posts.none { it.author.did == did }
            is FeedItemUi.Blocked, is FeedItemUi.NotFound -> true
        }
    }.toImmutableList()

/**
 * Apply [transform] to the [ViewerStateUi] of every post authored by [did]
 * across all [FeedItemUi] variants. Used for the optimistic unmute flip in
 * [FeedViewModel].
 *
 * Returns the same instance for any item none of whose contained posts are
 * authored by [did], preserving reference equality so LazyColumn can skip
 * recomposition for unchanged items.
 */
private fun ImmutableList<FeedItemUi>.updateViewerStateByAuthor(
    did: String,
    transform: (ViewerStateUi) -> ViewerStateUi,
): ImmutableList<FeedItemUi> =
    map { item ->
        when (item) {
            is FeedItemUi.Single -> {
                if (item.post.author.did != did) {
                    item
                } else {
                    item.copy(post = item.post.copy(viewer = transform(item.post.viewer)))
                }
            }
            is FeedItemUi.ReplyCluster -> {
                val root =
                    if (item.root.author.did == did) item.root.copy(viewer = transform(item.root.viewer)) else item.root
                val parent =
                    if (item.parent.author.did == did) item.parent.copy(viewer = transform(item.parent.viewer)) else item.parent
                val leaf =
                    if (item.leaf.author.did == did) item.leaf.copy(viewer = transform(item.leaf.viewer)) else item.leaf
                if (root === item.root && parent === item.parent && leaf === item.leaf) {
                    item
                } else {
                    item.copy(root = root, parent = parent, leaf = leaf)
                }
            }
            is FeedItemUi.SelfThreadChain -> {
                var changed = false
                val updatedPosts =
                    item.posts
                        .map { p ->
                            if (p.author.did == did) {
                                changed = true
                                p.copy(viewer = transform(p.viewer))
                            } else {
                                p
                            }
                        }.toImmutableList()
                if (changed) item.copy(posts = updatedPosts) else item
            }
            // Tombstones carry no author state — pass through unchanged.
            is FeedItemUi.Blocked, is FeedItemUi.NotFound -> item
        }
    }.toImmutableList()

/**
 * Flatten every [PostUi] out of every [FeedItemUi] variant in the list.
 * Used to seed the [PostInteractionsCache] with the full set of posts
 * visible after a page load, including posts inside [FeedItemUi.ReplyCluster]
 * and [FeedItemUi.SelfThreadChain].
 */
private fun List<FeedItemUi>.allPosts(): List<PostUi> =
    flatMap { item ->
        when (item) {
            is FeedItemUi.Single -> listOf(item.post)
            is FeedItemUi.ReplyCluster -> listOf(item.root, item.parent, item.leaf)
            is FeedItemUi.SelfThreadChain -> item.posts
            // Tombstone variants carry no PostUi — nothing to seed into the
            // interactions cache, and nothing to project for the video
            // coordinator. The mapper doesn't emit these today; they will
            // surface only via client-side optimism under oftc.4 (block).
            is FeedItemUi.Blocked, is FeedItemUi.NotFound -> emptyList()
        }
    }

/**
 * Project a [PostInteractionsCache][net.kikin.nubecita.core.postinteractions.PostInteractionsCache]
 * snapshot onto the feed item list. Replaces interaction fields (like /
 * repost counts and viewer flags) on every [PostUi] whose id appears in
 * [map], across all variants — [FeedItemUi.Single], [FeedItemUi.ReplyCluster],
 * and [FeedItemUi.SelfThreadChain].
 *
 * Returns the same instance for any item whose contained posts are all
 * absent from [map], preserving reference equality so LazyColumn can skip
 * recomposition for unchanged items.
 */
private fun ImmutableList<FeedItemUi>.applyInteractions(
    interactionMap: PersistentMap<String, PostInteractionState>,
): ImmutableList<FeedItemUi> =
    // One pass. The deletion check reads `leafId` — the post this item occupies
    // a timeline slot for. Deleting it removes the item; deleting an ancestor
    // shown only as context leaves the reply standing, which is what the reader
    // still wants to see. Such an ancestor disappears on the next fetch.
    //
    // This read `key` while `key` WAS the leaf's id. The grouped keys are
    // namespaced now (D12), so `interactionMap[item.key]` would look up
    // "cluster:at://…" against a map of post URIs, never match, and silently
    // stop removing deleted posts from the feed — the trap the previous comment
    // here predicted.
    mapNotNull { item ->
        if (interactionMap[item.leafId]?.isDeleted == true) return@mapNotNull null
        when (item) {
            is FeedItemUi.Single -> {
                val state = interactionMap[item.post.id] ?: return@mapNotNull item
                item.copy(post = item.post.mergeInteractionState(state))
            }
            is FeedItemUi.ReplyCluster -> {
                val newRoot = interactionMap[item.root.id]?.let { item.root.mergeInteractionState(it) }
                val newParent = interactionMap[item.parent.id]?.let { item.parent.mergeInteractionState(it) }
                val newLeaf = interactionMap[item.leaf.id]?.let { item.leaf.mergeInteractionState(it) }
                if (newRoot == null && newParent == null && newLeaf == null) {
                    item
                } else {
                    item.copy(
                        root = newRoot ?: item.root,
                        parent = newParent ?: item.parent,
                        leaf = newLeaf ?: item.leaf,
                    )
                }
            }
            is FeedItemUi.SelfThreadChain -> {
                var changed = false
                val mergedPosts =
                    item.posts
                        .map { p ->
                            val state = interactionMap[p.id]
                            if (state != null) {
                                changed = true
                                p.mergeInteractionState(state)
                            } else {
                                p
                            }
                        }.toImmutableList()
                if (changed) item.copy(posts = mergedPosts) else item
            }
            // Tombstones have no interaction state to merge — they carry no
            // PostUi, no like/repost counters. Pass through unchanged so the
            // map preserves referential equality for LazyColumn skip-recompose.
            is FeedItemUi.Blocked, is FeedItemUi.NotFound -> item
        }
    }.toImmutableList()
