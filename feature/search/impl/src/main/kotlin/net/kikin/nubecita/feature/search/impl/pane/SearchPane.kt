package net.kikin.nubecita.feature.search.impl.pane

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.feature.feed.api.FeedView
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.impl.DiscoverEffect
import net.kikin.nubecita.feature.search.impl.DiscoverEvent
import net.kikin.nubecita.feature.search.impl.DiscoverViewModel

/**
 * The Search tab's second pane on Medium/Expanded windows, shown until something is opened
 * in it. Stateful: it is composed in the list-detail scene's placeholder slot, which sits
 * OUTSIDE the Search entry, so both ViewModels below are scoped to the logged-in shell
 * entry (a second [DiscoverViewModel] instance beside the Search screen's own) and nothing
 * here may rely on `rememberSaveable`: the slot loses it whenever the pane leaves
 * composition. What must survive (scroll positions, "Show more" toggles) lives in
 * [SearchPaneViewModel].
 *
 * Follow and pin go through the pane's [DiscoverViewModel] exactly as on the landing.
 * Its effects are collected here: navigation goes to [LocalMainShellNavState] (provided by
 * `MainShell` around the whole `NavDisplay`, so it is in scope in the slot), and errors
 * show in the pane's own snackbar host because the shell's host is not exposed to content.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun SearchPane(
    modifier: Modifier = Modifier,
    viewModel: SearchPaneViewModel = hiltViewModel(),
    discoverViewModel: DiscoverViewModel = hiltViewModel(),
) {
    val paneState by viewModel.uiState.collectAsStateWithLifecycle()
    val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()
    val onPaneEvent = remember(viewModel) { viewModel::handleEvent }
    val onDiscoverEvent = remember(discoverViewModel) { discoverViewModel::handleEvent }
    val navState = LocalMainShellNavState.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(discoverViewModel) {
        discoverViewModel.handleEvent(DiscoverEvent.OnAppear)
    }
    LaunchedEffect(discoverViewModel, navState, snackbarHostState) {
        discoverViewModel.effects.collect { effect ->
            when (effect) {
                is DiscoverEffect.NavigateToProfile -> navState.add(Profile(handle = effect.handle))
                is DiscoverEffect.NavigateToFeed ->
                    navState.add(FeedView(feedUri = effect.uri, displayName = effect.displayName))
                // Only a trend tap emits this, and the pane shows no trends (they live in the list pane).
                is DiscoverEffect.SubmitSearch -> Unit
                is DiscoverEffect.ShowError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    // One list state per mode, created at the position the ViewModel holds and handed back when
    // the list leaves composition (a tab switch, a fold, a detail opening, or the mode changing).
    // Keyed by mode so switching mode never reuses the other mode's position.
    val target =
        if (paneState.mode is SearchPaneMode.Summary) ScrollTarget.Summary else ScrollTarget.Explore
    val listState =
        remember(target) {
            val saved = if (target == ScrollTarget.Summary) paneState.summaryScroll else paneState.exploreScroll
            LazyListState(saved.index, saved.offset)
        }
    DisposableEffect(listState, target) {
        onDispose {
            onPaneEvent(
                SearchPaneEvent.ScrollSaved(
                    position = ScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset),
                    target = target,
                ),
            )
        }
    }

    val onToggleFeeds = remember(onPaneEvent) { { onPaneEvent(SearchPaneEvent.FeedsExpansionToggled) } }
    val onToggleAccounts = remember(onPaneEvent) { { onPaneEvent(SearchPaneEvent.AccountsExpansionToggled) } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // Task 12 renders the search summary for SearchPaneMode.Summary; until then both modes
        // show the Explore content, each on its own list state.
        ExplorePaneContent(
            state = discoverState,
            feedsExpanded = paneState.feedsExpanded,
            accountsExpanded = paneState.accountsExpanded,
            onDiscoverEvent = onDiscoverEvent,
            onToggleFeeds = onToggleFeeds,
            onToggleAccounts = onToggleAccounts,
            listState = listState,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
