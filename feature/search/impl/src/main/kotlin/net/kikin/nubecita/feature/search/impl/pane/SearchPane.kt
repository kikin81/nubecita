package net.kikin.nubecita.feature.search.impl.pane

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.feature.feed.api.FeedView
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.impl.DiscoverEffect
import net.kikin.nubecita.feature.search.impl.DiscoverEvent
import net.kikin.nubecita.feature.search.impl.DiscoverViewModel
import net.kikin.nubecita.feature.search.impl.R

/**
 * The Search tab's second pane on Medium/Expanded windows, shown until something is opened
 * in it. Stateful: it is composed in the list-detail scene's placeholder slot, which sits
 * OUTSIDE the Search entry, so both ViewModels below are scoped to the logged-in shell
 * entry (a second [DiscoverViewModel] instance beside the Search screen's own) and nothing
 * here may rely on `rememberSaveable`: the slot loses it whenever the pane leaves
 * composition. What must survive (scroll positions, "Show more" toggles) lives in
 * [SearchPaneViewModel].
 *
 * In Explore mode, follow and pin go through the pane's [DiscoverViewModel] exactly as on the
 * landing; in Summary mode the rows' follow, pin and navigation go through [SearchPaneViewModel].
 * Both ViewModels' effects are collected here: navigation goes to [LocalMainShellNavState] (provided by
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
    val followErrorMsg = stringResource(R.string.search_follow_error)
    val unfollowErrorMsg = stringResource(R.string.search_unfollow_error)
    val pinErrorMsg = stringResource(R.string.search_pin_error)
    val unpinErrorMsg = stringResource(R.string.search_unpin_error)
    LaunchedEffect(viewModel, navState, snackbarHostState) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // replaceTop, as the Posts tab does: a post already open in the detail pane is
                // swapped for the tapped one, so back returns to the Summary and not to a stack
                // of threads. At the tab's home it degrades to a plain push.
                is SearchPaneEffect.NavigateToPost -> navState.replaceTop(PostDetailRoute(postUri = effect.uri))
                is SearchPaneEffect.NavigateToProfile -> navState.add(Profile(handle = effect.handle))
                is SearchPaneEffect.NavigateToFeed ->
                    navState.add(FeedView(feedUri = effect.uri, displayName = effect.displayName))
                is SearchPaneEffect.FollowFailed -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(if (effect.wasUnfollow) unfollowErrorMsg else followErrorMsg)
                }
                is SearchPaneEffect.PinFailed -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(if (effect.wasUnpin) unpinErrorMsg else pinErrorMsg)
                }
            }
        }
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
    // The Summary's list is also keyed on the searched query and filters, so a new search starts
    // at the top instead of keeping the old list's scroll (and later overwriting the reset).
    val listKey = paneState.summaryKey.takeIf { target == ScrollTarget.Summary }
    val listState =
        remember(target, listKey) {
            val saved = if (target == ScrollTarget.Summary) paneState.summaryScroll else paneState.exploreScroll
            LazyListState(saved.index, saved.offset)
        }
    DisposableEffect(listState, target) {
        onDispose {
            onPaneEvent(
                SearchPaneEvent.ScrollSaved(
                    position = ScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset),
                    target = target,
                    summaryKey = listKey,
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
        // Explore and the Summary each render on their own list state (see `listState` above).
        // The list's viewport starts below the status bar / cutout and inside the side cutouts
        // (container padding), so scrolled rows are clipped there instead of passing under the
        // status-bar icons; only the bottom inset stays as content padding so the last row can
        // scroll clear of the navigation bar. `innerPadding` is the Scaffold's own (safeDrawing
        // minus whatever the enclosing scaffold already consumed), applied exactly once.
        val layoutDirection = LocalLayoutDirection.current
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                ),
        ) {
            // The pane's own width decides the feed columns (the user can drag the divider).
            val feedColumns = feedColumnsFor(maxWidth)
            val contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
            when (val mode = paneState.mode) {
                SearchPaneMode.Explore ->
                    ExplorePaneContent(
                        state = discoverState,
                        feedColumns = feedColumns,
                        feedsExpanded = paneState.feedsExpanded,
                        accountsExpanded = paneState.accountsExpanded,
                        onDiscoverEvent = onDiscoverEvent,
                        onToggleFeeds = onToggleFeeds,
                        onToggleAccounts = onToggleAccounts,
                        listState = listState,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                is SearchPaneMode.Summary ->
                    SummaryPaneContent(
                        summary = mode,
                        viewerDid = paneState.viewerDid,
                        onEvent = onPaneEvent,
                        listState = listState,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
    }
}
