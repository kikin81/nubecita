package net.kikin.nubecita.feature.search.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.ActorAction
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.FeedAction
import net.kikin.nubecita.core.analytics.InteractActor
import net.kikin.nubecita.core.analytics.InteractFeed
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.postinteractions.FollowOutcome
import net.kikin.nubecita.core.postinteractions.FollowOverlay
import net.kikin.nubecita.core.postinteractions.FollowToggle
import net.kikin.nubecita.core.postinteractions.withFollowOverride
import net.kikin.nubecita.feature.search.impl.data.ExploreRepository
import net.kikin.nubecita.feature.search.impl.data.TrendLink
import net.kikin.nubecita.feature.search.impl.data.TrendUi
import net.kikin.nubecita.feature.search.impl.data.parseTrendLink
import net.kikin.nubecita.feature.search.impl.data.withFollowOverride
import javax.inject.Inject

/**
 * Presenter for the Search tab's Explore content — trending topics, suggested
 * accounts and suggested feeds with lazy per-feed preview strips.
 *
 * This is a PROJECTION of [ExploreRepository.content], which holds the data once per
 * process: a second instance of this ViewModel (the second pane on a wide window) shows
 * the same content and cannot disagree with the first. What the repository does not hold
 * is layered on here, on every projection:
 *  - the viewer's follow actions, from [FollowOverlay] (the one source of follow state,
 *    shared with the People tab);
 *  - which feeds are pinned, from [PinnedFeedsRepository.observePinnedFeeds] (the source
 *    of truth), plus this ViewModel's own pin taps until the stream confirms them.
 *
 * ## Section loading
 * [DiscoverEvent.OnAppear] asks the repository to load; it loads only the sections that
 * are not [DiscoverSectionStatus.Loaded] and shares an in-flight load with any other
 * caller. Sections are independent: one failing does not mask the others.
 * [DiscoverEvent.OnRefresh] forces a reload of every section.
 *
 * ## Follow toggle
 * [DiscoverEvent.OnFollowTapped] goes through the shared [FollowToggle]: optimistic
 * override, repository call, rollback on failure. Analytics ([InteractActor]) fires on
 * success; a failure emits [DiscoverEffect.ShowError]. Rapid taps on the same DID are dropped.
 *
 * ## Pin toggle
 * [DiscoverEvent.OnPinTapped] flips the pin optimistically, then calls
 * [PinnedFeedsRepository.pinFeed] / [PinnedFeedsRepository.unpinFeed]. Analytics
 * ([InteractFeed]) fires on success; on failure the flip is undone and
 * [DiscoverEffect.ShowError] is emitted. Rapid taps on the same URI are dropped.
 *
 * ## Trend tap
 * [DiscoverEvent.OnTrendTapped] opens a feed trend's feed (resolving a handle to a DID
 * first), searches a search trend's query, and otherwise — an unknown link, a starter
 * pack, or a handle that does not resolve — searches the trend's display name.
 *
 * ## Session-local account dismissal
 * [DiscoverEvent.OnAccountDismissed] asks the repository to drop the account, so every
 * instance agrees and a refresh does not resurface it.
 */
