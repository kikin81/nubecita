package net.kikin.nubecita.feature.feed.impl

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 *
 * The real chip row (`FeedChipRow` + scroll-away + lists bottom sheet) is
 * a580.8. This host already owns `selectedFeedUri` and the
 * `SelectFeed`/`SelectList` seam; a580.8 drops the chip row in place of the
 * provisional selector below.
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
    // The selector renders only when there is more than one feed/list to
    // switch between (see ProvisionalFeedSelector's early return). When it
    // does, it clears the status bar for itself via statusBarsPadding, so the
    // pane below must NOT re-pad the same inset.
    val hasSelector = state.feedChips.size + state.pinnedLists.size > 1

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ProvisionalFeedSelector(
                feedChips = state.feedChips,
                pinnedLists = state.pinnedLists,
                selectedUri = activeFeed?.uri,
                onSelectFeed = { hostViewModel.handleEvent(FeedHostEvent.SelectFeed(it)) },
                onSelectList = { hostViewModel.handleEvent(FeedHostEvent.SelectList(it)) },
            )

            // Only the active pane is composed; other feeds' ViewModels stay
            // retained (keyed by feedUri) but un-composed.
            if (activeFeed != null) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            // When the selector is shown above this pane it has
                            // already consumed the status-bar space; mark that
                            // inset consumed so the pane's FeedScreen Scaffold
                            // (contentWindowInsets = systemBars) doesn't pad the
                            // status bar a SECOND time, which would leave a
                            // phantom gap above the first post. With no selector
                            // (single feed) the inset is left intact so
                            // FeedScreen keeps its edge-to-edge
                            // scroll-behind-status-bar behavior unchanged.
                            .then(
                                if (hasSelector) {
                                    Modifier.consumeWindowInsets(WindowInsets.statusBars)
                                } else {
                                    Modifier
                                },
                            ),
                ) {
                    stateHolder.SaveableStateProvider(activeFeed.uri) {
                        FeedPane(
                            feedUri = activeFeed.uri,
                            kind = activeFeed.kind,
                            onNavigateToPost = onNavigateToPost,
                            onNavigateToAuthor = onNavigateToAuthor,
                            onNavigateToMediaViewer = onNavigateToMediaViewer,
                            onNavigateToVideoPlayer = onNavigateToVideoPlayer,
                            onNavigateTo = onNavigateTo,
                            onComposeClick = onComposeClick,
                            onReplyClick = onReplyClick,
                        )
                    }
                }
            }
        }

        // Host-level snackbar for the feeds-fallback notice (a concern the
        // nested FeedScreen can't surface). Inset past the navigation bar +
        // IME so it isn't occluded; it shows rarely and transiently, so the
        // rare overlap with FeedScreen's own snackbar is acceptable for this
        // provisional host (a580.8 consolidates snackbar ownership).
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding(),
        )
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
    onNavigateToPost: (String) -> Unit,
    onNavigateToAuthor: (String) -> Unit,
    onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit,
    onNavigateToVideoPlayer: (postUri: String) -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    onComposeClick: () -> Unit,
    onReplyClick: (String) -> Unit,
    // hiltViewModel acquisition lives in the default param value (keyed on
    // the preceding `feedUri`) per the compose-lints ComposeViewModelInjection
    // rule — keeps the pane VM injectable/overridable from tests + previews.
    feedViewModel: FeedViewModel = hiltViewModel(key = feedUri),
) {
    LaunchedEffect(feedUri) {
        feedViewModel.handleEvent(FeedEvent.Bind(feedUri, kind))
    }
    FeedScreen(
        onNavigateToPost = onNavigateToPost,
        onNavigateToAuthor = onNavigateToAuthor,
        onNavigateToMediaViewer = onNavigateToMediaViewer,
        onNavigateToVideoPlayer = onNavigateToVideoPlayer,
        onNavigateTo = onNavigateTo,
        onComposeClick = onComposeClick,
        onReplyClick = onReplyClick,
        viewModel = feedViewModel,
    )
}

/**
 * PROVISIONAL selector — replaced by `FeedChipRow` in a580.8. Exists only
 * to exercise feed switching (and per-feed retention) before the real chip
 * row lands. Renders only when there is more than one feed/list to switch
 * between, so the single-Following case looks unchanged.
 */
@Composable
private fun ProvisionalFeedSelector(
    feedChips: ImmutableList<PinnedFeedUi>,
    pinnedLists: ImmutableList<PinnedFeedUi>,
    selectedUri: String?,
    onSelectFeed: (String) -> Unit,
    onSelectList: (String) -> Unit,
) {
    if (feedChips.size + pinnedLists.size <= 1) return
    Row(
        modifier =
            Modifier
                // The provisional selector sits above the pane's own Scaffold,
                // so it must clear the status bar itself (the real FeedChipRow
                // in a580.8 lives inside the Feed Scaffold and inherits its
                // insets). Without this the row renders under the status bar
                // and its taps are swallowed by the system bar.
                .statusBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        feedChips.forEach { feed ->
            ProvisionalSelectorButton(
                label = feed.displayName,
                selected = feed.uri == selectedUri,
                onClick = { onSelectFeed(feed.uri) },
            )
        }
        pinnedLists.forEach { list ->
            ProvisionalSelectorButton(
                label = "List: ${list.displayName}",
                selected = list.uri == selectedUri,
                onClick = { onSelectList(list.uri) },
            )
        }
    }
}

@Composable
private fun ProvisionalSelectorButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
