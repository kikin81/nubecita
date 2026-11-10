package net.kikin.nubecita.feature.feed.impl

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.MuteRepository
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.FeedType
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.analytics.ViewFeed
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feedcache.CachedFeedRow
import net.kikin.nubecita.core.feedcache.FeedAnchor
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
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
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.OutgoingPostUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.feature.feed.impl.translation.FeedTranslationCoordinator
import net.kikin.nubecita.feature.search.api.Hashtag
import java.io.IOException
import javax.inject.Inject
import net.kikin.nubecita.core.feedcache.FeedType as CacheFeedType

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel
    @Inject
    constructor(
        private val feedRepository: FeedRepository,
        private val sessionStateProvider: SessionStateProvider,
        private val postInteractionsCache: PostInteractionsCache,
        val sharedVideoPlayer: SharedVideoPlayer,
        private val analytics: AnalyticsClient,
        private val muteRepository: MuteRepository,
        private val handler: PostInteractionHandler,
        private val outgoingPostStore: OutgoingPostStore,
        /**
         * Resolves per-post translation for the screen's Compose layer.
         *
         * Exposed rather than consumed, like [sharedVideoPlayer] above: the
         * state it drives is scroll-scoped and snapshot-backed, so it belongs
         * to the composition, not to `UiState`. The ViewModel is only the
         * injection point — it never reads or writes translation state.
         */
        val translationCoordinator: FeedTranslationCoordinator,
        autoplayPolicy: AutoplayPolicy,
    ) : MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState()),
        PostInteractionHandler by handler {
        /**
         * The feed this VM renders, bound via [FeedEvent.Bind]. Defaults to the
         * Following timeline so a pane that never receives an explicit bind
         * (the pre-host `FeedScreen`) keeps working.
         */
        private val binding = MutableStateFlow(FeedBinding(FeedKind.Following, ""))

        /**
         * Authors the viewer just muted, hidden from the list before the server
         * has confirmed — and put back if it refuses.
         *
         * A set rather than an edited list because `PagingData` is immutable
         * and re-derived on every load: there is nothing to edit in place, and
         * a mutation applied to one generation would vanish on the next (D21).
         */
        private val optimisticMutes = MutableStateFlow(persistentSetOf<String>())

        /** The mirror of [optimisticMutes] for an unmute, which flips a flag rather than hiding a row. */
        private val optimisticUnmutes = MutableStateFlow(persistentSetOf<String>())

        /** Which binding has already logged `view_feed`, so a re-bind to the same feed does not log twice. */
        private var loggedViewFeedFor: FeedBinding? = null

        /**
         * Seam positions whose fill is in flight. A plain set because every
         * touch happens on the main dispatcher, where `handleEvent` and the
         * launched continuation both run.
         */
        private val seamFillsInFlight = mutableSetOf<Int>()

        /**
         * The place the screen should scroll to once the first page is on
         * screen, or `null` when there is nothing to restore.
         *
         * Seeding the `Pager` loads the right rows but does not position the
         * list: Paging decides where the anchored row lands in the loaded
         * window, and the reader's pixel offset within that row is not
         * Paging's business at all. So the screen scrolls, once, and clears
         * this — and the ViewModel never touches `LazyListState` (D9).
         */
        val restoreTarget = MutableStateFlow<FeedAnchor?>(null)

        /**
         * Where the next `Pager` starts. Each [FeedEvent.ShowNewest] is a new
         * [PagerStart.Newest] value, so a second tap rebuilds again rather than
         * being swallowed as "already at the top".
         */
        private val pagerStart = MutableStateFlow<PagerStart>(PagerStart.SavedPlace)

        /**
         * True once a [FeedEvent.ShowNewest] has caught up and reopened the
         * pager at the top; the screen scrolls to it and clears this with
         * [FeedEvent.NewestShown]. The same publish-then-consume shape as
         * [restoreTarget], and for the same reason: the VM never touches
         * `LazyListState` (D9).
         *
         * Raised only after the catch-up has written. Scrolling any earlier
         * lands on a row that the catch-up then pushes down, and the list
         * follows that row's key rather than staying at the top.
         */
        val scrollToNewest = MutableStateFlow(false)

        /** The "show newest" in flight, so a second tap does not start another. */
        private var showNewestJob: Job? = null

        /** The partition this VM reads, once an account is known. */
        private val feedKey: Flow<FeedKey> =
            combine(sessionStateProvider.state, binding) { session, bound ->
                (session as? SessionState.SignedIn)?.did?.let { bound.toFeedKey(it) }
            }.filterNotNull()
                .distinctUntilChanged()

        /**
         * The paged feed, read from `:core:feed-cache` (D1).
         *
         * `cachedIn` sits directly above the transforms and below the `Pager`:
         * the pages are held for this scope, so a transform re-running — which
         * it does on every emission of the three flows below — reshapes what is
         * already loaded instead of refetching it.
         *
         * The three transforms are the optimistic mechanisms that used to edit
         * a list in `UiState`. Each is now fed by its own flow, because
         * `PagingData` is immutable: an in-place edit has nowhere to live and
         * would be dropped by the next generation anyway (D21).
         */
        val feed: Flow<PagingData<CachedFeedRow>> =
            combine(feedKey, pagerStart, ::Pair)
                .flatMapLatest { (key, start) ->
                    // Read before the Pager is built, so the first page loaded
                    // is the one the reader was in rather than the top. A
                    // dead anchor resolves downward inside the repository
                    // (D11); `null` just means "open at the top".
                    val anchor =
                        when (start) {
                            PagerStart.SavedPlace -> feedRepository.anchor(key)
                            is PagerStart.Newest -> null
                        }
                    restoreTarget.value = anchor
                    feedRepository.pagedFeed(key, initialPosition = anchor?.position)
                }.cachedIn(viewModelScope)
                .withOptimisticState(
                    mutedAuthors = optimisticMutes,
                    unmutedAuthors = optimisticUnmutes,
                    interactions = postInteractionsCache.state,
                )

        /**
         * The faces on the new-posts pill, or empty when there is nothing to
         * tell the reader about.
         *
         * Gated on the feed supporting a bounded catch-up rather than on a
         * hardcoded list of kinds: a pill means "entries arrived above you",
         * and only a feed that can prepend has those. A generator refreshes on
         * open instead (D14), so its reader is already at the newest content.
         */
        val newPosts: StateFlow<ImmutableList<AuthorUi>> =
            feedKey
                .flatMapLatest { key ->
                    if (key.feedType.supportsSince) feedRepository.newPostAuthors(key) else flowOf(persistentListOf())
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), persistentListOf())

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

            // Top-level posts the user just submitted, rendered above the
            // Following timeline. Whether a given one is still *needed* depends
            // on what the loaded pages hold, which only the screen can see now
            // — it filters against the snapshot (see `notYetInFeed`).
            combine(outgoingPostStore.posts, binding) { outgoing, bound -> pendingOutgoing(outgoing, bound.kind) }
                .distinctUntilChanged()
                .onEach { pending -> setState { copy(outgoingPosts = pending) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: FeedEvent) {
            when (event) {
                is FeedEvent.Bind -> bind(event.feedUri, event.kind, event.surface)
                FeedEvent.Refresh -> refresh()
                is FeedEvent.FillSeam -> fillSeam(event.position)
                is FeedEvent.ItemsLoaded -> onItemsLoaded(event.posts)
                is FeedEvent.AnchorChanged -> saveAnchor(event.itemKey, event.offset)
                FeedEvent.ShowNewest -> showNewest()
                FeedEvent.NewestShown -> scrollToNewest.value = false
                FeedEvent.RestoreConsumed -> restoreTarget.value = null
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
         * list changes and a rollback on failure, which stay in this VM rather
         * than in the shared [PostInteractionHandler]. All other overflow
         * actions forward to the injected [handler].
         */
        override fun onOverflowAction(
            post: PostUi,
            action: PostOverflowAction,
        ) {
            when (action) {
                PostOverflowAction.MuteAuthor -> {
                    val authorDid = post.author.did
                    optimisticMutes.value = optimisticMutes.value.add(authorDid)
                    viewModelScope.launch {
                        muteRepository
                            .muteActor(authorDid)
                            .onFailure {
                                // Rollback is removing the DID, not restoring a
                                // saved list: the rows were never gone, only
                                // filtered, so they come back where they were.
                                optimisticMutes.value = optimisticMutes.value.remove(authorDid)
                                sendEffect(FeedEffect.ShowError(it.toFeedError()))
                            }
                    }
                }
                PostOverflowAction.UnmuteAuthor -> {
                    val authorDid = post.author.did
                    optimisticUnmutes.value = optimisticUnmutes.value.add(authorDid)
                    viewModelScope.launch {
                        muteRepository
                            .unmuteActor(authorDid)
                            .onFailure {
                                optimisticUnmutes.value = optimisticUnmutes.value.remove(authorDid)
                                sendEffect(FeedEffect.ShowError(it.toFeedError()))
                            }
                    }
                }
                else -> handler.onOverflowAction(post, action)
            }
        }

        /**
         * Remember the `(feedUri, kind)` this VM renders.
         *
         * Re-binding to the same pair must be a no-op: the host re-emits `Bind`
         * from a `LaunchedEffect(feedUri)` on every recomposition of the pane,
         * and re-emitting the binding would restart the `Pager` and throw away
         * the reader's position — the opposite of this feature's point.
         *
         * Switching feeds drops the optimistic sets. They describe rows in the
         * feed being left, and a DID muted in one feed is already muted
         * server-side for the next one.
         */
        private fun bind(
            feedUri: String,
            kind: FeedKind,
            surface: PostSurface,
        ) {
            // surface always tracks the latest binding (cheap) even when the
            // feedUri/kind pair is unchanged — re-binding drives analytics
            // surface attribution for like/repost/share events via the handler.
            handler.bind(surface, viewModelScope)
            val next = FeedBinding(kind, feedUri)
            if (binding.value == next) return
            optimisticMutes.value = persistentSetOf()
            optimisticUnmutes.value = persistentSetOf()
            // A "show newest" belonged to the feed being left; the next one
            // opens at its own saved place.
            pagerStart.value = PagerStart.SavedPlace
            binding.value = next
        }

        /**
         * The reader asked for a clean timeline (D8). Paging drives the fetch
         * itself — the screen calls `refresh()` on the items — so what is left
         * here is the state that is not Paging's: the optimistic sets, which
         * the refetched rows already reflect server-side.
         */
        private fun refresh() {
            optimisticMutes.value = persistentSetOf()
            optimisticUnmutes.value = persistentSetOf()
            // And the reader's place: they asked for a clean timeline, so
            // restoring them into the old one would be the opposite of the
            // gesture — and the row the anchor named goes with the partition.
            restoreTarget.value = null
            val key = currentFeedKey() ?: return
            viewModelScope.launch { feedRepository.clearAnchor(key) }
        }

        /**
         * Reconcile the interactions cache with a newly loaded set of posts,
         * and log `view_feed` the first time a binding shows content.
         *
         * Dispatched by the screen, because the "pages changed" signal lives in
         * `LazyPagingItems` and a `ViewModel` cannot see it. Seeding is not
         * optional: it is how wire data that is NEWER than the cache (a like
         * made on another device) replaces a cached overlay, and without it an
         * overlay would pin a stale count for as long as the process lives.
         */
        private fun onItemsLoaded(posts: List<PostUi>) {
            if (posts.isEmpty()) return
            postInteractionsCache.seed(posts)
            val bound = binding.value
            if (loggedViewFeedFor != bound) {
                loggedViewFeedFor = bound
                // Once per successful first load of a feed surface — not on
                // refresh, and not per appended page. feed_type is derived from
                // the binding, so Discover / lists / custom generators report
                // distinctly; no feed URI is ever attached.
                analytics.log(ViewFeed(bound.kind.toAnalyticsFeedType(bound.feedUri)))
            }
        }

        /**
         * Persist where the reader stopped.
         *
         * Fire-and-forget on the VM's scope rather than awaited: this runs
         * every time a scroll settles, and the screen has nothing to do with
         * the result. The repository drops a write whose key no longer has a
         * row — a refresh landing between the settle and the write — so a
         * stale place is never stored.
         */
        private fun saveAnchor(
            itemKey: String,
            offset: Int,
        ) {
            val key = currentFeedKey() ?: return
            viewModelScope.launch { feedRepository.saveAnchor(key, itemKey, offset) }
        }

        /**
         * Fill the gap the seam marks.
         *
         * The same bounded catch-up the background worker runs (D5), so a fill
         * can neither run away nor produce a different timeline than an
         * unattended prepend would. [position] guards the repeat: the row
         * re-composes freely and may ask again, but only one fill per seam is
         * ever in flight, and a failure clears the guard so the next crossing
         * can retry.
         */
        private fun fillSeam(position: Int) {
            val key = currentFeedKey() ?: return
            if (!seamFillsInFlight.add(position)) return
            setState { copy(isFillingSeam = true) }
            viewModelScope.launch {
                val outcome = feedRepository.catchUp(key)
                seamFillsInFlight.remove(position)
                setState { copy(isFillingSeam = seamFillsInFlight.isNotEmpty()) }
                outcome.onFailure { sendEffect(FeedEffect.ShowError(it.toFeedError())) }
            }
        }

        /**
         * Go to the newest entry: catch up, rebuild the pager at the top of
         * the partition, acknowledge what was new, and tell the screen to
         * scroll there.
         *
         * Only for a feed that can catch up. A generator feed refreshes on
         * open (D14), keeps no place and raises no pill, so its loaded window
         * already starts at the top and the screen's scroll is enough.
         *
         * The catch-up comes FIRST. Reopening at the top of a stale partition
         * and letting Paging's PREPEND fetch afterwards writes the newest page
         * above the reader, and the list keeps the row it was showing — so the
         * reader lands under what they asked to see. One call fetches the
         * newest page; the rest of the range fills below it through PREPEND
         * and the seam, out of the reader's way. `FeedCatchUp` serializes the
         * two callers.
         *
         * A failed fetch still reopens at the newest entry held, and says why.
         */
        private fun showNewest() {
            val key = currentFeedKey() ?: return
            if (!key.feedType.supportsSince) return
            if (showNewestJob?.isActive == true) return
            // A pending restore would scroll the reader straight back down.
            restoreTarget.value = null
            showNewestJob =
                viewModelScope.launch {
                    feedRepository.catchUp(key).onFailure { sendEffect(FeedEffect.ShowError(it.toFeedError())) }
                    pagerStart.value =
                        PagerStart.Newest(request = (pagerStart.value as? PagerStart.Newest)?.request?.plus(1) ?: 0)
                    // After the catch-up, so what it just fetched counts as seen.
                    feedRepository.dismissNewPosts(key)
                    scrollToNewest.value = true
                }
        }

        private fun currentFeedKey(): FeedKey? =
            (sessionStateProvider.state.value as? SessionState.SignedIn)
                ?.did
                ?.let { binding.value.toFeedKey(it) }

        private fun pendingOutgoing(
            outgoing: List<OutgoingPost>,
            kind: FeedKind,
        ): ImmutableList<OutgoingPostUi> {
            if (kind != FeedKind.Following || outgoing.isEmpty()) return persistentListOf()
            return outgoing
                .filter { it.replyToUri == null }
                .map { it.toUi() }
                .toImmutableList()
        }

        // Maps the bound feed to its analytics bucket. Generators split on the
        // default Discover feed vs any other (custom) generator; feedUri is
        // used only for this comparison and is never logged.
        private fun FeedKind.toAnalyticsFeedType(uri: String): FeedType =
            when (this) {
                FeedKind.Following -> FeedType.Following
                FeedKind.List -> FeedType.List
                FeedKind.Generator ->
                    if (uri == PinnedFeedsRepository.DISCOVER_FEED_URI) FeedType.Discover else FeedType.Custom
            }
    }

/**
 * How long the pill's query stays warm after the screen stops collecting, so a
 * tab switch and back does not re-query Room.
 */
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** What a [FeedViewModel] is bound to: one cached partition, once a DID is known. */
internal data class FeedBinding(
    val kind: FeedKind,
    val feedUri: String,
) {
    /**
     * The cache partition this binding reads.
     *
     * Discover is modelled distinctly from a custom generator so the refresh
     * scheduler can treat the always-present feed specially — the same split
     * the analytics bucket makes.
     */
    fun toFeedKey(accountDid: String): FeedKey =
        when (kind) {
            FeedKind.Following -> FeedKey.following(accountDid)
            FeedKind.List -> FeedKey(accountDid, CacheFeedType.LIST, feedUri)
            FeedKind.Generator ->
                FeedKey(
                    accountDid,
                    if (feedUri == PinnedFeedsRepository.DISCOVER_FEED_URI) CacheFeedType.DISCOVER else CacheFeedType.CUSTOM,
                    feedUri,
                )
        }
}

/**
 * Collapse a load failure into the UI-resolvable category the error screen
 * renders.
 *
 * The feed-generator cases discriminate on [XrpcError.errorName], the parsed
 * `error` field of the XRPC error body — never on the rendered `message`, which
 * is human-facing prose the appview is free to reword. Ordering matters:
 * `XrpcError` is a `RuntimeException`, so it would otherwise fall through to
 * [FeedError.Unknown].
 *
 * A blank upstream message normalises to null so the screen has one "nothing to
 * quote" signal instead of two — otherwise a `"message": ""` body would render a
 * dangling "Message from server:" label.
 *
 * Top-level because the screen reads it too now: a load failure arrives as
 * Paging's `LoadState.Error`, not as an effect from the VM.
 */
internal fun Throwable.toFeedError(): FeedError =
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

/** Appview could not reach the feed generator at all. */
private const val UPSTREAM_FAILURE = "UpstreamFailure"

/** Generator answered too slowly for the appview's budget. */
private const val UPSTREAM_TIMEOUT = "UpstreamTimeout"

/** No generator record exists at the requested AT URI. */
private const val UNKNOWN_FEED = "UnknownFeed"

/**
 * Applies the three optimistic mechanisms to a paged feed.
 *
 * Extracted from the ViewModel so the composition — which transform runs in
 * which order — is testable on its own. A test can feed it a
 * `PagingData.from(…)` and read the result with `asSnapshot`, which it cannot
 * do through `FeedViewModel.feed`: that flow is `cachedIn(viewModelScope)`, and
 * a scope the test cannot complete leaves `asSnapshot` waiting forever.
 *
 * Order matters. The deleted-post filter runs before the overlay, so a deleted
 * post leaves the list rather than rendering with updated counters, and the
 * mute filter runs first so hidden rows are not transformed at all.
 *
 * Each transform short-circuits on an empty input, which is the common case:
 * nothing muted and nothing tapped means the pages pass straight through,
 * preserving item identity so the list can skip recomposition.
 */
internal fun Flow<PagingData<CachedFeedRow>>.withOptimisticState(
    mutedAuthors: Flow<Set<String>>,
    unmutedAuthors: Flow<Set<String>>,
    interactions: Flow<PersistentMap<String, PostInteractionState>>,
): Flow<PagingData<CachedFeedRow>> =
    combine(this, mutedAuthors, unmutedAuthors, interactions) { data, muted, unmuted, snapshot ->
        var transformed = data
        // A seam is not authored by anyone and carries no counters, so every
        // transform below passes it through untouched. Hiding it along with a
        // muted author's posts would leave the timeline with the bare visual
        // break the seam exists to prevent.
        if (muted.isNotEmpty()) {
            transformed = transformed.filter { row -> row !is CachedFeedRow.Item || !row.item.isAuthoredBy(muted) }
        }
        if (unmuted.isNotEmpty()) {
            transformed = transformed.map { row -> row.mapItem { it.clearViewerMuteFor(unmuted) } }
        }
        if (snapshot.isNotEmpty()) {
            transformed =
                transformed
                    .filter { row -> row !is CachedFeedRow.Item || snapshot[row.item.leafId]?.isDeleted != true }
                    .map { row -> row.mapItem { it.applyInteractions(snapshot) } }
        }
        transformed
    }

/** Applies [transform] to a rendered item, leaving a seam alone. */
private inline fun CachedFeedRow.mapItem(transform: (FeedItemUi) -> FeedItemUi): CachedFeedRow =
    when (this) {
        is CachedFeedRow.Item -> {
            val mapped = transform(item)
            if (mapped === item) this else CachedFeedRow.Item(mapped)
        }
        is CachedFeedRow.Seam -> this
    }

/**
 * Whether this item's "primary" post is authored by one of [dids] — the
 * predicate behind the optimistic mute-hide.
 *
 * Per variant:
 * - [FeedItemUi.Single] — its post's author.
 * - [FeedItemUi.ReplyCluster] — the **leaf's** author. The leaf is the
 *   "followed" post; root and parent are context. Keeping a cluster whose leaf
 *   is by someone else replying to the muted author is intentional — the viewer
 *   follows that other person, and it matches the server's next fetch, where a
 *   muted account appears only as thread context.
 * - [FeedItemUi.SelfThreadChain] — any post's author (a chain is same-author by
 *   construction).
 * - Tombstones carry no author, so they are never hidden.
 */
internal fun FeedItemUi.isAuthoredBy(dids: Set<String>): Boolean =
    when (this) {
        is FeedItemUi.Single -> post.author.did in dids
        is FeedItemUi.ReplyCluster -> leaf.author.did in dids
        is FeedItemUi.SelfThreadChain -> posts.any { it.author.did in dids }
        is FeedItemUi.Blocked, is FeedItemUi.NotFound -> false
    }

/**
 * Clear the viewer's muted-author flag on every post authored by one of [dids]
 * — the optimistic unmute, which flips a flag rather than hiding a row.
 *
 * Returns the same instance when nothing matched, so `LazyColumn` can skip
 * recomposition for an unchanged item.
 */
internal fun FeedItemUi.clearViewerMuteFor(dids: Set<String>): FeedItemUi = updateViewerStateByAuthor(dids) { it.copy(isAuthorMutedByViewer = false) }

private fun FeedItemUi.updateViewerStateByAuthor(
    dids: Set<String>,
    transform: (ViewerStateUi) -> ViewerStateUi,
): FeedItemUi =
    when (this) {
        is FeedItemUi.Single ->
            if (post.author.did !in dids) this else copy(post = post.copy(viewer = transform(post.viewer)))
        is FeedItemUi.ReplyCluster -> {
            val newRoot = if (root.author.did in dids) root.copy(viewer = transform(root.viewer)) else root
            val newParent = if (parent.author.did in dids) parent.copy(viewer = transform(parent.viewer)) else parent
            val newLeaf = if (leaf.author.did in dids) leaf.copy(viewer = transform(leaf.viewer)) else leaf
            if (newRoot === root && newParent === parent && newLeaf === leaf) {
                this
            } else {
                copy(root = newRoot, parent = newParent, leaf = newLeaf)
            }
        }
        is FeedItemUi.SelfThreadChain -> {
            var changed = false
            val updated =
                posts
                    .map { p ->
                        if (p.author.did in dids) {
                            changed = true
                            p.copy(viewer = transform(p.viewer))
                        } else {
                            p
                        }
                    }.toImmutableList()
            if (changed) copy(posts = updated) else this
        }
        // Tombstones carry no author state — pass through unchanged.
        is FeedItemUi.Blocked, is FeedItemUi.NotFound -> this
    }

/**
 * Overlay a [PostInteractionsCache] snapshot onto one item: like / repost
 * counts and viewer flags for every contained [PostUi] that appears in [map].
 *
 * Returns the same instance when none of its posts are in the map, preserving
 * reference equality so `LazyColumn` can skip recomposition.
 *
 * Removing a deleted item is NOT here — that is a `PagingData.filter` on
 * [FeedItemUi.leafId], because dropping an item is a list operation and this is
 * a per-item one.
 */
internal fun FeedItemUi.applyInteractions(map: PersistentMap<String, PostInteractionState>): FeedItemUi =
    when (this) {
        is FeedItemUi.Single -> {
            val state = map[post.id]
            if (state == null) this else copy(post = post.mergeInteractionState(state))
        }
        is FeedItemUi.ReplyCluster -> {
            val newRoot = map[root.id]?.let { root.mergeInteractionState(it) }
            val newParent = map[parent.id]?.let { parent.mergeInteractionState(it) }
            val newLeaf = map[leaf.id]?.let { leaf.mergeInteractionState(it) }
            if (newRoot == null && newParent == null && newLeaf == null) {
                this
            } else {
                copy(root = newRoot ?: root, parent = newParent ?: parent, leaf = newLeaf ?: leaf)
            }
        }
        is FeedItemUi.SelfThreadChain -> {
            var changed = false
            val merged =
                posts
                    .map { p ->
                        val state = map[p.id]
                        if (state != null) {
                            changed = true
                            p.mergeInteractionState(state)
                        } else {
                            p
                        }
                    }.toImmutableList()
            if (changed) copy(posts = merged) else this
        }
        // Tombstones carry no PostUi — no counters to merge.
        is FeedItemUi.Blocked, is FeedItemUi.NotFound -> this
    }

/**
 * Every [PostUi] inside this item, which is what the interactions cache is
 * seeded with — a post inside a cluster or a chain needs reconciling too.
 */
internal fun List<CachedFeedRow>.allPosts(): List<PostUi> =
    filterIsInstance<CachedFeedRow.Item>().map { it.item }.flatMap { item ->
        when (item) {
            is FeedItemUi.Single -> listOf(item.post)
            is FeedItemUi.ReplyCluster -> listOf(item.root, item.parent, item.leaf)
            is FeedItemUi.SelfThreadChain -> item.posts
            is FeedItemUi.Blocked, is FeedItemUi.NotFound -> emptyList()
        }
    }

/**
 * The outgoing posts not yet present in the loaded pages.
 *
 * Filtered against every post the pages hold, not just each item's leaf: the
 * user's own post can land inside a self-thread chain or as a cluster's leaf,
 * and showing the optimistic row beside the real one would read as a double
 * post.
 *
 * Evaluated by the screen rather than the VM because the loaded set lives in
 * `LazyPagingItems`. Pure, so it is unit-tested rather than asserted through a
 * Compose test.
 */
internal fun ImmutableList<OutgoingPostUi>.notYetInFeed(loaded: List<CachedFeedRow>): ImmutableList<OutgoingPostUi> {
    if (isEmpty()) return this
    val present = loaded.allPosts().mapTo(HashSet()) { it.id }
    if (present.isEmpty()) return this
    return filterNot { it.post.id in present }.toImmutableList()
}

/** Where a feed's `Pager` starts. */
private sealed interface PagerStart {
    /** At the reader's saved place, or the top when there is none. */
    data object SavedPlace : PagerStart

    /** At the newest entry, asked for explicitly; [request] distinguishes repeat asks. */
    data class Newest(
        val request: Int,
    ) : PagerStart
}
