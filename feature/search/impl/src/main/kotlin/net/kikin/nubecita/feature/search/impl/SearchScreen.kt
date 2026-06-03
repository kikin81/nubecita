package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalTabReTapSignal
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.feature.search.impl.ui.RecentSearchChipStrip

/**
 * Stateful Search tab home. Hoists [SearchViewModel] and forwards
 * its state + a small set of stable lambdas down to
 * [SearchScreenContent]. The per-tab Screens (Posts / People) are
 * mounted inside the stateless body's [HorizontalPager] gated on
 * `!isQueryBlank`.
 *
 * `onClearQueryRequest` mutates [SearchViewModel.textFieldState]
 * directly via the editor-VM exception (CLAUDE.md). Routing this
 * through a `SearchEvent` would add a hop for no benefit — the
 * canonical clear path is the same `textFieldState.clearText()`
 * that [SearchInputRow]'s trailing X already calls.
 *
 * Suppresses VM-forwarding lints — see ComposerScreen / ProfileScreen
 * for the full rationale (slack compose-lints 1.5.0+ tightened
 * ComposeViewModelForwarding's data-flow analysis; conflicts with
 * ComposeViewModelInjection on stateful screens that hoist state).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Remembered bound-method reference so SearchScreenContent's onEvent
    // parameter stays stable across recompositions. Mirrors the
    // `:feature:chats:impl` pattern (`onEvent = viewModel::handleEvent` via
    // a remembered reference).
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    val onClearQueryRequest =
        remember(viewModel) {
            { viewModel.textFieldState.clearText() }
        }
    // Width class drives the expanded-search container (full-screen vs docked).
    // Computed here at the stateful boundary so the stateless body and its
    // screenshot tests stay width-independent (they use the `false` default).
    val isAtLeastMedium =
        currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    SearchScreenContent(
        textFieldState = viewModel.textFieldState,
        isQueryBlank = state.isQueryBlank,
        currentQuery = state.currentQuery,
        phase = state.phase,
        recentSearches = state.recentSearches,
        onEvent = onEvent,
        onClearQueryRequest = onClearQueryRequest,
        isAtLeastMedium = isAtLeastMedium,
        modifier = modifier,
    )
}

/**
 * Stateless screen body. Extracted so preview / screenshot-test
 * composables can drive the layout without a Hilt-graph dependency
 * on [SearchViewModel]. The single [onEvent] callback is the stable
 * dispatch seam; per-component callbacks are derived once via
 * `remember` so the leaf composables ([SearchInputRow],
 * [RecentSearchChipStrip]) keep their narrow contracts without paying
 * for unstable lambda allocations.
 *
 * Layout: a [Scaffold] hosts a [SnackbarHost] for per-tab append
 * errors. Inside the Scaffold a [Column] renders the [SearchBarSection]
 * (the collapsed pill + the expanded typing overlay) followed by a body
 * branched on [phase]:
 *  - [SearchPhase.Discover]: [RecentSearchChipStrip] when there are
 *    recents, otherwise nothing.
 *  - [SearchPhase.Results]: [SecondaryTabRow] + [HorizontalPager]
 *    hosting [SearchPostsScreen] (page 0), [SearchActorsScreen]
 *    (page 1), and [SearchFeedsScreen] (page 2).
 *    `beyondViewportPageCount = 1` keeps adjacent per-tab VMs alive
 *    across tab switches so results are preserved.
 *
 * The mid-query typeahead surface ([SearchTypeaheadScreen]) is no longer
 * a body phase — it renders inside [SearchBarSection]'s expanded overlay.
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
) {
    val onSubmit = remember(onEvent) { { onEvent(SearchEvent.SubmitClicked) } }
    val onChipTap = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipTapped(query)) } }
    val onChipRemove = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipRemoved(query)) } }
    val onClearAll = remember(onEvent) { { onEvent(SearchEvent.ClearAllRecentsClicked) } }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val tabScope = rememberCoroutineScope()

    // The expand/collapse state of the search bar. Owned here (not the VM):
    // it is Compose-runtime animation state, like scroll position. Defaults
    // to Collapsed.
    val searchBarState = rememberSearchBarState()
    val searchBarScope = rememberCoroutineScope()

    // Search-tab re-tap: expand the search bar (which focuses the field and
    // pops the soft keyboard). MainShell emits on `LocalTabReTapSignal`
    // whenever the user taps the active bottom-nav item; the default empty
    // signal (no provider in previews / screenshot tests) never emits so
    // this is a runtime no-op in those contexts. Issue #267 / nubecita-vrba.13.
    val tabReTapSignal = LocalTabReTapSignal.current
    LaunchedEffect(tabReTapSignal, searchBarState) {
        tabReTapSignal.collect {
            searchBarScope.launch { searchBarState.animateToExpanded() }
        }
    }

    // Pre-resolve all nine append-error strings via stringResource() so
    // the snackbar lambdas can pick by variant without calling
    // Context.getString() at fire time. The Compose lint rule
    // `LocalContextGetResourceValueCall` flags any in-Composable
    // Context.getString call (even when captured in a deferred
    // lambda — the static analyzer doesn't track invocation timing).
    val postsNetworkMsg = stringResource(R.string.search_posts_append_error_network)
    val postsRateLimitedMsg = stringResource(R.string.search_posts_append_error_rate_limited)
    val postsUnknownMsg = stringResource(R.string.search_posts_append_error_unknown)
    val peopleNetworkMsg = stringResource(R.string.search_people_append_error_network)
    val peopleRateLimitedMsg = stringResource(R.string.search_people_append_error_rate_limited)
    val peopleUnknownMsg = stringResource(R.string.search_people_append_error_unknown)
    val feedsNetworkMsg = stringResource(R.string.search_feeds_append_error_network)
    val feedsRateLimitedMsg = stringResource(R.string.search_feeds_append_error_rate_limited)
    val feedsUnknownMsg = stringResource(R.string.search_feeds_append_error_unknown)
    // PostCard overflow-menu coming-soon snackbars (oftc.2).
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

    // Dismiss-then-show on each snackbar emission rather than queueing,
    // matching the convention established in :feature:feed:impl/FeedScreen
    // and :feature:postdetail:impl/PostDetailScreen. Repeated transient
    // failures on a flapping connection (`onPagerSwipe` typically fires
    // bursts) shouldn't pile up an unread queue — the latest error is the
    // most useful one to surface.
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
    // Surface PostCard overflow-menu coming-soon snackbars on the search
    // screen's SnackbarHostState (same surface as the append-error path).
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
                // Reverting an unsubmitted edit on collapse targets the last
                // submitted query (Results) or blank (Discover), so the pill
                // text and the body stay in sync.
                revertTarget = revertTargetFor(phase),
                onSubmit = onSubmit,
                onChipTap = onChipTap,
                onChipRemove = onChipRemove,
                onClearAll = onClearAll,
                isAtLeastMedium = isAtLeastMedium,
            )
            when (phase) {
                SearchPhase.Discover ->
                    if (recentSearches.isNotEmpty()) {
                        RecentSearchChipStrip(
                            items = recentSearches,
                            onChipTap = onChipTap,
                            onChipRemove = onChipRemove,
                            onClearAll = onClearAll,
                        )
                    }
                is SearchPhase.Results -> {
                    // The result tabs query the *submitted* query, never the live
                    // text. Editing in the expanded overlay updates `currentQuery`
                    // (for the overlay typeahead) while these tabs stay composed
                    // underneath — feeding them `currentQuery` would re-run the
                    // search for unsubmitted text. `phase.query` is the last
                    // submitted query.
                    val submittedQuery = phase.query
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
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onPostsAppendError,
                                    onShowOverflowComingSoon = onPostsOverflowComingSoon,
                                )
                            1 ->
                                SearchActorsScreen(
                                    currentQuery = submittedQuery,
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onActorsAppendError,
                                )
                            2 ->
                                SearchFeedsScreen(
                                    currentQuery = submittedQuery,
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
