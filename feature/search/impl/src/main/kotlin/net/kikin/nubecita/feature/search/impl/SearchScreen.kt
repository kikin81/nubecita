package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.LocalTabReTapSignal
import net.kikin.nubecita.designsystem.component.NubecitaPullToRefreshBox
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.feature.feed.api.FeedView
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.impl.ui.DiscoverSections
import net.kikin.nubecita.feature.search.impl.ui.RecentSearchChipStrip

/**
 * Stateful Search tab home. Hoists [SearchViewModel] and [DiscoverViewModel]
 * and forwards their state down to [SearchScreenContent]. The per-tab Screens
 * (Posts / People / Feeds) are mounted inside the stateless body's
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        recentSearches = state.recentSearches,
        onEvent = onEvent,
        onClearQueryRequest = onClearQueryRequest,
        isAtLeastMedium = isAtLeastMedium,
        onNavigateToActor = onNavigateToActor,
        discoverState = discoverState,
        onDiscoverEvent = onDiscoverEvent,
        snackbarHostState = snackbarHostState,
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
 *  - [SearchPhase.Discover]: a [NubecitaPullToRefreshBox] wrapping a
 *    [LazyColumn] with:
 *    — [RecentSearchChipStrip] when there are recents,
 *    — [DiscoverSections] (accounts carousel + feeds carousel) when
 *      the respective sections are [DiscoverSectionStatus.Loaded],
 *    — an empty-state hint when all sections are empty/error and no recents.
 *  - [SearchPhase.Results]: [SecondaryTabRow] + [HorizontalPager] hosting
 *    [SearchPostsScreen] / [SearchActorsScreen] / [SearchFeedsScreen].
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
    isAtLeastMedium: Boolean = false,
    onNavigateToActor: (handle: String) -> Unit = {},
    discoverState: DiscoverState = DiscoverState(),
    onDiscoverEvent: (DiscoverEvent) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val onSubmit = remember(onEvent) { { onEvent(SearchEvent.SubmitClicked) } }
    val onChipTap = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipTapped(query)) } }
    val onChipRemove = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipRemoved(query)) } }
    val onClearAll = remember(onEvent) { { onEvent(SearchEvent.ClearAllRecentsClicked) } }

    // Capture onDiscoverEvent in rememberUpdatedState so the LaunchedEffect(phase)
    // below always dispatches to the current callback without restarting.
    val currentOnDiscoverEvent by rememberUpdatedState(onDiscoverEvent)

    val snackScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val tabScope = rememberCoroutineScope()

    val searchBarState = rememberSearchBarState()
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
    val peopleNetworkMsg = stringResource(R.string.search_people_append_error_network)
    val peopleRateLimitedMsg = stringResource(R.string.search_people_append_error_rate_limited)
    val peopleUnknownMsg = stringResource(R.string.search_people_append_error_unknown)
    val feedsNetworkMsg = stringResource(R.string.search_feeds_append_error_network)
    val feedsRateLimitedMsg = stringResource(R.string.search_feeds_append_error_rate_limited)
    val feedsUnknownMsg = stringResource(R.string.search_feeds_append_error_unknown)
    val overflowReportComingSoon =
        stringResource(R.string.search_snackbar_overflow_report_coming_soon)
    val overflowMuteComingSoon =
        stringResource(R.string.search_snackbar_overflow_mute_coming_soon)
    val overflowUnmuteComingSoon =
        stringResource(R.string.search_snackbar_overflow_unmute_coming_soon)
    val overflowBlockComingSoon =
        stringResource(R.string.search_snackbar_overflow_block_coming_soon)
    val overflowUnblockComingSoon =
        stringResource(R.string.search_snackbar_overflow_unblock_coming_soon)
    val overflowMuteThreadComingSoon =
        stringResource(R.string.search_snackbar_overflow_mute_thread_coming_soon)
    val overflowUnmuteThreadComingSoon =
        stringResource(R.string.search_snackbar_overflow_unmute_thread_coming_soon)
    val overflowCopyTextComingSoon =
        stringResource(R.string.search_snackbar_overflow_copy_text_coming_soon)

    val onPostsAppendError =
        remember(snackScope, snackbarHostState, postsNetworkMsg, postsRateLimitedMsg, postsUnknownMsg) {
            { error: SearchPostsError ->
                val message =
                    when (error) {
                        SearchPostsError.Network -> postsNetworkMsg
                        SearchPostsError.RateLimited -> postsRateLimitedMsg
                        is SearchPostsError.Unknown -> postsUnknownMsg
                    }
                snackScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
                Unit
            }
        }
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
    val onPostsOverflowComingSoon =
        remember(
            snackScope,
            snackbarHostState,
            overflowReportComingSoon,
            overflowMuteComingSoon,
            overflowUnmuteComingSoon,
            overflowBlockComingSoon,
            overflowUnblockComingSoon,
            overflowMuteThreadComingSoon,
            overflowUnmuteThreadComingSoon,
            overflowCopyTextComingSoon,
        ) {
            { action: PostOverflowAction ->
                val message =
                    when (action) {
                        PostOverflowAction.ReportPost -> overflowReportComingSoon
                        PostOverflowAction.MuteAuthor -> overflowMuteComingSoon
                        PostOverflowAction.UnmuteAuthor -> overflowUnmuteComingSoon
                        PostOverflowAction.BlockAuthor -> overflowBlockComingSoon
                        PostOverflowAction.UnblockAuthor -> overflowUnblockComingSoon
                        PostOverflowAction.MuteThread -> overflowMuteThreadComingSoon
                        PostOverflowAction.UnmuteThread -> overflowUnmuteThreadComingSoon
                        PostOverflowAction.CopyPostText -> overflowCopyTextComingSoon
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
    LaunchedEffect(discoverState.accountsStatus, discoverState.feedsStatus) {
        if (discoverState.accountsStatus != DiscoverSectionStatus.Loading &&
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

                    val showEmptyHint =
                        (
                            discoverState.accountsStatus is DiscoverSectionStatus.Empty ||
                                discoverState.accountsStatus is DiscoverSectionStatus.Error
                        ) &&
                            (
                                discoverState.feedsStatus is DiscoverSectionStatus.Empty ||
                                    discoverState.feedsStatus is DiscoverSectionStatus.Error
                            ) &&
                            recentSearches.isEmpty()

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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (recentSearches.isNotEmpty()) {
                                item(key = "recents") {
                                    RecentSearchChipStrip(
                                        items = recentSearches,
                                        onChipTap = onChipTap,
                                        onChipRemove = onChipRemove,
                                        onClearAll = onClearAll,
                                    )
                                }
                            }
                            if (discoverState.accountsStatus == DiscoverSectionStatus.Loaded ||
                                discoverState.feedsStatus == DiscoverSectionStatus.Loaded
                            ) {
                                item(key = "discover_sections") {
                                    DiscoverSections(
                                        state = discoverState,
                                        onEvent = onDiscoverEvent,
                                    )
                                }
                            }
                            if (showEmptyHint) {
                                item(key = "empty_hint") {
                                    DiscoverEmptyHint(
                                        modifier =
                                            Modifier
                                                .fillParentMaxSize()
                                                .padding(horizontal = 32.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                is SearchPhase.Results -> {
                    val submittedQuery = phase.query
                    val fromRecent = phase.fromRecent
                    SearchResultsTabBar(
                        selectedTabIndex = pagerState.currentPage,
                        onSelectTab = { page -> tabScope.launch { pagerState.animateScrollToPage(page) } },
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
                                    currentQuery = submittedQuery,
                                    fromRecent = fromRecent,
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onPostsAppendError,
                                    onShowOverflowComingSoon = onPostsOverflowComingSoon,
                                )
                            1 ->
                                SearchActorsScreen(
                                    currentQuery = submittedQuery,
                                    fromRecent = fromRecent,
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onActorsAppendError,
                                )
                            2 ->
                                SearchFeedsScreen(
                                    currentQuery = submittedQuery,
                                    fromRecent = fromRecent,
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onFeedsAppendError,
                                )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty-state hint shown when the Discover body has no accounts, no feeds,
 * and no recent searches. Prompts the user to pull to refresh.
 */
@Composable
private fun DiscoverEmptyHint(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.discover_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
    }
}
