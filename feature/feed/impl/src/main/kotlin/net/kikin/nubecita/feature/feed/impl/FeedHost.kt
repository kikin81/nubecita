package net.kikin.nubecita.feature.feed.impl

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalTabReTapSignal
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import net.kikin.nubecita.feature.feed.impl.ui.FeedChipRow
import net.kikin.nubecita.feature.feed.impl.ui.PinnedListsSheet
import net.kikin.nubecita.feature.feed.impl.ui.selectedFeedChipIndex
import net.kikin.nubecita.feature.feeds.api.Feeds
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Host for the main Feed's per-feed switcher. Renders pinned feeds in a
 * [HorizontalPager] where each feed is a [FeedPane] whose [FeedViewModel]
 * is keyed by `feedUri` (retained in the Feed nav entry's `ViewModelStore`).
 *
 * Horizontal swiping is adaptive: enabled on Compact (phones) for fluid touch
 * navigation, and disabled on Medium/Expanded (tablets) where feeds are
 * displayed in a narrow ListDetailSceneStrategy pane adjacent to the
 * VerticalDragHandle divider, preventing touch slop collision with pane resizing.
 *
 * Back gestures participate in the "exit through home" model via [BackHandler]:
 * pressing back while on a secondary feed (page > 0) navigates back to page 0
 * ("Following") before exiting to the launcher.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
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
    val coroutineScope = rememberCoroutineScope()

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

    val activeList =
        remember(state.selectedFeedUri, state.pinnedLists) {
            state.pinnedLists.firstOrNull { it.uri == state.selectedFeedUri }
        }

    val pagerFeeds =
        remember(state.feedChips, activeList) {
            if (activeList != null && state.feedChips.none { it.uri == activeList.uri }) {
                (state.feedChips + activeList).toImmutableList()
            } else {
                state.feedChips
            }
        }

    val initialFollowingFeed =
        remember {
            PinnedFeedUi(
                id = "following",
                uri = "app.bsky.feed.getTimeline",
                kind = FeedKind.Following,
                displayName = "Following",
                avatarUrl = null,
            )
        }
    val resolvedFeeds = if (pagerFeeds.isEmpty()) persistentListOf(initialFollowingFeed) else pagerFeeds

    val initialIndex =
        remember(resolvedFeeds) {
            val idx = resolvedFeeds.indexOfFirst { it.uri == state.selectedFeedUri }
            if (idx >= 0) idx else 0
        }
    val pagerState =
        rememberPagerState(
            initialPage = initialIndex,
            pageCount = { resolvedFeeds.size },
        )

    // Sync pager settled page to host ViewModel selection
    LaunchedEffect(pagerState, resolvedFeeds) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val targetFeed = resolvedFeeds.getOrNull(page)
                if (targetFeed != null && targetFeed.uri != state.selectedFeedUri) {
                    hostViewModel.handleEvent(FeedHostEvent.SelectFeed(targetFeed.uri))
                }
            }
    }

    // Sync external/chip selection change to pager page
    LaunchedEffect(state.selectedFeedUri, resolvedFeeds) {
        val targetIndex = resolvedFeeds.indexOfFirst { it.uri == state.selectedFeedUri }
        if (targetIndex >= 0 && targetIndex != pagerState.targetPage) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    // Back gesture: navigate to Page 0 (Following) before exiting
    PredictiveBackHandler(enabled = pagerState.settledPage != 0) { progress ->
        try {
            progress.collect { /* predictive back progress */ }
            pagerState.animateScrollToPage(0)
        } catch (_: CancellationException) {
            // User cancelled predictive back gesture
        }
    }

    // Adaptive width: enable pager gesture swiping on Compact (phone) only.
    // On tablet / medium+ width, the feed is inside a 412dp/440dp list pane
    // adjacent to the pane-expansion drag handle; disable swipe gestures there
    // to avoid collision with pane resizing.
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val isCompact =
        !adaptiveInfo
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val chipRowHeightDp = 48.dp
    val density = LocalDensity.current
    val chipRowHeightPx = remember(density) { with(density) { chipRowHeightDp.toPx() } }
    val chipRowOffsetHeightPx = remember { Animatable(0f) }

    val nestedScrollConnection =
        remember(chipRowHeightPx, coroutineScope) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val delta = available.y
                    val newOffset = chipRowOffsetHeightPx.value + delta
                    coroutineScope.launch {
                        chipRowOffsetHeightPx.snapTo(newOffset.coerceIn(-chipRowHeightPx, 0f))
                    }
                    return Offset.Zero
                }
            }
        }

    LaunchedEffect(pagerState.currentPage) {
        chipRowOffsetHeightPx.animateTo(0f)
    }

    val tabReTapSignal = LocalTabReTapSignal.current
    LaunchedEffect(tabReTapSignal) {
        tabReTapSignal.collect {
            chipRowOffsetHeightPx.animateTo(0f)
        }
    }

    // Chip-row scroll lives at the host level so it is a single global position.
    val initialChipIndex =
        remember {
            maxOf(0, selectedFeedChipIndex(state.feedChips, state.pinnedLists, state.selectedFeedUri))
        }
    val chipListState = rememberLazyListState(initialFirstVisibleItemIndex = initialChipIndex)
    var showPinnedListsSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.feedChips.isNotEmpty() || state.pinnedLists.isNotEmpty() || state.status == FeedHostStatus.Loading) {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .height(chipRowHeightDp)
                                    .offset { IntOffset(x = 0, y = chipRowOffsetHeightPx.value.roundToInt()) }
                                    .background(MaterialTheme.colorScheme.surface),
                        ) {
                            FeedChipRow(
                                feedChips = state.feedChips,
                                pinnedLists = state.pinnedLists,
                                selectedFeedUri = state.selectedFeedUri,
                                chipListState = chipListState,
                                status = state.status,
                                onSelectFeed = { uri ->
                                    val targetIndex = resolvedFeeds.indexOfFirst { it.uri == uri }
                                    if (targetIndex >= 0) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(targetIndex) }
                                    }
                                    hostViewModel.handleEvent(FeedHostEvent.SelectFeed(uri))
                                },
                                onSelectList = { uri ->
                                    hostViewModel.handleEvent(FeedHostEvent.SelectList(uri))
                                },
                                onRetry = { hostViewModel.handleEvent(FeedHostEvent.Retry) },
                                onManageFeedsClick = { onNavigateTo(Feeds) },
                                onOpenListsSheet = { showPinnedListsSheet = true },
                            )
                        }
                        Spacer(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .windowInsetsTopHeight(WindowInsets.statusBars)
                                    .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            key = { page -> resolvedFeeds.getOrNull(page)?.uri ?: page.toString() },
            userScrollEnabled = isCompact,
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val feed = resolvedFeeds.getOrNull(page)
            if (feed != null) {
                val isPageActive = pagerState.settledPage == page && !pagerState.isScrollInProgress
                FeedPane(
                    feedUri = feed.uri,
                    kind = feed.kind,
                    isPageActive = isPageActive,
                    customContentPadding = padding,
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

    if (showPinnedListsSheet) {
        PinnedListsSheet(
            pinnedLists = state.pinnedLists,
            selectedFeedUri = state.selectedFeedUri,
            onSelectList = { uri ->
                hostViewModel.handleEvent(FeedHostEvent.SelectList(uri))
                showPinnedListsSheet = false
            },
            onDismiss = { showPinnedListsSheet = false },
        )
    }
}

/**
 * A single feed pane. Its [FeedViewModel] is keyed by [feedUri] so it is
 * retained across feed switches; [FeedEvent.Bind] is dispatched once per
 * pane (the VM no-ops a re-bind to the same feed, so no re-fetch on
 * return). Nav callbacks pass straight through to the pane's [FeedScreen].
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
private fun FeedPane(
    feedUri: String,
    kind: FeedKind,
    isPageActive: Boolean,
    customContentPadding: PaddingValues,
    onNavigateToPost: (String) -> Unit,
    onNavigateToAuthor: (String) -> Unit,
    onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit,
    onNavigateToVideoPlayer: (postUri: String) -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    onComposeClick: () -> Unit,
    onReplyClick: (String) -> Unit,
    onQuoteClick: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    feedViewModel: FeedViewModel = hiltViewModel(key = feedUri),
) {
    LaunchedEffect(feedUri) {
        feedViewModel.handleEvent(FeedEvent.Bind(feedUri, kind))
    }
    FeedScreen(
        showChipRow = false,
        isPageActive = isPageActive,
        customContentPadding = customContentPadding,
        selectedFeedUri = feedUri,
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