@HiltViewModel
internal class DiscoverViewModel
    @Inject
    constructor(
        private val exploreRepository: ExploreRepository,
        private val followToggle: FollowToggle,
        private val followOverlay: FollowOverlay,
        private val pinnedFeedsRepository: PinnedFeedsRepository,
        private val analytics: AnalyticsClient,
    ) : MviViewModel<DiscoverState, DiscoverEvent, DiscoverEffect>(DiscoverState()) {
        /** Latest pinned feed URIs from [PinnedFeedsRepository.observePinnedFeeds]. */
        private var currentPinnedUris: Set<String> = emptySet()

        /** This ViewModel's pin taps that the pinned-feeds stream has not confirmed yet (uri → pinned). */
        private val optimisticPins = mutableMapOf<String, Boolean>()

        /** In-flight pin/unpin jobs keyed by feed URI — drops rapid concurrent taps. */
        private val activePinJobs = mutableMapOf<String, Job>()

        /**
         * In-flight preview fetch jobs keyed by feed URI — synchronous guard against
         * duplicate fetches when the carousel's snapshotFlow re-emits on layout changes.
         */
        private val activePreviewJobs = mutableMapOf<String, Job>()

        /** Whether this ViewModel has been shown ([DiscoverEvent.OnAppear]) and so wants content. */
        private var appeared = false

        /** Whether the last projection saw every section Idle — one reload per transition to that. */
        private var wasAllIdle = false

        /** The handle resolution of the last feed-trend tap, while it is in flight. */
        private var trendTapJob: Job? = null

        /** The most-recently-launched section load (cancel-and-restart on refresh). */
        private var loadJob: Job? = null

        init {
            exploreRepository.content
                .onEach { project() }
                .launchIn(viewModelScope)
            followOverlay.overrides
                .onEach { project() }
                .launchIn(viewModelScope)
            pinnedFeedsRepository
                .observePinnedFeeds()
                .map { result -> result.feeds.map { it.uri }.toSet() }
                .onEach { uris ->
                    currentPinnedUris = uris
                    // The stream now says what a tap asked for: drop the tap's own say-so.
                    optimisticPins.entries.removeAll { (uri, pinned) -> (uri in uris) == pinned }
                    project()
                }.launchIn(viewModelScope)
        }

        override fun handleEvent(event: DiscoverEvent) {
            when (event) {
                DiscoverEvent.OnAppear -> onAppear()
                DiscoverEvent.OnRefresh -> onRefresh()
                is DiscoverEvent.OnFollowTapped -> onFollowTapped(event.did)
                is DiscoverEvent.OnPinTapped -> onPinTapped(event.uri)
                is DiscoverEvent.OnFeedCardVisible -> onFeedCardVisible(event.uri)
                is DiscoverEvent.OnAccountTapped ->
                    sendEffect(DiscoverEffect.NavigateToProfile(event.handle))
                is DiscoverEvent.OnFeedTapped ->
                    sendEffect(DiscoverEffect.NavigateToFeed(event.uri, event.displayName))
                is DiscoverEvent.OnAccountDismissed -> onAccountDismissed(event.did)
                is DiscoverEvent.OnTrendTapped -> onTrendTapped(event.trend)
                DiscoverEvent.OnTrendsExpansionToggled -> setState { copy(trendsExpanded = !trendsExpanded) }
            }
        }

        /**
         * Rebuilds the state from the repository's content with the follow overlay and the
         * pin state laid over it. Idempotent, so it runs after every source changes and
         * synchronously after this ViewModel's own optimistic writes.
         */
        private fun project() {
            val content = exploreRepository.content.value
            // The content went back to Idle (the account changed) under a screen that stays
            // composed: nothing will send OnAppear again, so ask for the content now.
            val allIdle =
                content.trendsStatus == DiscoverSectionStatus.Idle &&
                    content.feedsStatus == DiscoverSectionStatus.Idle &&
                    content.accountsStatus == DiscoverSectionStatus.Idle
            val reloadNeeded = allIdle && !wasAllIdle && appeared
            wasAllIdle = allIdle
            val overrides = followOverlay.current()
            setState {
                copy(
                    trends = content.trends,
                    trendsStatus = content.trendsStatus,
                    accounts = content.accounts.map { it.withFollowOverride(overrides) }.toImmutableList(),
                    accountsStatus = content.accountsStatus,
                    feeds =
                        content.feeds
                            .map { discoverFeed ->
                                val uri = discoverFeed.feed.uri
                                val pinned = optimisticPins[uri] ?: (uri in currentPinnedUris)
                                if (discoverFeed.feed.isPinned != pinned) {
                                    discoverFeed.copy(feed = discoverFeed.feed.copy(isPinned = pinned))
                                } else {
                                    discoverFeed
                                }
                            }.toImmutableList(),
                    feedsStatus = content.feedsStatus,
                )
            }
            if (reloadNeeded) startLoad(force = false)
        }

        // -------------------------------------------------------------------------
        // Section loading
        // -------------------------------------------------------------------------

        private fun onAppear() {
            appeared = true
            val state = uiState.value
            val needsLoad =
                state.trendsStatus.needsLoad() || state.accountsStatus.needsLoad() || state.feedsStatus.needsLoad()
            if (!needsLoad) return
            startLoad(force = false)
        }

        private fun onRefresh() = startLoad(force = true)

        private fun startLoad(force: Boolean) {
            loadJob?.cancel()
            loadJob = viewModelScope.launch { exploreRepository.load(force) }
        }

        // -------------------------------------------------------------------------
        // Follow toggle
        // -------------------------------------------------------------------------

        private fun onFollowTapped(did: String) {
            val account = uiState.value.accounts.find { it.did == did } ?: return
            val started =
                followToggle.toggle(
                    scope = viewModelScope,
                    did = did,
                    isFollowing = account.isFollowing,
                    followUri = account.followUri,
                ) { outcome ->
                    when (outcome) {
                        FollowOutcome.Followed ->
                            analytics.log(InteractActor(ActorAction.Follow, PostSurface.Explore))
                        FollowOutcome.Unfollowed ->
                            analytics.log(InteractActor(ActorAction.Unfollow, PostSurface.Explore))
                        FollowOutcome.FollowFailed -> sendEffect(DiscoverEffect.ShowError("Failed to follow"))
                        FollowOutcome.UnfollowFailed -> sendEffect(DiscoverEffect.ShowError("Failed to unfollow"))
                    }
                }
            // The optimistic override is written already; show it now rather than when
            // the overlay's collector next runs.
            if (started) project()
        }

        // -------------------------------------------------------------------------
        // Pin toggle
        // -------------------------------------------------------------------------

        private fun onPinTapped(uri: String) {
            if (activePinJobs[uri]?.isActive == true) return

            val feedUi = uiState.value.feeds.find { it.feed.uri == uri } ?: return
            val wasPinned = feedUi.feed.isPinned

            optimisticPins[uri] = !wasPinned
            project()

            activePinJobs[uri] =
                viewModelScope.launch {
                    val result =
                        if (wasPinned) {
                            pinnedFeedsRepository.unpinFeed(uri)
                        } else {
                            pinnedFeedsRepository.pinFeed(uri)
                        }
                    result
                        .onSuccess {
                            analytics.log(
                                InteractFeed(
                                    action = if (wasPinned) FeedAction.Unpin else FeedAction.Pin,
                                    surface = PostSurface.Explore,
                                ),
                            )
                        }.onFailure {
                            optimisticPins.remove(uri)
                            project()
                            sendEffect(DiscoverEffect.ShowError("Failed to ${if (wasPinned) "unpin" else "pin"} feed"))
                        }
                }
        }

        // -------------------------------------------------------------------------
        // Lazy preview
        // -------------------------------------------------------------------------

        private fun onFeedCardVisible(uri: String) {
            // Synchronous in-flight guard: drops rapid duplicate visibility events (e.g. carousel
            // snapshotFlow re-emitting on layout changes) before the repository has flipped
            // previewStatus away from Idle.
            if (activePreviewJobs[uri]?.isActive == true) return
            val feedUi = uiState.value.feeds.find { it.feed.uri == uri } ?: return
            // Idle is the only state that triggers a fetch.
            if (feedUi.previewStatus != FeedPreviewStatus.Idle) return

            activePreviewJobs[uri] = viewModelScope.launch { exploreRepository.loadFeedPreview(uri) }
        }

        // -------------------------------------------------------------------------
        // Account dismissal
        // -------------------------------------------------------------------------

        private fun onAccountDismissed(did: String) {
            exploreRepository.dismissAccount(did)
            project()
        }

        // -------------------------------------------------------------------------
        // Trend tap
        // -------------------------------------------------------------------------

        private fun onTrendTapped(trend: TrendUi) {
            // A double tap on a feed trend would otherwise open the feed twice.
            if (trendTapJob?.isActive == true) return
            when (val link = parseTrendLink(trend.link)) {
                is TrendLink.Feed ->
                    trendTapJob =
                        viewModelScope.launch {
                            exploreRepository
                                .resolveDid(link.actor)
                                .onSuccess { did ->
                                    sendEffect(
                                        DiscoverEffect.NavigateToFeed(
                                            uri = "at://$did/app.bsky.feed.generator/${link.rkey}",
                                            displayName = trend.displayName,
                                        ),
                                    )
                                }.onFailure { sendEffect(DiscoverEffect.SubmitSearch(trend.displayName)) }
                        }
                is TrendLink.Search -> sendEffect(DiscoverEffect.SubmitSearch(link.query))
                // Starter packs open in their own screen in a later change; until then, search the name.
                is TrendLink.StarterPack, TrendLink.Unknown -> sendEffect(DiscoverEffect.SubmitSearch(trend.displayName))
            }
        }
    }
