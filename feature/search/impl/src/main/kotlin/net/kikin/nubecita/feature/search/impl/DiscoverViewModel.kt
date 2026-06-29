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
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.feature.search.impl.data.SuggestionsRepository
import javax.inject.Inject

/**
 * Presenter for the Discover tab — suggested accounts and suggested feeds
 * with lazy per-feed preview strips.
 *
 * ## Section loading
 * Both sections load concurrently on [DiscoverEvent.OnAppear]. Each section
 * is independent: an accounts error does not mask the feeds section. Load-once
 * semantics apply: a [DiscoverSectionStatus.Loaded] section is skipped on
 * subsequent appearances. [DiscoverSectionStatus.Error] and
 * [DiscoverSectionStatus.Empty] sections auto-retry on the next
 * [DiscoverEvent.OnAppear]. [DiscoverEvent.OnRefresh] unconditionally reloads
 * both sections.
 *
 * ## Pin toggle
 * [DiscoverEvent.OnPinTapped] optimistically flips [DiscoverFeedUi.feed.isPinned]
 * in state, then calls [PinnedFeedsRepository.pinFeed] or
 * [PinnedFeedsRepository.unpinFeed]. Analytics ([InteractFeed]) fires on
 * success. On failure the flag is flipped back on the CURRENT state (targeted
 * rollback — no snapshot clobber). Rapid concurrent taps on the same URI are
 * dropped via an in-flight job guard. Mirrors [FeedPinViewModel]'s approach
 * but scoped per-URI across a list.
 *
 * The `isPinned` flag is also seeded reactively from
 * [PinnedFeedsRepository.observePinnedFeeds] so that changes made on other
 * surfaces (Feed tab, another screen) reflect here immediately.
 *
 * ## Follow toggle
 * [DiscoverEvent.OnFollowTapped] optimistically flips
 * [SuggestedAccountUi.isFollowing], then calls [FollowRepository.follow] or
 * [FollowRepository.unfollow]. Analytics ([InteractActor]) fires on success.
 * On failure the flag is reverted on the CURRENT state. Rapid concurrent taps
 * on the same DID are dropped. Mirrors [ProfileViewModel]'s follow handler.
 *
 * ## Lazy preview
 * [DiscoverEvent.OnFeedCardVisible] triggers a one-shot
 * [SuggestionsRepository.getFeedPreview] fetch for the given URI. Subsequent
 * calls for the same URI are no-ops once the fetch has started or completed —
 * [FeedPreviewStatus] is the cache sentinel.
 *
 * ## Session-local account dismissal
 * Dismissed DIDs are stored in [dismissedDids] (ViewModel-scoped, not in
 * [DiscoverState]) and filtered from the accounts list on every load so
 * a pull-to-refresh does not resurface dismissed cards.
 */
