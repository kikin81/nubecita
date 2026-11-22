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
 * moves (a tab refreshed, or the session changed), without going back to Loading, and a failed
 * re-read keeps the rows it had; but when the signed-in account changes the sections are
 * cleared first, so a failed re-read shows Error and never the previous account's rows. A new
 * search cancels the requests of the old one.
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

        /**
         * A section's request key, taken from the search the pane's state currently shows (the one
         * [onSubmittedChanged] adopted), never straight from the store: a section can then never
         * see a key before its header and results have been reset for it.
         */
        private fun <K : Any> adopted(key: (SubmittedSearch) -> K): Flow<K?> = uiState.map { state -> (state.mode as? SearchPaneMode.Summary)?.let { key(it.search) } }.distinctUntilChanged()

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
                .onEach { did -> onViewerChanged(did) }
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
                key = adopted { it.postsKey() },
                fetch = { key -> firstPages.posts(key.query, key.filters, key.submission) },
                onLoaded = { page ->
                    copy(hitsTotal = page.hitsTotal, topPost = page.items.firstOrNull(), postsStatus = SummarySectionStatus.Loaded)
                },
                onFailed = { copy(postsStatus = postsStatus.afterFailure()) },
            )
            loadSection(
                section = SummarySection.People,
                key = adopted { it.queryKey() },
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
                key = adopted { it.queryKey() },
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
         * The key comes from the adopted state ([adopted]), and `mapLatest` cancels a superseded
         * request, so a result for an old key does not reach the screen. (An explicit key check on
         * apply was removed: no test could reach a case where it, and not the cancellation, was what
         * kept an old result out.) Resetting to Loading on a new key is done where the new key is adopted
         * ([onSubmittedChanged]), in the same state change that swaps the header, so a section
         * never shows one search's results under another's header.
         */
        private fun <K : Any, P> loadSection(
            section: SummarySection,
            key: Flow<K?>,
            fetch: suspend (K) -> Result<P>,
            onLoaded: SearchPaneMode.Summary.(P) -> SearchPaneMode.Summary,
            onFailed: SearchPaneMode.Summary.() -> SearchPaneMode.Summary,
        ) {
            // The account is part of the trigger: after an account change the sections reload even
            // when nothing else moved ([onViewerChanged] has already reset them).
            combine(key, firstPages.version, retries.getValue(section), uiState.map { it.viewerDid }.distinctUntilChanged()) { current, _, _, _ -> current }
                .mapLatest { current ->
                    if (current == null) return@mapLatest
                    val result = fetch(current)
                    updateSummary {
                        result.fold(onSuccess = { onLoaded(it) }, onFailure = { onFailed() })
                    }
                }.launchIn(viewModelScope)
        }

        /**
         * The signed-in account changed: whatever the Summary shows belongs to the previous account
         * (people with their follow state, feeds with their pins), so every section goes back to
         * Loading with its rows cleared. The reload then fails into Error rather than into the old
         * rows. A failed re-read for the SAME account keeps its rows ([afterFailure]).
         */
        private fun onViewerChanged(did: String?) {
            setState {
                if (did == viewerDid) return@setState this
                val summary = mode as? SearchPaneMode.Summary
                copy(
                    viewerDid = did,
                    mode = summary?.cleared() ?: mode,
                )
            }
        }

        private fun SearchPaneMode.Summary.cleared() =
            copy(
                hitsTotal = null,
                topPost = null,
                people = persistentListOf(),
                feeds = persistentListOf(),
                postsStatus = SummarySectionStatus.Loading,
                peopleStatus = SummarySectionStatus.Loading,
                feedsStatus = SummarySectionStatus.Loading,
            )

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

        /** Rewrites the Summary on the CURRENT state; a no-op in Explore. */
        private fun updateSummary(
            transform: SearchPaneMode.Summary.() -> SearchPaneMode.Summary,
        ) {
            setState {
                val summary = mode as? SearchPaneMode.Summary ?: return@setState this
                copy(mode = summary.transform())
            }
        }

        /**
         * Optimistic follow / unfollow through the shared [FollowToggle], as the People tab does:
         * the toggle owns what the row becomes; this keeps only the error message for the screen.
         * The viewer's own row has no Follow button, and a tap for it is ignored.
         */
        private fun onFollowTapped(did: String) {
            if (did == uiState.value.viewerDid) return
            val actor = (uiState.value.mode as? SearchPaneMode.Summary)?.people?.find { it.did == did } ?: return
            followToggle.toggleActor(
                scope = viewModelScope,
                actor = actor,
                updateActor = { transform -> updateActor(did, transform) },
            ) { outcome ->
                when (outcome) {
                    FollowOutcome.FollowFailed -> sendEffect(SearchPaneEffect.FollowFailed(wasUnfollow = false))
                    FollowOutcome.UnfollowFailed -> sendEffect(SearchPaneEffect.FollowFailed(wasUnfollow = true))
                    FollowOutcome.Followed, FollowOutcome.Unfollowed -> Unit
                }
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
