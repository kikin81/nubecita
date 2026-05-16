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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.feature.search.impl.ui.RecentSearchChipStrip
import net.kikin.nubecita.feature.search.impl.ui.SearchInputRow

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
 */
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
    SearchScreenContent(
        textFieldState = viewModel.textFieldState,
        isQueryBlank = state.isQueryBlank,
        currentQuery = state.currentQuery,
        phase = state.phase,
        recentSearches = state.recentSearches,
        onEvent = onEvent,
        onClearQueryRequest = onClearQueryRequest,
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
 * errors. Inside the Scaffold a [Column] renders the input row
 * followed by a body branched on [phase]:
 *  - [SearchPhase.Discover]: [RecentSearchChipStrip] when there are
 *    recents, otherwise nothing.
 *  - [SearchPhase.Typeahead]: [SearchTypeaheadScreen] (vrba.10) — the
 *    mid-query grouped suggestions surface with a "Search for {q}"
 *    CTA at the top.
 *  - [SearchPhase.Results]: [SecondaryTabRow] + [HorizontalPager]
 *    hosting [SearchPostsScreen] (page 0), [SearchActorsScreen]
 *    (page 1), and [SearchFeedsScreen] (page 2).
 *    `beyondViewportPageCount = 1` keeps adjacent per-tab VMs alive
 *    across tab switches so results are preserved.
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
) {
    val onSubmit = remember(onEvent) { { onEvent(SearchEvent.SubmitClicked) } }
    val onChipTap = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipTapped(query)) } }
    val onChipRemove = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipRemoved(query)) } }
    val onClearAll = remember(onEvent) { { onEvent(SearchEvent.ClearAllRecentsClicked) } }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val tabScope = rememberCoroutineScope()

    // Pre-resolve all six append-error strings via stringResource() so
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

    val onPostsAppendError =
        remember(snackScope, snackbarHostState, postsNetworkMsg, postsRateLimitedMsg, postsUnknownMsg) {
            { error: SearchPostsError ->
                val message =
                    when (error) {
                        SearchPostsError.Network -> postsNetworkMsg
                        SearchPostsError.RateLimited -> postsRateLimitedMsg
                        is SearchPostsError.Unknown -> postsUnknownMsg
                    }
                snackScope.launch { snackbarHostState.showSnackbar(message) }
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
                snackScope.launch { snackbarHostState.showSnackbar(message) }
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
                snackScope.launch { snackbarHostState.showSnackbar(message) }
                Unit
            }
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            SearchInputRow(
                textFieldState = textFieldState,
                isQueryBlank = isQueryBlank,
                onSubmit = onSubmit,
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
                is SearchPhase.Typeahead ->
                    SearchTypeaheadScreen(
                        currentQuery = phase.query,
                        onCommitQuery = onSubmit,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    )
                is SearchPhase.Results -> {
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
                                    currentQuery = currentQuery,
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onPostsAppendError,
                                )
                            1 ->
                                SearchActorsScreen(
                                    currentQuery = currentQuery,
                                    onClearQuery = onClearQueryRequest,
                                    onShowAppendError = onActorsAppendError,
                                )
                            2 ->
                                SearchFeedsScreen(
                                    currentQuery = currentQuery,
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
