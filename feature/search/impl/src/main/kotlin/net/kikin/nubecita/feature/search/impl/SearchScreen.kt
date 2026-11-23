package net.kikin.nubecita.feature.search.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.LocalTabReTapSignal
import net.kikin.nubecita.designsystem.component.NubecitaPullToRefreshBox
import net.kikin.nubecita.feature.feed.api.FeedView
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.SearchTab
import net.kikin.nubecita.feature.search.impl.ui.DiscoverLanding
import net.kikin.nubecita.feature.search.impl.ui.PostFiltersSheet
import net.kikin.nubecita.feature.starterpack.api.StarterPack

/**
 * Stateful Search tab home. Hoists [SearchViewModel] and [DiscoverViewModel]
 * and forwards their state down to [SearchScreenContent]. The per-tab Screens
 * (Posts / People / Feeds / Packs) are mounted inside the stateless body's
 * [HorizontalPager] gated on `!isQueryBlank`.
 *
 * [DiscoverViewModel] effects are collected here (the stateful boundary) so
 * nav actions reach [LocalMainShellNavState] directly and snackbar errors
 * surface in the shared [SnackbarHostState] (hoisted from
 * [SearchScreenContent] so both VMs can emit to it). Suppresses
 * VM-forwarding lints — see ComposerScreen / ProfileScreen for the full
 * rationale.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    discoverViewModel: DiscoverViewModel = hiltViewModel(),
    // The same instance the Posts tab uses (same ViewModelStoreOwner); read here
    // only for the result estimate the filter sheet's button shows.
    postsViewModel: SearchPostsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val postsState by postsViewModel.uiState.collectAsStateWithLifecycle()
    val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()

    val onEvent = remember(viewModel) { viewModel::handleEvent }
    val onDiscoverEvent = remember(discoverViewModel) { discoverViewModel::handleEvent }
    val onClearQueryRequest =
        remember(viewModel) {
            { viewModel.textFieldState.clearText() }
        }

    val isAtLeastMedium =
        currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val navState = LocalMainShellNavState.current
    val searchBarState = rememberSearchBarState()

    // System back from results clears the search instead of leaving the tab.
    // The condition reads the tab's back stack, not the pane layout: on a wide
    // window a pushed post or profile replaces the detail pane and Search is
    // still on screen, but it is no longer the top of its stack, so back must
    // close that pane first. The search survives underneath it.
    BackHandler(
        enabled =
            shouldClearSearchOnBack(
                isTopOfTabStack = navState.backStack.lastOrNull() == Search,
                phase = state.phase,
                isBarCollapsed = searchBarState.currentValue == SearchBarValue.Collapsed,
            ),
    ) {
        onEvent(SearchEvent.ClearSearch)
    }

    val onNavigateToActor =
        remember(navState) { { handle: String -> navState.add(Profile(handle = handle)) } }

    // Snackbar state hoisted to this level so both SearchScreenContent's per-tab
    // error handlers AND the discover-effect ShowError can share a single host.
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    // Collect Discover VM effects: nav → MainShellNavState, error → snackbar.
    LaunchedEffect(Unit) {
        discoverViewModel.effects.collect { effect ->
            when (effect) {
                is DiscoverEffect.NavigateToProfile ->
                    navState.add(Profile(handle = effect.handle))
                is DiscoverEffect.NavigateToFeed ->
                    navState.add(
                        FeedView(feedUri = effect.uri, displayName = effect.displayName),
                    )
                is DiscoverEffect.NavigateToPack -> navState.add(StarterPack(uri = effect.uri))
                is DiscoverEffect.SubmitSearch -> {
                    viewModel.textFieldState.setTextAndPlaceCursorAtEnd(effect.query)
                    viewModel.handleEvent(SearchEvent.SubmitClicked)
                }
                is DiscoverEffect.ShowError ->
                    snackScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(effect.message)
                    }
            }
        }
    }

    SearchScreenContent(
        textFieldState = viewModel.textFieldState,
        isQueryBlank = state.isQueryBlank,
        currentQuery = state.currentQuery,
        phase = state.phase,
        selectedTab = state.selectedTab,
        filters = state.filters,
        isFilterSheetOpen = state.isFilterSheetOpen,
        postsHitsTotal = postsState.hitsTotal,
        recentSearches = state.recentSearches,
        onEvent = onEvent,
        onClearQueryRequest = onClearQueryRequest,
        isAtLeastMedium = isAtLeastMedium,
        onNavigateToActor = onNavigateToActor,
        discoverState = discoverState,
        onDiscoverEvent = onDiscoverEvent,
        snackbarHostState = snackbarHostState,
        searchBarState = searchBarState,
        modifier = modifier,
    )
}

/**
 * Stateless screen body. Extracted so preview / screenshot-test composables
 * can drive the layout without a Hilt-graph dependency on [SearchViewModel]
 * or [DiscoverViewModel]. The single [onEvent] / [onDiscoverEvent] callbacks
 * are the stable dispatch seams.
 *
 * Layout: a [Scaffold] hosts a [SnackbarHost] for per-tab append errors and
 * discover-action errors. Inside the Scaffold a [Column] renders the
 * [SearchBarSection] (the collapsed pill + the expanded typing overlay)
 * followed by a body branched on [phase]:
 *
 *  - [SearchPhase.Discover]: a [NubecitaPullToRefreshBox] wrapping
 *    [DiscoverLanding]: recents, the Trending list, then (phones only) the
 *      accounts and feeds carousels, or an empty-state hint when nothing loads.
 *  - [SearchPhase.Results]: [SecondaryTabRow] + [HorizontalPager] hosting
 *    [SearchPostsScreen] / [SearchActorsScreen] / [SearchFeedsScreen] / [SearchPacksScreen].
 *
 * [snackbarHostState] defaults to a fresh remembered state so existing
 * screenshot tests that call [SearchScreenContent] without it continue to
 * compile and run. [discoverState] and [onDiscoverEvent] similarly default to
 * empty values so pre-Task-3 fixtures need no changes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SearchScreenContent(
    textFieldState: TextFieldState,
    isQueryBlank: Boolean,
    currentQuery: String,
    phase: SearchPhase,
    recentSearches: ImmutableList<String>,
    onEvent: (SearchEvent) -> Unit,
    onClearQueryRequest: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTab: SearchTab = SearchTab.POSTS,
    filters: PostSearchFilters = PostSearchFilters(),
    isFilterSheetOpen: Boolean = false,
    postsHitsTotal: Long? = null,
    isAtLeastMedium: Boolean = false,
    onNavigateToActor: (handle: String) -> Unit = {},
    discoverState: DiscoverState = DiscoverState(),
    onDiscoverEvent: (DiscoverEvent) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    searchBarState: SearchBarState = rememberSearchBarState(),
) {
    val onSubmit = remember(onEvent) { { onEvent(SearchEvent.SubmitClicked) } }
    val onChipTap = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipTapped(query)) } }
    val onChipRemove = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipRemoved(query)) } }
    val onClearAll = remember(onEvent) { { onEvent(SearchEvent.ClearAllRecentsClicked) } }

    // Capture onDiscoverEvent in rememberUpdatedState so the LaunchedEffect(phase)
    // below always dispatches to the current callback without restarting.
    val currentOnDiscoverEvent by rememberUpdatedState(onDiscoverEvent)

    val snackScope = rememberCoroutineScope()

    val tabScope = rememberCoroutineScope()

    val searchBarScope = rememberCoroutineScope()

    val tabReTapSignal = LocalTabReTapSignal.current
    LaunchedEffect(tabReTapSignal, searchBarState) {
        tabReTapSignal.collect {
            searchBarScope.launch { searchBarState.animateToExpanded() }
        }
    }

    // Pre-resolve all nine append-error strings via stringResource() so
    // the snackbar lambdas can pick by variant without calling
    // Context.getString() at fire time.
    val postsNetworkMsg = stringResource(R.string.search_posts_append_error_network)
    val postsRateLimitedMsg = stringResource(R.string.search_posts_append_error_rate_limited)
    val postsUnknownMsg = stringResource(R.string.search_posts_append_error_unknown)
    val postsBadQueryMsg = stringResource(R.string.search_posts_append_error_bad_query)
    val peopleNetworkMsg = stringResource(R.string.search_people_append_error_network)
    val peopleRateLimitedMsg = stringResource(R.string.search_people_append_error_rate_limited)
    val peopleUnknownMsg = stringResource(R.string.search_people_append_error_unknown)
    val feedsNetworkMsg = stringResource(R.string.search_feeds_append_error_network)
    val feedsRateLimitedMsg = stringResource(R.string.search_feeds_append_error_rate_limited)
    val feedsUnknownMsg = stringResource(R.string.search_feeds_append_error_unknown)
    val onPostsAppendError =
        remember(snackScope, snackbarHostState, postsNetworkMsg, postsRateLimitedMsg, postsUnknownMsg, postsBadQueryMsg) {
            { error: SearchPostsError ->
                val message =
                    when (error) {
                        SearchPostsError.Network -> postsNetworkMsg
                        SearchPostsError.RateLimited -> postsRateLimitedMsg
                        SearchPostsError.BadQuery -> postsBadQueryMsg
                        is SearchPostsError.Unknown -> postsUnknownMsg
                    }
                snackScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
                Unit
            }
        }
    val packsNetworkMsg = stringResource(R.string.search_packs_append_error_network)
    val packsRateLimitedMsg = stringResource(R.string.search_packs_append_error_rate_limited)
    val packsUnknownMsg = stringResource(R.string.search_packs_append_error_unknown)
    val onActorsAppendError =
        remember(snackScope, snackbarHostState, peopleNetworkMsg, peopleRateLimitedMsg, peopleUnknownMsg) {
            { error: SearchActorsError ->
                val message =
                    when (error) {
                        SearchActorsError.Network -> peopleNetworkMsg
                        SearchActorsError.RateLimited -> peopleRateLimitedMsg
                        is SearchActorsError.Unknown -> peopleUnknownMsg
                    }
                snackScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
                Unit
            }
        }
    val onFeedsAppendError =
        remember(snackScope, snackbarHostState, feedsNetworkMsg, feedsRateLimitedMsg, feedsUnknownMsg) {
            { error: SearchFeedsError ->
                val message =
                    when (error) {
                        SearchFeedsError.Network -> feedsNetworkMsg
                        SearchFeedsError.RateLimited -> feedsRateLimitedMsg
                        is SearchFeedsError.Unknown -> feedsUnknownMsg
                    }
                snackScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
                Unit
            }
        }
    val onPacksAppendError =
        remember(snackScope, snackbarHostState, packsNetworkMsg, packsRateLimitedMsg, packsUnknownMsg) {
            { error: SearchPacksError ->
                val message =
                    when (error) {
                        SearchPacksError.Network -> packsNetworkMsg
                        SearchPacksError.RateLimited -> packsRateLimitedMsg
                        is SearchPacksError.Unknown -> packsUnknownMsg
                    }
                snackScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
                Unit
            }
        }
    // Tracks whether a user-initiated pull-to-refresh is in progress for the
    // Discover section. Starts on user pull, resets when both sections finish
    // loading. This guards the PR indicator from showing during the initial
    // load (before any content exists) — initial loads use the existing
    // section-status Idle → Loading → Loaded lifecycle without showing a spinner.
    var isDiscoverRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(discoverState.trendsStatus, discoverState.accountsStatus, discoverState.feedsStatus) {
        if (discoverState.trendsStatus != DiscoverSectionStatus.Loading &&
            discoverState.accountsStatus != DiscoverSectionStatus.Loading &&
            discoverState.feedsStatus != DiscoverSectionStatus.Loading
        ) {
            isDiscoverRefreshing = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            SearchBarSection(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                isQueryBlank = isQueryBlank,
                currentQuery = currentQuery,
                recentSearches = recentSearches,
                revertTarget = revertTargetFor(phase),
                onSubmit = onSubmit,
                onChipTap = onChipTap,
                onChipRemove = onChipRemove,
                onClearAll = onClearAll,
                isAtLeastMedium = isAtLeastMedium,
                onNavigateToActor = onNavigateToActor,
            )
            when (phase) {
                SearchPhase.Discover -> {
                    // Fire OnAppear whenever the Discover phase becomes active.
                    // Gated inside a LaunchedEffect keyed on the phase so it
                    // re-fires if the user navigates away and back.
                    LaunchedEffect(phase) {
                        currentOnDiscoverEvent(DiscoverEvent.OnAppear)
                    }

                    NubecitaPullToRefreshBox(
                        isRefreshing = isDiscoverRefreshing,
                        onRefresh = {
                            isDiscoverRefreshing = true
                            currentOnDiscoverEvent(DiscoverEvent.OnRefresh)
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    ) {
                        DiscoverLanding(
                            state = discoverState,
                            recentSearches = recentSearches,
                            onChipTap = onChipTap,
                            onChipRemove = onChipRemove,
                            onClearAll = onClearAll,
                            onEvent = onDiscoverEvent,
                            showCards = !isAtLeastMedium,
                        )
                    }
                }
                is SearchPhase.Results -> {
                    val submittedQuery = phase.query
                    val fromRecent = phase.fromRecent
                    val submission = phase.submission
                    // Created per results session, starting on the stored tab,
                    // so a fresh search never inherits the previous pager page.
                    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal, pageCount = { SearchTab.entries.size })
                    val currentOnEvent by rememberUpdatedState(onEvent)
                    // One state machine decides every pager / stored-tab move (see TabSyncState); this
                    // only feeds it events and carries its actions out. A scroll runs in its own job
                    // and an interrupted one ends only that job, so no collector below can die with it.
                    val tabSync = remember(pagerState) { TabSyncHolder() }
                    val currentSelectedTab by rememberUpdatedState(selectedTab)
                    val applyStep: (TabSyncStep) -> Unit = { step ->
                        tabSync.state = step.state
                        when (val action = step.action) {
                            TabSyncAction.None -> Unit
                            is TabSyncAction.ScrollTo ->
                                tabScope.launch {
                                    try {
                                        pagerState.animateScrollToPage(action.tab.ordinal)
                                    } catch (_: CancellationException) {
                                        // Interrupted by a newer scroll or a drag: nothing to undo.
                                    }
                                }
                            is TabSyncAction.Report -> currentOnEvent(SearchEvent.TabSelected(action.tab))
                        }
                    }
                    val currentApplyStep by rememberUpdatedState(applyStep)
                    LaunchedEffect(selectedTab, pagerState) {
                        currentApplyStep(
                            tabSync.state.onStoreTabObserved(selectedTab, SearchTab.entries[pagerState.targetPage]),
                        )
                    }
                    LaunchedEffect(pagerState) {
                        pagerState.interactionSource.interactions.collect { interaction ->
                            if (interaction is DragInteraction.Start) tabSync.state = tabSync.state.onUserDragStarted()
                        }
                    }
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
                            .collect { (page, scrolling) ->
                                if (scrolling) return@collect
                                currentApplyStep(tabSync.state.onSettled(SearchTab.entries[page], currentSelectedTab))
                            }
                    }
                    SearchResultsTabBar(
                        selectedTabIndex = pagerState.currentPage,
                        // A tap is a request like any other: it goes to the store and the pager follows.
                        onSelectTab = { page -> currentOnEvent(SearchEvent.TabSelected(SearchTab.entries[page])) },
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        beyondViewportPageCount = 1,
                    ) { page ->
                        when (page) {
                            0 ->
                                SearchPostsScreen(
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onPostsAppendError,
                                    snackbarHostState = snackbarHostState,
                                    fullScreenRangePicker = !isAtLeastMedium,
                                )
                            1 ->
                                SearchActorsScreen(
                                    currentQuery = submittedQuery,
                                    fromRecent = fromRecent,
                                    submission = submission,
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onActorsAppendError,
                                    snackbarHostState = snackbarHostState,
                                )
                            2 ->
                                SearchFeedsScreen(
                                    currentQuery = submittedQuery,
                                    fromRecent = fromRecent,
                                    submission = submission,
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onFeedsAppendError,
                                    snackbarHostState = snackbarHostState,
                                )
                            3 ->
                                SearchPacksScreen(
                                    currentQuery = submittedQuery,
                                    fromRecent = fromRecent,
                                    submission = submission,
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onPacksAppendError,
                                )
                        }
                    }
                }
            }
        }
        // Hosted here, not in the Posts tab, so any results tab can open it.
        if (isFilterSheetOpen && phase is SearchPhase.Results) {
            PostFiltersSheet(
                filters = filters,
                hitsTotal = postsHitsTotal,
                fullScreenRangePicker = !isAtLeastMedium,
                onFiltersChange = { onEvent(SearchEvent.FiltersChanged(it)) },
                onReset = { onEvent(SearchEvent.FiltersChanged(PostSearchFilters(sort = filters.sort))) },
                onDismiss = { onEvent(SearchEvent.FilterSheetOpenChanged(false)) },
            )
        }
    }
}

/**
 * Extracted from [SearchScreenContent]'s tab branch so the tab strip is
 * independently screenshot-testable without standing up the Hilt graph
 * required by the per-tab Screens. Visibility is `internal` (rather
 * than `private`) so the screenshotTest source set can render it
 * directly with stub callbacks.
 */
@Composable
internal fun SearchResultsTabBar(
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SecondaryTabRow(selectedTabIndex = selectedTabIndex, modifier = modifier) {
        Tab(
            selected = selectedTabIndex == 0,
            onClick = { onSelectTab(0) },
            text = { Text(stringResource(R.string.search_tab_posts)) },
        )
        Tab(
            selected = selectedTabIndex == 1,
            onClick = { onSelectTab(1) },
            text = { Text(stringResource(R.string.search_tab_people)) },
        )
        Tab(
            selected = selectedTabIndex == 2,
            onClick = { onSelectTab(2) },
            text = { Text(stringResource(R.string.search_tab_feeds)) },
        )
        Tab(
            selected = selectedTabIndex == 3,
            onClick = { onSelectTab(3) },
            text = { Text(stringResource(R.string.search_tab_packs)) },
        )
    }
}