@HiltViewModel
internal class DiscoverViewModel
    @Inject
    constructor(
        private val suggestionsRepository: SuggestionsRepository,
        private val followRepository: FollowRepository,
        private val pinnedFeedsRepository: PinnedFeedsRepository,
        private val analytics: AnalyticsClient,
    ) : MviViewModel<DiscoverState, DiscoverEvent, DiscoverEffect>(DiscoverState()) {
        /**
         * Latest set of pinned feed URIs from [PinnedFeedsRepository.observePinnedFeeds].
         * Used in [loadFeeds] to seed [DiscoverFeedUi.feed.isPinned] at load time
         * without needing the flow to re-emit after the feed list lands.
         *
         * Plain var (not StateFlow) — only written in the observer collector and read
         * in [loadFeeds]; never collected as a flow.
         */
        private var currentPinnedUris: Set<String> = emptySet()

        /** In-flight follow/unfollow jobs keyed by DID — drops rapid concurrent taps. */
        private val activeFollowJobs = mutableMapOf<String, Job>()

        /** In-flight pin/unpin jobs keyed by feed URI — drops rapid concurrent taps. */
        private val activePinJobs = mutableMapOf<String, Job>()

        /**
         * In-flight preview fetch jobs keyed by feed URI — synchronous guard against
         * duplicate fetches when the carousel's snapshotFlow re-emits on layout changes.
         */
        private val activePreviewJobs = mutableMapOf<String, Job>()

        /** Tracks the most-recently-launched accounts load (for cancel-and-restart on refresh). */
        private var accountsLoadJob: Job? = null

        /** Tracks the most-recently-launched feeds load (for cancel-and-restart on refresh). */
        private var feedsLoadJob: Job? = null

        /** DIDs dismissed this session — persists for the ViewModel lifetime. */
        private val dismissedDids = mutableSetOf<String>()

        init {
            // Observe pinned feeds and (a) update currentPinnedUris for use in loadFeeds,
            // (b) reactively update isPinned on the already-loaded feeds list.
            pinnedFeedsRepository
                .observePinnedFeeds()
                .map { result -> result.feeds.map { it.uri }.toSet() }
                .onEach { uris ->
                    currentPinnedUris = uris
                    setState {
                        copy(
                            feeds =
                                feeds
                                    .map { discoverFeed ->
                                        val pinned = discoverFeed.feed.uri in uris
                                        if (discoverFeed.feed.isPinned != pinned) {
                                            discoverFeed.copy(feed = discoverFeed.feed.copy(isPinned = pinned))
                                        } else {
                                            discoverFeed
                                        }
                                    }.toImmutableList(),
                        )
                    }
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
            }
        }

        // -------------------------------------------------------------------------
        // Section loading
        // -------------------------------------------------------------------------

        private fun onAppear() {
            val state = uiState.value
            if (state.accountsStatus.needsLoad()) {
                accountsLoadJob?.cancel()
                accountsLoadJob = viewModelScope.launch { loadAccounts() }
            }
            if (state.feedsStatus.needsLoad()) {
                feedsLoadJob?.cancel()
                feedsLoadJob = viewModelScope.launch { loadFeeds() }
            }
        }

        private fun onRefresh() {
            accountsLoadJob?.cancel()
            accountsLoadJob = viewModelScope.launch { loadAccounts() }
            feedsLoadJob?.cancel()
            feedsLoadJob = viewModelScope.launch { loadFeeds() }
        }

        private suspend fun loadAccounts() {
            setState { copy(accountsStatus = DiscoverSectionStatus.Loading) }
            suggestionsRepository
                .getSuggestedAccounts()
                .onSuccess { list ->
                    val filtered = list.filter { it.did !in dismissedDids }
                    if (filtered.isEmpty()) {
                        setState { copy(accountsStatus = DiscoverSectionStatus.Empty) }
                    } else {
                        setState {
                            copy(
                                accounts = filtered.toImmutableList(),
                                accountsStatus = DiscoverSectionStatus.Loaded,
                            )
                        }
                    }
                }.onFailure {
                    setState { copy(accountsStatus = DiscoverSectionStatus.Error) }
                }
        }

        private suspend fun loadFeeds() {
            setState { copy(feedsStatus = DiscoverSectionStatus.Loading) }
            suggestionsRepository
                .getSuggestedFeeds()
                .onSuccess { list ->
                    if (list.isEmpty()) {
                        setState { copy(feedsStatus = DiscoverSectionStatus.Empty) }
                    } else {
                        // Seed isPinned from the latest pinned-feeds observer value so
                        // the UI shows the correct pin state immediately on first render,
                        // even before the observer has a chance to re-emit.
                        val pinnedUris = currentPinnedUris
                        setState {
                            // Preserve any already-loaded preview data so a pull-to-refresh
                            // does not discard previews for feeds that haven't changed URIs.
                            // New URIs (not in the prior list) start at Idle as normal.
                            val priorByUri = feeds.associateBy { it.feed.uri }
                            copy(
                                feeds =
                                    list
                                        .map { feedUi ->
                                            val prior = priorByUri[feedUi.uri]
                                            DiscoverFeedUi(
                                                feed = feedUi.copy(isPinned = feedUi.uri in pinnedUris),
                                                preview = prior?.preview,
                                                previewStatus = prior?.previewStatus ?: FeedPreviewStatus.Idle,
                                            )
                                        }.toImmutableList(),
                                feedsStatus = DiscoverSectionStatus.Loaded,
                            )
                        }
                    }
                }.onFailure {
                    setState { copy(feedsStatus = DiscoverSectionStatus.Error) }
                }
        }

        // -------------------------------------------------------------------------
        // Follow toggle
        // -------------------------------------------------------------------------

        private fun onFollowTapped(did: String) {
            if (activeFollowJobs[did]?.isActive == true) return

            val account = uiState.value.accounts.find { it.did == did } ?: return

            if (account.isFollowing) {
                val followUri = account.followUri ?: return
                // Optimistic flip: unfollow
                setState {
                    copy(
                        accounts =
                            accounts
                                .map { if (it.did == did) it.copy(isFollowing = false, followUri = null) else it }
                                .toImmutableList(),
                    )
                }
                activeFollowJobs[did] =
                    viewModelScope.launch {
                        followRepository
                            .unfollow(followUri)
                            .onSuccess {
                                analytics.log(InteractActor(ActorAction.Unfollow, PostSurface.Explore))
                            }.onFailure {
                                // Targeted rollback: flip back on CURRENT state
                                setState {
                                    copy(
                                        accounts =
                                            accounts
                                                .map { if (it.did == did) it.copy(isFollowing = true, followUri = followUri) else it }
                                                .toImmutableList(),
                                    )
                                }
                                sendEffect(DiscoverEffect.ShowError("Failed to unfollow"))
                            }
                    }
            } else {
                // Optimistic flip: follow (followUri will be set on success)
                setState {
                    copy(
                        accounts =
                            accounts
                                .map { if (it.did == did) it.copy(isFollowing = true) else it }
                                .toImmutableList(),
                    )
                }
                activeFollowJobs[did] =
                    viewModelScope.launch {
                        followRepository
                            .follow(did)
                            .onSuccess { newFollowUri ->
                                // Commit the returned follow record URI
                                setState {
                                    copy(
                                        accounts =
                                            accounts
                                                .map { if (it.did == did) it.copy(isFollowing = true, followUri = newFollowUri) else it }
                                                .toImmutableList(),
                                    )
                                }
                                analytics.log(InteractActor(ActorAction.Follow, PostSurface.Explore))
                            }.onFailure {
                                // Targeted rollback
                                setState {
                                    copy(
                                        accounts =
                                            accounts
                                                .map { if (it.did == did) it.copy(isFollowing = false, followUri = null) else it }
                                                .toImmutableList(),
                                    )
                                }
                                sendEffect(DiscoverEffect.ShowError("Failed to follow"))
                            }
                    }
            }
        }

        // -------------------------------------------------------------------------
        // Pin toggle
        // -------------------------------------------------------------------------

        private fun onPinTapped(uri: String) {
            if (activePinJobs[uri]?.isActive == true) return

            val feedUi = uiState.value.feeds.find { it.feed.uri == uri } ?: return
            val wasPinned = feedUi.feed.isPinned

            // Optimistic flip
            setState {
                copy(
                    feeds =
                        feeds
                            .map { if (it.feed.uri == uri) it.copy(feed = it.feed.copy(isPinned = !wasPinned)) else it }
                            .toImmutableList(),
                )
            }

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
                            // Targeted rollback on CURRENT state — no snapshot clobber
                            setState {
                                copy(
                                    feeds =
                                        feeds
                                            .map { if (it.feed.uri == uri) it.copy(feed = it.feed.copy(isPinned = wasPinned)) else it }
                                            .toImmutableList(),
                                )
                            }
                            sendEffect(DiscoverEffect.ShowError("Failed to ${if (wasPinned) "unpin" else "pin"} feed"))
                        }
                }
        }

        // -------------------------------------------------------------------------
        // Lazy preview
        // -------------------------------------------------------------------------

        private fun onFeedCardVisible(uri: String) {
            // Synchronous in-flight guard: drops rapid duplicate visibility events (e.g. carousel
            // snapshotFlow re-emitting on layout changes) before setState has a chance to flip
            // previewStatus away from Idle.
            if (activePreviewJobs[uri]?.isActive == true) return
            val feedUi = uiState.value.feeds.find { it.feed.uri == uri } ?: return
            // Idle is the only state that triggers a fetch.
            if (feedUi.previewStatus != FeedPreviewStatus.Idle) return

            setState {
                copy(
                    feeds =
                        feeds
                            .map { if (it.feed.uri == uri) it.copy(previewStatus = FeedPreviewStatus.Loading) else it }
                            .toImmutableList(),
                )
            }

            activePreviewJobs[uri] =
                viewModelScope.launch {
                    suggestionsRepository
                        .getFeedPreview(uri)
                        .onSuccess { posts ->
                            setState {
                                copy(
                                    feeds =
                                        feeds
                                            .map {
                                                if (it.feed.uri == uri) {
                                                    it.copy(
                                                        preview = posts.toImmutableList(),
                                                        previewStatus = FeedPreviewStatus.Loaded,
                                                    )
                                                } else {
                                                    it
                                                }
                                            }.toImmutableList(),
                                )
                            }
                        }.onFailure {
                            setState {
                                copy(
                                    feeds =
                                        feeds
                                            .map { if (it.feed.uri == uri) it.copy(previewStatus = FeedPreviewStatus.Error) else it }
                                            .toImmutableList(),
                                )
                            }
                        }
                }
        }

        // -------------------------------------------------------------------------
        // Account dismissal
        // -------------------------------------------------------------------------

        private fun onAccountDismissed(did: String) {
            dismissedDids.add(did)
            setState { copy(accounts = accounts.filter { it.did != did }.toImmutableList()) }
        }
    }
