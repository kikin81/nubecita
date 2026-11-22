package net.kikin.nubecita.feature.search.impl.pane

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.search.impl.data.FeedPinToggle
import net.kikin.nubecita.feature.search.impl.data.FollowOutcome
import net.kikin.nubecita.feature.search.impl.data.FollowOverlay
import net.kikin.nubecita.feature.search.impl.data.FollowToggle
import net.kikin.nubecita.feature.search.impl.data.PinOutcome
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.SearchFirstPages
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearch
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearchStore
import net.kikin.nubecita.feature.search.impl.data.withFollowOverride
import net.kikin.nubecita.feature.search.impl.data.withPinnedFrom
import javax.inject.Inject

/**
 * Presenter for the Search tab's second pane.
 *
 * The mode is a projection of [SubmittedSearchStore.submitted]: [SearchPaneMode.Explore]
 * with no search, [SearchPaneMode.Summary] with one. The pane is composed in the list-detail
 * scene's placeholder slot, so this ViewModel is scoped to the logged-in shell entry and
 * survives tab switches and fold changes; it holds what `rememberSaveable` cannot keep there
 * (scroll positions, "Show more" toggles).
 *
 * The Summary's scroll position starts over whenever the searched query or filters change;
 * Explore's is kept across searches. Changing only the result tab keeps both.
 *
 * **Summary content.** The three sections read the same [SearchFirstPages] entries the result
 * tabs do, keyed by the store's (query, filters, submission) so the pane and a tab of the same
 * search share one request. Posts depend on the filters; people and feeds do not, so a filter
 * change reloads the posts section only. A section re-reads when [SearchFirstPages.version]
 * moves (a tab refreshed, or the session changed), without going back to Loading. Every result
 * is applied only if the pane is still showing the search it was requested for.
 *
 * Follow and pin go through the primitives the People and Feeds tabs use ([FollowToggle] over
 * [FollowOverlay], [FeedPinToggle] over the pinned-feeds stream), so the three surfaces agree.
 * The pane logs no analytics: it never reports a search of its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchPaneViewModel
    @Inject
    constructor(
        private val store: SubmittedSearchStore,
        private val firstPages: SearchFirstPages,
        private val followToggle: FollowToggle,
        private val followOverlay: FollowOverlay,
        private val pinToggle: FeedPinToggle,
        pinnedFeedsRepository: PinnedFeedsRepository,
        sessionStateProvider: SessionStateProvider,
    ) : MviViewModel<SearchPaneState, SearchPaneEvent, SearchPaneEffect>(SearchPaneState()) {
        /** What the posts section was requested for. */
        private data class PostsKey(
            val query: String,
            val filters: PostSearchFilters,
            val submission: Int,
        )

        /** What the people and feeds sections were requested for. */
        private data class QueryKey(
            val query: String,
            val submission: Int,
        )

        private fun SubmittedSearch.postsKey() = PostsKey(query, filters, submission)

        private fun SubmittedSearch.queryKey() = QueryKey(query, submission)

        private val retries = SummarySection.entries.associateWith { MutableStateFlow(0) }

        /** Latest pinned feed URIs; applied to every feeds page as it lands. */
        private var currentPinnedUris: Set<String> = emptySet()

        init {
            store.submitted
                .onEach { search -> onSubmittedChanged(search) }
                .launchIn(viewModelScope)
            sessionStateProvider.state
                .map { (it as? SessionState.SignedIn)?.did }
                .distinctUntilChanged()
                .onEach { did -> setState { copy(viewerDid = did) } }
                .launchIn(viewModelScope)
            followOverlay.overrides
                .onEach { overrides ->
                    updateSummary { copy(people = people.map { it.withFollowOverride(overrides) }.toImmutableList()) }
                }.launchIn(viewModelScope)
            pinnedFeedsRepository
                .observePinnedFeeds()
                .map { result -> result.feeds.map { it.uri }.toSet() }
                .onEach { uris ->
                    currentPinnedUris = uris
                    updateSummary { copy(feeds = feeds.map { it.withPinnedFrom(uris) }.toImmutableList()) }
                }.launchIn(viewModelScope)

            loadSection(
                section = SummarySection.Posts,
                key = store.submitted.map { it?.postsKey() }.distinctUntilChanged(),
                isCurrent = { summary, key -> summary.search.postsKey() == key },
                fetch = { key -> firstPages.posts(key.query, key.filters, key.submission) },
                onLoaded = { page ->
                    copy(hitsTotal = page.hitsTotal, topPost = page.items.firstOrNull(), postsStatus = SummarySectionStatus.Loaded)
                },
                onFailed = { copy(postsStatus = postsStatus.afterFailure()) },
            )
            loadSection(
                section = SummarySection.People,
                key = store.submitted.map { it?.queryKey() }.distinctUntilChanged(),
                isCurrent = { summary, key -> summary.search.queryKey() == key },
                fetch = { key -> firstPages.people(key.query, key.submission) },
                onLoaded = { page ->
                    val overrides = followOverlay.current()
                    copy(
                        people =
                            page.items
                                .take(SUMMARY_ROWS)
                                .map { it.withFollowOverride(overrides) }
                                .toImmutableList(),
                        peopleStatus = SummarySectionStatus.Loaded,
                    )
                },
                onFailed = { copy(peopleStatus = peopleStatus.afterFailure()) },
            )
            loadSection(
                section = SummarySection.Feeds,
                key = store.submitted.map { it?.queryKey() }.distinctUntilChanged(),
                isCurrent = { summary, key -> summary.search.queryKey() == key },
                fetch = { key -> firstPages.feeds(key.query, key.submission) },
                onLoaded = { page ->
                    copy(
                        feeds =
                            page.items
                                .take(SUMMARY_ROWS)
                                .map { it.withPinnedFrom(currentPinnedUris) }
                                .toImmutableList(),
                        feedsStatus = SummarySectionStatus.Loaded,
                    )
                },
                onFailed = { copy(feedsStatus = feedsStatus.afterFailure()) },
            )
        }

        override fun handleEvent(event: SearchPaneEvent) {
            when (event) {
                is SearchPaneEvent.ScrollSaved -> onScrollSaved(event)
                SearchPaneEvent.FeedsExpansionToggled -> setState { copy(feedsExpanded = !feedsExpanded) }
                SearchPaneEvent.AccountsExpansionToggled -> setState { copy(accountsExpanded = !accountsExpanded) }
                is SearchPaneEvent.FilterRemoved -> store.updateFilters { it.without(event.filter) }
                SearchPaneEvent.EditFiltersClicked -> store.setFilterSheetOpen(true)
                is SearchPaneEvent.SeeAllClicked -> store.selectTab(event.tab)
                is SearchPaneEvent.RetrySection -> retry(event.section)
                is SearchPaneEvent.PostTapped -> sendEffect(SearchPaneEffect.NavigateToPost(event.uri))
                is SearchPaneEvent.ActorTapped -> sendEffect(SearchPaneEffect.NavigateToProfile(event.handle))
                is SearchPaneEvent.FeedTapped -> sendEffect(SearchPaneEffect.NavigateToFeed(event.uri, event.displayName))
                is SearchPaneEvent.FollowTapped -> onFollowTapped(event.did)
                is SearchPaneEvent.PinTapped -> onPinTapped(event.uri)
            }
        }

        private fun SummarySectionStatus.afterFailure() = if (this == SummarySectionStatus.Loaded) this else SummarySectionStatus.Error

        /**
         * Runs one section: whenever its [key] changes, [SearchFirstPages.version] moves or the
         * section is retried, [fetch] the first page and apply it with [onLoaded] / [onFailed].
         * `mapLatest` cancels a superseded request; [isCurrent] drops a result whose key is no
         * longer the one on screen (the pane may have moved to another search while it was
         * landing). Resetting to Loading on a new key is done where the new key is adopted
         * ([onSubmittedChanged]), in the same state change that swaps the header, so a section
         * never shows one search's results under another's header.
         */
        private fun <K : Any, P> loadSection(
            section: SummarySection,
            key: Flow<K?>,
            isCurrent: (SearchPaneMode.Summary, K) -> Boolean,
            fetch: suspend (K) -> Result<P>,
            onLoaded: SearchPaneMode.Summary.(P) -> SearchPaneMode.Summary,
            onFailed: SearchPaneMode.Summary.() -> SearchPaneMode.Summary,
        ) {
            combine(key, firstPages.version, retries.getValue(section)) { current, _, _ -> current }
                .mapLatest { current ->
                    if (current == null) return@mapLatest
                    val result = fetch(current)
                    updateSummary(requires = { isCurrent(it, current) }) {
                        result.fold(onSuccess = { onLoaded(it) }, onFailure = { onFailed() })
                    }
                }.launchIn(viewModelScope)
        }

        private fun retry(section: SummarySection) {
            updateSummary {
                when (section) {
                    SummarySection.Posts -> copy(postsStatus = SummarySectionStatus.Loading)
                    SummarySection.People -> copy(peopleStatus = SummarySectionStatus.Loading)
                    SummarySection.Feeds -> copy(feedsStatus = SummarySectionStatus.Loading)
                }
            }
            retries.getValue(section).update { it + 1 }
        }

        /** Rewrites the Summary on the CURRENT state; a no-op in Explore or when [requires] is false. */
        private fun updateSummary(
            requires: (SearchPaneMode.Summary) -> Boolean = { true },
            transform: SearchPaneMode.Summary.() -> SearchPaneMode.Summary,
        ) {
            setState {
                val summary = mode as? SearchPaneMode.Summary ?: return@setState this
                if (!requires(summary)) return@setState this
                copy(mode = summary.transform())
            }
        }

        /**
         * Optimistic follow / unfollow through the shared [FollowToggle], as the People tab does:
         * the row flips at once and, on a failure, flips back on the CURRENT state with a message
         * for the screen. The viewer's own row has no Follow button, and a tap for it is ignored.
         */
        private fun onFollowTapped(did: String) {
            if (did == uiState.value.viewerDid) return
            val actor = (uiState.value.mode as? SearchPaneMode.Summary)?.people?.find { it.did == did } ?: return
            val wasFollowing = actor.isFollowing
            val previousFollowUri = actor.followUri

            val started =
                followToggle.toggle(
                    scope = viewModelScope,
                    did = did,
                    isFollowing = wasFollowing,
                    followUri = previousFollowUri,
                ) { outcome ->
                    when (outcome) {
                        FollowOutcome.Followed ->
                            updateActor(did) { it.copy(isFollowing = true, followUri = followOverlay[did]?.followUri) }
                        FollowOutcome.Unfollowed -> Unit
                        FollowOutcome.FollowFailed -> {
                            updateActor(did) { it.copy(isFollowing = false, followUri = null) }
                            sendEffect(SearchPaneEffect.FollowFailed(wasUnfollow = false))
                        }
                        FollowOutcome.UnfollowFailed -> {
                            updateActor(did) { it.copy(isFollowing = true, followUri = previousFollowUri) }
                            sendEffect(SearchPaneEffect.FollowFailed(wasUnfollow = true))
                        }
                    }
                }
            // The toggle has already written the optimistic override; show it now rather
            // than when the overlay's collector next runs.
            if (started) {
                val overrides = followOverlay.current()
                updateActor(did) { it.withFollowOverride(overrides) }
            }
        }

        private fun updateActor(
            did: String,
            transform: (ActorUi) -> ActorUi,
        ) {
            updateSummary { copy(people = people.map { if (it.did == did) transform(it) else it }.toImmutableList()) }
        }

        /** Optimistic pin / unpin through the shared [FeedPinToggle], as the Feeds tab does. */
        private fun onPinTapped(uri: String) {
            val feed = (uiState.value.mode as? SearchPaneMode.Summary)?.feeds?.find { it.uri == uri } ?: return
            val wasPinned = feed.isPinned
            pinToggle.toggle(
                scope = viewModelScope,
                uri = uri,
                isPinned = wasPinned,
                onOptimistic = { pinned -> setPinned(uri, pinned) },
            ) { outcome ->
                when (outcome) {
                    PinOutcome.Pinned, PinOutcome.Unpinned -> Unit
                    PinOutcome.PinFailed, PinOutcome.UnpinFailed -> {
                        setPinned(uri, wasPinned)
                        sendEffect(SearchPaneEffect.PinFailed(wasUnpin = wasPinned))
                    }
                }
            }
        }

        private fun setPinned(
            uri: String,
            pinned: Boolean,
        ) {
            updateSummary { copy(feeds = feeds.map { if (it.uri == uri) it.copy(isPinned = pinned) else it }.toImmutableList()) }
        }

        /**
         * Adopts the store's search: the mode, the header and the scroll key, and puts every
         * section whose request key changed back to Loading with its old results cleared. Posts
         * depend on the filters; people and feeds only on the query and submission, so a filter
         * change leaves them as they are.
         */
        private fun onSubmittedChanged(search: SubmittedSearch?) {
            val key = search?.let { SummaryKey(it.query, it.filters) }
            setState {
                val summary =
                    search?.let {
                        val previous = (mode as? SearchPaneMode.Summary)?.search
                        val queryChanged = previous == null || previous.queryKey() != it.queryKey()
                        val postsChanged = queryChanged || previous?.filters != it.filters
                        var next = (mode as? SearchPaneMode.Summary)?.copy(search = it) ?: SearchPaneMode.Summary(it)
                        if (postsChanged) {
                            next = next.copy(hitsTotal = null, topPost = null, postsStatus = SummarySectionStatus.Loading)
                        }
                        if (queryChanged) {
                            next =
                                next.copy(
                                    people = persistentListOf(),
                                    feeds = persistentListOf(),
                                    peopleStatus = SummarySectionStatus.Loading,
                                    feedsStatus = SummarySectionStatus.Loading,
                                )
                        }
                        next
                    }
                copy(
                    mode = summary ?: SearchPaneMode.Explore,
                    summaryScroll = if (key != summaryKey) ScrollPosition() else summaryScroll,
                    summaryKey = key,
                )
            }
        }

        private fun onScrollSaved(event: SearchPaneEvent.ScrollSaved) {
            setState {
                if (event.target == ScrollTarget.Summary && event.summaryKey != null && event.summaryKey != summaryKey) {
                    return@setState this
                }
                val target =
                    event.target
                        ?: if (mode is SearchPaneMode.Summary) ScrollTarget.Summary else ScrollTarget.Explore
                when (target) {
                    ScrollTarget.Explore -> copy(exploreScroll = event.position)
                    ScrollTarget.Summary -> copy(summaryScroll = event.position)
                }
            }
        }
    }
