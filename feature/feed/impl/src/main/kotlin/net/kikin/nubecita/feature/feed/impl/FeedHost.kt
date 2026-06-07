package net.kikin.nubecita.feature.feed.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi

/**
 * Host for the main Feed's per-feed switcher. Renders the active feed as a
 * [FeedPane] whose [FeedViewModel] is keyed by `feedUri` (retained in the
 * Feed nav entry's `ViewModelStore`) and wrapped in a
 * `SaveableStateHolder` (retains scroll). Switching away and back restores
 * posts, cursor, and scroll with no re-fetch; only the active pane is
 * composed.
 *
 * The nav callbacks are threaded straight through to each pane's
 * [FeedScreen] — the host adds the feed-switcher chrome around the existing
 * screen without changing its navigation contract. `FeedNavigationModule`
 * wires them once at the `@MainShell` entry exactly as it did for the bare
 * `FeedScreen` before this host existed.
 */
@Composable
internal fun FeedHost(
    modifier: Modifier = Modifier,
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit = { _, _ -> },
    onNavigateToVideoPlayer: (postUri: String) -> Unit = {},
    onNavigateTo: (NavKey) -> Unit = {},
    onComposeClick: () -> Unit = {},
    onReplyClick: (String) -> Unit = {},
    onQuoteClick: (String) -> Unit = {},
    hostViewModel: FeedHostViewModel = hiltViewModel(),
) {
    val state by hostViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-resolve snackbar copy at composition time so locale changes
    // participate in recomposition (VM stays Android-resource-free).
    val feedsFallbackMessage = stringResource(R.string.feed_host_snackbar_feeds_fallback)
    LaunchedEffect(Unit) {
        hostViewModel.effects.collect { effect ->
            when (effect) {
                FeedHostEffect.ShowError -> snackbarHostState.showSnackbar(feedsFallbackMessage)
            }
        }
    }

    // Resolve the active (uri, kind) from the selection. Falls back to the
    // first Following chip so the pane is always bindable even mid-load.
    val activeFeed =
        remember(state.selectedFeedUri, state.feedChips, state.pinnedLists) {
            val all = state.feedChips + state.pinnedLists
            all.firstOrNull { it.uri == state.selectedFeedUri }
                ?: all.firstOrNull { it.kind == FeedKind.Following }
                ?: all.firstOrNull()
        }

    val stateHolder = rememberSaveableStateHolder()

    Box(modifier = modifier.fillMaxSize()) {
        if (activeFeed != null) {
            stateHolder.SaveableStateProvider(activeFeed.uri) {
                FeedPane(
                    feedUri = activeFeed.uri,
                    kind = activeFeed.kind,
                    feedChips = state.feedChips,
                    pinnedLists = state.pinnedLists,
                    selectedFeedUri = state.selectedFeedUri,
                    status = state.status,
                    onSelectFeed = { hostViewModel.handleEvent(FeedHostEvent.SelectFeed(it)) },
                    onSelectList = { hostViewModel.handleEvent(FeedHostEvent.SelectList(it)) },
                    onRetry = { hostViewModel.handleEvent(FeedHostEvent.Retry) },
                    onNavigateToPost = onNavigateToPost,
                    onNavigateToAuthor = onNavigateToAuthor,
                    onNavigateToMediaViewer = onNavigateToMediaViewer,
                    onNavigateToVideoPlayer = onNavigateToVideoPlayer,
                    onNavigateTo = onNavigateTo,
                    onComposeClick = onComposeClick,
                    onReplyClick = onReplyClick,
                    onQuoteClick = onQuoteClick,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }
}

/**
 * A single feed pane. Its [FeedViewModel] is keyed by [feedUri] so it is
 * retained across feed switches; [FeedEvent.Bind] is dispatched once per
 * pane (the VM no-ops a re-bind to the same feed, so no re-fetch on
 * return). Nav callbacks pass straight through to the pane's [FeedScreen].
 *
 * Suppresses the VM-forwarding lints for the same reason [FeedScreen] does:
 * the pane intentionally hands its retained [FeedViewModel] (keyed on
 * `feedUri` for per-feed retention) to the stateful [FeedScreen]. The
 * compose-lints data-flow heuristic flags the forward, but hoisting the VM
 * higher would break the per-`feedUri` retention this host exists to provide.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
private fun FeedPane(
    feedUri: String,
    kind: FeedKind,
    feedChips: ImmutableList<PinnedFeedUi>,
    pinnedLists: ImmutableList<PinnedFeedUi>,
    selectedFeedUri: String?,
    status: FeedHostStatus,
    onSelectFeed: (String) -> Unit,
    onSelectList: (String) -> Unit,
    onRetry: () -> Unit,
    onNavigateToPost: (String) -> Unit,
    onNavigateToAuthor: (String) -> Unit,
    onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit,
    onNavigateToVideoPlayer: (postUri: String) -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    onComposeClick: () -> Unit,
    onReplyClick: (String) -> Unit,
    onQuoteClick: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    // hiltViewModel acquisition lives in the default param value (keyed on
    // the preceding `feedUri`) per the compose-lints ComposeViewModelInjection
    // rule — keeps the pane VM injectable/overridable from tests + previews.
    feedViewModel: FeedViewModel = hiltViewModel(key = feedUri),
) {
    LaunchedEffect(feedUri) {
        feedViewModel.handleEvent(FeedEvent.Bind(feedUri, kind))
    }
    FeedScreen(
        feedChips = feedChips,
        pinnedLists = pinnedLists,
        selectedFeedUri = selectedFeedUri,
        status = status,
        onSelectFeed = onSelectFeed,
        onSelectList = onSelectList,
        onRetry = onRetry,
        onNavigateToPost = onNavigateToPost,
        onNavigateToAuthor = onNavigateToAuthor,
        onNavigateToMediaViewer = onNavigateToMediaViewer,
        onNavigateToVideoPlayer = onNavigateToVideoPlayer,
        onNavigateTo = onNavigateTo,
        onComposeClick = onComposeClick,
        onReplyClick = onReplyClick,
        onQuoteClick = onQuoteClick,
        snackbarHostState = snackbarHostState,
        viewModel = feedViewModel,
    )
}
