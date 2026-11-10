package net.kikin.nubecita.feature.feed.impl

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.window.core.layout.WindowSizeClass
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.haptic.rememberPostHaptics
import net.kikin.nubecita.core.common.navigation.LocalTabReTapSignal
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.core.feedcache.CachedFeedRow
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PinnedFeedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCardShimmer
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.feed.impl.translation.FeedTranslationState
import net.kikin.nubecita.feature.feed.impl.translation.rememberFeedTranslations
import net.kikin.nubecita.feature.feed.impl.ui.FeedAnchoring
import net.kikin.nubecita.feature.feed.impl.ui.FeedChipRow
import net.kikin.nubecita.feature.feed.impl.ui.FeedEmptyState
import net.kikin.nubecita.feature.feed.impl.ui.FeedErrorState
import net.kikin.nubecita.feature.feed.impl.ui.NewPostsPill
import net.kikin.nubecita.feature.feed.impl.ui.PinnedListsSheet
import net.kikin.nubecita.feature.feed.impl.ui.PostFeedList
import net.kikin.nubecita.feature.feed.impl.ui.TrendingVideosCarousel
import net.kikin.nubecita.feature.feed.impl.ui.TrendingVideosViewModel
import net.kikin.nubecita.feature.feed.impl.ui.rememberFeedInteractions
import net.kikin.nubecita.feature.feed.impl.video.FeedVideoPlayerCoordinator
import net.kikin.nubecita.feature.feeds.api.Feeds
import net.kikin.nubecita.feature.videos.api.VideoFeed
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

/** Clear of the chip row, so the pill floats over the posts rather than the chrome. */
private val PILL_TOP_INSET = 8.dp

private const val SHIMMER_PREVIEW_COUNT = 6

/**
 * Hilt-aware Following timeline screen.
 *
 * Owns the screen's lifecycle wiring: state collection, the single
 * `effects` collector that surfaces snackbars + nav callbacks, the
 * `LazyListState` hoisted via `rememberSaveable` (for back-nav and
 * config-change retention), the screen-internal `SnackbarHostState`,
 * the `remember`-d `PostCallbacks` that dispatch to the VM, and the
 * collection of the paged feed. There is no initial-load trigger: a
 * `Pager` initializes itself, and an event here would race the
 * mediator's own `initialize()`. Delegates the rendering to
 * [FeedScreenContent]
 * which previews/screenshot tests call directly with fixture inputs.
 *
 * Suppresses VM-forwarding lints — see ComposerScreen / ProfileScreen
 * for the full rationale (slack compose-lints 1.5.0+ tightened
 * ComposeViewModelForwarding's data-flow analysis; conflicts with
 * ComposeViewModelInjection on stateful screens that hoist state).
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun FeedScreen(
    modifier: Modifier = Modifier,
    feedChips: ImmutableList<PinnedFeedUi> = persistentListOf(),
    pinnedLists: ImmutableList<PinnedFeedUi> = persistentListOf(),
    selectedFeedUri: String? = null,
    chipListState: LazyListState = rememberLazyListState(),
    status: FeedHostStatus = FeedHostStatus.Ready,
    onSelectFeed: (String) -> Unit = {},
    onSelectList: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit = { _, _ -> },
    onNavigateToVideoPlayer: (postUri: String) -> Unit = {},
    /**
     * Generic tab-internal sub-route push callback. The host
     * (`FeedNavigationModule`) wires it to
     * `LocalMainShellNavState.current.add(key)`. The screen stays
     * host-agnostic so previews and instrumentation tests that don't
     * stand up `MainShell` can render `FeedScreen()` unchanged.
     *
     * Today's only emission is `Report(...)` from the PostCard overflow
     * Report row (`nubecita-oftc.3`). Future moderation children
     * (`oftc.4` Block / `oftc.5` Mute confirmation sheets) will travel
     * the same callback with their own NavKey types — no per-feature
     * callback proliferation needed.
     */
    onNavigateTo: (NavKey) -> Unit = {},
    onComposeClick: () -> Unit = {},
    onReplyClick: (String) -> Unit = {},
    onQuoteClick: (String) -> Unit = {},
    showChipRow: Boolean = true,
    showSnackbarHost: Boolean = true,
    isPageActive: Boolean = true,
    customContentPadding: PaddingValues? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: FeedViewModel = hiltViewModel(),
    trendingViewModel: TrendingVideosViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.feed.collectAsLazyPagingItems()
    // The loaded window. A new instance per generation, so it is the right
    // `remember` key for anything derived from what is on screen.
    val snapshot = items.itemSnapshotList
    val loadStatus = items.loadState.toFeedLoadStatus(items.itemCount)
    val viewState =
        remember(state, loadStatus, snapshot) {
            // The outgoing rows are filtered here rather than in the VM
            // because only Paging knows what is loaded: a just-submitted post
            // that a page now carries must stop rendering as an optimistic row.
            state.copy(outgoingPosts = state.outgoingPosts.notYetInFeed(snapshot.items)).toViewState(loadStatus, items.itemCount)
        }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    // Reconcile the interactions cache with what is loaded, and let the VM log
    // `view_feed` once per feed. Paging has no VM-visible "pages changed"
    // callback, so the screen carries the signal across.
    //
    // Keyed on the loaded posts' URIs, NOT on the snapshot. Seeding makes the
    // interactions cache emit, which re-runs the feed's transforms and hands
    // back a new snapshot — so keying on the snapshot would seed again, with
    // posts the overlay had already been applied to. The URI list only changes
    // when the pages really change, which closes that loop.
    // The signature is O(1); the posts are materialized only when it moves.
    // Flattening every loaded post per generation would allocate twice over
    // the whole window on every like tap — a generation arrives on each
    // interactions-cache emission, not only on a page load.
    val pageSignature =
        remember(snapshot) {
            Triple(snapshot.size, snapshot.items.firstOrNull()?.key, snapshot.items.lastOrNull()?.key)
        }
    LaunchedEffect(pageSignature) { viewModel.handleEvent(FeedEvent.ItemsLoaded(snapshot.items.allPosts())) }

    // Discover-only Trending Videos carousel. The ViewModel is created
    // unconditionally (composition-safe across feed switches) but only loads
    // when Discover is the active feed — on first selection and on each
    // pull-to-refresh. Its empty/dismissed visibility is owned here so the
    // header slot is absent (not a zero-height item) when there's nothing to
    // show, avoiding a phantom spacing gap atop the feed.
    val trendingThumbs by trendingViewModel.thumbs.collectAsStateWithLifecycle()
    var trendingDismissed by rememberSaveable { mutableStateOf(false) }
    var trendingLoadRequested by rememberSaveable { mutableStateOf(false) }
    val isDiscoverFeed = selectedFeedUri?.endsWith("/app.bsky.feed.generator/whats-hot") == true
    val isFeedRefreshing = (viewState as? FeedScreenViewState.Loaded)?.isRefreshing == true
    LaunchedEffect(isDiscoverFeed, isFeedRefreshing, isPageActive) {
        if (isDiscoverFeed && isPageActive && (!trendingLoadRequested || isFeedRefreshing)) {
            trendingViewModel.load()
            trendingLoadRequested = true
        }
    }
    val trendingHeader: (@Composable () -> Unit)? =
        if (isDiscoverFeed && trendingThumbs.isNotEmpty() && !trendingDismissed) {
            {
                TrendingVideosCarousel(
                    thumbs = trendingThumbs,
                    onOpen = { postUri -> onNavigateTo(VideoFeed(postUri)) },
                    onDismiss = { trendingDismissed = true },
                )
            }
        } else {
            null
        }
    val haptics = rememberPostHaptics()
    val interactions =
        rememberFeedInteractions(
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            haptics = haptics,
            onReplyClick = onReplyClick,
            onQuoteClick = onQuoteClick,
            onNavigateToPost = onNavigateToPost,
            onNavigateToAuthor = onNavigateToAuthor,
            onNavigateToMediaViewer = onNavigateToMediaViewer,
            onNavigateToVideoPlayer = onNavigateToVideoPlayer,
            onNavigateTo = onNavigateTo,
        )

    // No initial-load dispatch: a `Pager` initializes itself. An event here
    // would race the mediator's own `initialize()` and, on a warm partition,
    // could refetch the very page the reader's position points into.

    // Two actions, one gesture: Paging refetches, the VM drops the state it
    // owns. Splitting them keeps the destructive path (D8) in one place rather
    // than duplicating the fetch in the VM.
    //
    // Remembered because both capture `LazyPagingItems`, which is not @Stable:
    // the compiler cannot auto-remember a lambda over an unstable capture, so
    // an inline one would allocate on every recomposition of this scope — and
    // this scope reads loadState, itemCount and itemSnapshotList, so it
    // recomposes often.
    val onRefresh =
        remember(interactions, items) {
            {
                interactions.onRefresh()
                items.refresh()
            }
        }
    val onRetry = remember(items) { { items.retry() } }

    // The reader's place. Only this surface keeps one: a generator feed
    // refreshes on open (D14), so an anchor written there would never be read.
    val newPosts by viewModel.newPosts.collectAsStateWithLifecycle()
    val restoreTo by viewModel.restoreTarget.collectAsStateWithLifecycle()
    val anchoring =
        remember(viewModel, restoreTo) {
            FeedAnchoring(
                restoreTo = restoreTo,
                onAnchorChanged = { key, offset -> viewModel.handleEvent(FeedEvent.AnchorChanged(key, offset)) },
                onRestoreApplied = { viewModel.handleEvent(FeedEvent.RestoreConsumed) },
            )
        }

    val scope = rememberCoroutineScope()
    // "Take me to the newest" — the pill, or a Feed-tab re-tap. The VM fetches
    // the newest page, rebuilds the pager at the top (index 0 of the window
    // loaded around the saved place is not the top, nubecita-lrnv.4), records
    // the acknowledgement, and then raises scrollToNewest.
    //
    // The scroll also waits for a window that starts at the top of the
    // partition. Scrolling to index 0 of a window that does not would land on
    // its first row, and LazyListState follows that row's key into the next
    // generation instead of staying at the top.
    val scrollToNewest by viewModel.scrollToNewest.collectAsStateWithLifecycle()
    val windowStartsAtTop = items.loadState.source.prepend.endOfPaginationReached
    // A boolean, not the count: the effect only cares that something is
    // loaded, and keying on the count would relaunch it with every page.
    val hasItems = items.itemCount > 0
    LaunchedEffect(scrollToNewest, windowStartsAtTop, hasItems) {
        if (scrollToNewest && windowStartsAtTop && hasItems) {
            listState.scrollToItem(0)
            viewModel.handleEvent(FeedEvent.NewestShown)
        }
    }
    val onNewPostsClick = remember(viewModel) { { viewModel.handleEvent(FeedEvent.ShowNewest) } }
    // FeedScreenContent animates its own scroll to the top on a re-tap; this is
    // the half only the VM can do.
    val onTabReTap = onNewPostsClick
    val onFillSeam =
        remember(viewModel) { { position: Int -> viewModel.handleEvent(FeedEvent.FillSeam(position)) } }
    // Jump-to-newest IS the destructive refresh (D8), plus the scroll that
    // makes "newest" mean what it says: the partition is replaced from the
    // top, and a reader left mid-list would be looking at the middle of a
    // timeline they just asked to start over.
    val onJumpToNewest =
        remember(onRefresh, listState, scope) {
            {
                onRefresh()
                scope.launch { listState.scrollToItem(0) }
                Unit
            }
        }

    // Per-post translation, dispatched only when the list settles. The gate is
    // the whole feature: measured on a 120Hz device, translating on scroll-idle
    // costs nothing detectable (2.01% janky off vs 1.77% / 1.65% on, p50 5ms
    // throughout) because nothing runs during a fling.
    //
    // `visiblePosts` reads the layout at settle time rather than closing over a
    // snapshot: the posts that matter are the ones still on screen when the
    // scroll stops, not the ones that were when it started.
    val translationScope = rememberCoroutineScope()
    val translations =
        rememberFeedTranslations(
            listState = listState,
            visiblePosts = {
                val loaded = snapshot.items
                // visibleItemsInfo indexes the LazyColumn, which puts the
                // optional header and the optimistic outgoing rows ahead of
                // the paged ones. Subtracting them is not cosmetic: off by the
                // header alone and every settle translates its neighbour.
                // PostFeedList computes the same offset for the scroll anchor.
                val leading =
                    (if (trendingHeader != null) 1 else 0) +
                        ((viewState as? FeedScreenViewState.Loaded)?.outgoingPosts?.size ?: 0)
                listState.layoutInfo.visibleItemsInfo
                    .mapNotNull { info -> loaded.getOrNull(info.index - leading) }
                    .allPosts()
            },
            coordinator = viewModel.translationCoordinator,
            scope = translationScope,
        )

    // Hosted here rather than inside the list: the offer belongs to the
    // screen, and a dialog anchored to a post would vanish the moment that
    // post scrolled out of the composition.
    translations?.autoOffer?.let { source ->
        AutoTranslateOfferDialog(
            sourceLanguage = source,
            onAccept = translations::acceptAutoOffer,
            onDecline = translations::declineAutoOffer,
        )
    }

    FeedScreenContent(
        viewState = viewState,
        items = items,
        anchoring = anchoring,
        newPosts = newPosts,
        onNewPostsClick = onNewPostsClick,
        onTabReTap = onTabReTap,
        onFillSeam = onFillSeam,
        onJumpToNewest = onJumpToNewest,
        listState = listState,
        snackbarHostState = snackbarHostState,
        callbacks = interactions.callbacks,
        // Two actions, one gesture: Paging refetches, the VM drops the state
        // it owns. Splitting them is what keeps the destructive path (D8) in
        // one place instead of duplicating the fetch in the VM.
        onRefresh = onRefresh,
        onRetry = onRetry,
        onRetryOutgoing = interactions.onRetryOutgoing,
        onDiscardOutgoing = interactions.onDiscardOutgoing,
        feedChips = feedChips,
        pinnedLists = pinnedLists,
        selectedFeedUri = selectedFeedUri,
        chipListState = chipListState,
        status = status,
        onSelectFeed = onSelectFeed,
        onSelectList = onSelectList,
        onRetryFeeds = onRetry,
        onNavigateTo = onNavigateTo,
        onComposeClick = onComposeClick,
        onImageTap = interactions.onImageTap,
        onQuotedImageTap = interactions.onQuotedImageTap,
        onVideoTap = interactions.onVideoTap,
        coordinator = interactions.coordinator,
        translations = translations,
        header = trendingHeader,
        showChipRow = showChipRow,
        showSnackbarHost = showSnackbarHost,
        isPageActive = isPageActive,
        customContentPadding = customContentPadding,
        modifier = modifier,
    )
}

/**
 * Stateless screen body. Takes the projected [FeedScreenViewState] and
 * the small set of callbacks the host wires to VM events. Previews and
 * Compose UI tests invoke this directly with fixture inputs — no
 * ViewModel, no Hilt graph, no live network.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun FeedScreenContent(
    viewState: FeedScreenViewState,
    items: LazyPagingItems<CachedFeedRow>,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    callbacks: PostCallbacks,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onFillSeam: (position: Int) -> Unit,
    onJumpToNewest: () -> Unit,
    feedChips: ImmutableList<PinnedFeedUi>,
    pinnedLists: ImmutableList<PinnedFeedUi>,
    selectedFeedUri: String?,
    chipListState: LazyListState,
    status: FeedHostStatus,
    onSelectFeed: (String) -> Unit,
    onSelectList: (String) -> Unit,
    onRetryFeeds: () -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    // Null on a surface that keeps no reading position — see PostFeedList.
    anchoring: FeedAnchoring? = null,
    // Faces for the new-posts pill; empty means no pill.
    newPosts: ImmutableList<AuthorUi> = persistentListOf(),
    onNewPostsClick: () -> Unit = {},
    // Called on a Feed-tab re-tap while this page is the one on screen, after
    // the list has been asked to scroll to the top.
    onTabReTap: () -> Unit = {},
    onComposeClick: () -> Unit = {},
    onRetryOutgoing: (localId: String) -> Unit = {},
    onDiscardOutgoing: (localId: String) -> Unit = {},
    onImageTap: (post: PostUi, imageIndex: Int) -> Unit = { _, _ -> },
    onQuotedImageTap: (quotedPostUri: String, imageIndex: Int) -> Unit = { _, _ -> },
    onVideoTap: ((postUri: String) -> Unit)? = null,
    coordinator: FeedVideoPlayerCoordinator? = null,
    showChipRow: Boolean = true,
    showSnackbarHost: Boolean = true,
    isPageActive: Boolean = true,
    customContentPadding: PaddingValues? = null,
    /**
     * Optional leading item above the posts (the Discover Trending Videos
     * carousel). Hoisted so the host owns its empty/dismissed visibility —
     * `null` renders no header slot at all, avoiding a phantom spacing gap.
     */
    header: (@Composable () -> Unit)? = null,
    /** Per-post translation state, or null on a surface that does not translate. */
    translations: FeedTranslationState? = null,
) {
    var showPinnedListsSheet by rememberSaveable { mutableStateOf(false) }

    val chipRowHeightDp = 48.dp
    val density = LocalDensity.current
    val chipRowHeightPx = remember(density) { with(density) { chipRowHeightDp.toPx() } }
    val chipRowOffsetHeightPx = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

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

    LaunchedEffect(selectedFeedUri) {
        chipRowOffsetHeightPx.animateTo(0f)
    }

    // Tap-to-top: collect MainShell's tab-retap signal and scroll the
    // feed list to the top. The default empty SharedFlow (no provider in
    // previews / screenshot tests) never emits, so collecting in those
    // contexts is a runtime no-op. Keyed on (signal, listState) so the
    // collector restarts cleanly across recompositions that re-create
    // either reference.
    val tabReTapSignal = LocalTabReTapSignal.current
    val currentOnTabReTap by rememberUpdatedState(onTabReTap)
    val currentIsPageActive by rememberUpdatedState(isPageActive)
    LaunchedEffect(tabReTapSignal, listState) {
        tabReTapSignal.collect {
            // Only the page on screen: every composed pane hears the signal,
            // and an off-screen feed should not lose its place to a tap the
            // reader made somewhere else.
            if (currentIsPageActive) {
                currentOnTabReTap()
                listState.animateScrollToItem(0)
                chipRowOffsetHeightPx.animateTo(0f)
            }
        }
    }
    // FAB is the composer entry point. wtq.9 swapped the prior scroll-
    // to-top FAB content for `NubecitaIcon(NubecitaIconName.Edit)`. The action itself
    // (`onComposeClick`) is hoisted as a callback rather than read from
    // `LocalMainShellNavState` directly — `FeedScreenContent` is
    // exercised by screenshot tests that don't provide the nav-state
    // CompositionLocal, and the established repo pattern (see
    // PostDetailNavigationModule) wires nav callbacks at the
    // EntryProvider, not inside the screen Composable. The home-tab
    // retap path that the old FAB shared with this screen still works
    // via the `LocalTabReTapSignal` collector above.
    //
    // Visibility gate: only Loaded. InitialLoading / Empty / InitialError
    // hide the FAB so the user isn't tempted to compose into a feed
    // they can't yet see. (Empty timeline still hides for V1 — we'll
    // revisit if telemetry shows users want to post into a fresh
    // following list.)
    //
    // No `remember` / `derivedStateOf`: keying on the full `viewState`
    // would cost an O(n) structural-equality compare every recomposition
    // (Loaded carries `feedItems: ImmutableList<FeedItemUi>`), and the
    // body itself is a constant-time `is`-check that doesn't need
    // memoization. Compose is happy to re-evaluate this each frame.
    val showComposeFab = viewState is FeedScreenViewState.Loaded
    val isCompact =
        !currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    // Hoist the per-card Surface tokens above the LazyColumn so each
    // visible item doesn't re-subscribe to LocalColorScheme /
    // LocalShapes individually. Per Option A, feed posts render flush on the
    // screen canvas (surface) with rectangular shape, separated by dividers,
    // avoiding the "unread/actionable tile" look of isolated container cards.
    val cardColor = MaterialTheme.colorScheme.surface
    val cardShape = RectangleShape

    val hasSelector = showChipRow && (feedChips.isNotEmpty() || pinnedLists.isNotEmpty() || status == FeedHostStatus.Loading)
    val nestedScrollModifier =
        if (hasSelector) {
            Modifier.nestedScroll(nestedScrollConnection)
        } else {
            Modifier
        }

    Scaffold(
        modifier = modifier.then(nestedScrollModifier),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = {
            if (showSnackbarHost) {
                SnackbarHost(snackbarHostState)
            }
        },
        topBar = {
            if (hasSelector) {
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
                                feedChips = feedChips,
                                pinnedLists = pinnedLists,
                                selectedFeedUri = selectedFeedUri,
                                chipListState = chipListState,
                                status = status,
                                onSelectFeed = onSelectFeed,
                                onSelectList = onSelectList,
                                onRetry = onRetryFeeds,
                                onManageFeedsClick = { onNavigateTo(Feeds) },
                                onOpenListsSheet = { showPinnedListsSheet = true },
                            )
                        }
                        // Status-bar scrim. Sized to the status-bar inset via
                        // windowInsetsTopHeight (NOT statusBarsPadding, which
                        // adds padding around a zero-height Spacer and paints
                        // nothing). Drawn last so it sits on top: it keeps the
                        // system-bar icons legible against a surface backdrop
                        // and hides the chip row as it translates up behind it
                        // on scroll, instead of letting it bleed into the
                        // cutout/status bar.
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
        floatingActionButton = {
            // AnimatedVisibility wraps the FAB so the appearance / dismissal
            // fades + scales rather than popping in.
            AnimatedVisibility(
                visible = showComposeFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                if (isCompact) {
                    FloatingActionButton(onClick = onComposeClick) {
                        NubecitaIcon(
                            name = NubecitaIconName.Edit,
                            contentDescription = stringResource(R.string.feed_compose_new_post),
                            filled = true,
                        )
                    }
                } else {
                    // Small Extended FAB on tablets: 56dp container height
                    // (same as the compact FAB) plus a "Compose" pill label
                    // — discoverable on tablet width without the visual
                    // heft of the 96dp Large or 80dp Medium variants.
                    //
                    // Accessibility split: the icon carries the longer
                    // "Compose new post" description (same as the compact
                    // variant), and the visible "Compose" label is
                    // cleared from the semantics tree via
                    // `clearAndSetSemantics {}` so TalkBack reads the full
                    // action description once, not "Compose new post,
                    // Compose, button".
                    SmallExtendedFloatingActionButton(
                        onClick = onComposeClick,
                        text = {
                            Text(
                                text = stringResource(R.string.feed_compose_fab_label),
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        },
                        icon = {
                            NubecitaIcon(
                                name = NubecitaIconName.Edit,
                                contentDescription = stringResource(R.string.feed_compose_new_post),
                                filled = true,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        val effectivePadding = customContentPadding ?: padding
        // EVERY branch must consume `padding` — without this, the status bar
        // and gesture bar overlap content under edge-to-edge. Scrollable
        // surfaces apply via `contentPadding` so the surface itself extends
        // behind translucent system bars; full-screen state composables
        // accept a contentPadding parameter and apply it to their root.
        when (viewState) {
            FeedScreenViewState.InitialLoading ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(FeedTestTags.SHIMMER),
                    contentPadding = effectivePadding,
                ) {
                    items(count = SHIMMER_PREVIEW_COUNT, key = { "shimmer-$it" }) { index ->
                        Surface(
                            color = cardColor,
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                PostCardShimmer(showImagePlaceholder = index % 2 == 0)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            FeedScreenViewState.Empty ->
                FeedEmptyState(
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = effectivePadding,
                )
            is FeedScreenViewState.InitialError ->
                FeedErrorState(
                    error = viewState.error,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = effectivePadding,
                )
            is FeedScreenViewState.Loaded ->
                Box(modifier = Modifier.fillMaxSize()) {
                    PostFeedList(
                        onQuotedImageTap = onQuotedImageTap,
                        items = items,
                        anchoring = anchoring,
                        outgoingPosts = viewState.outgoingPosts,
                        onRetryOutgoing = onRetryOutgoing,
                        onDiscardOutgoing = onDiscardOutgoing,
                        isAppending = viewState.isAppending,
                        isRefreshing = viewState.isRefreshing,
                        listState = listState,
                        callbacks = callbacks,
                        onRefresh = onRefresh,
                        onImageTap = onImageTap,
                        cardColor = cardColor,
                        cardShape = cardShape,
                        contentPadding = effectivePadding,
                        lastLikeTapPostUri = viewState.lastLikeTapPostUri,
                        lastRepostTapPostUri = viewState.lastRepostTapPostUri,
                        onVideoTap = onVideoTap,
                        coordinator = coordinator,
                        videoAutoplayEnabled = viewState.videoAutoplayEnabled,
                        isPageActive = isPageActive,
                        header = header,
                        isFillingSeam = viewState.isFillingSeam,
                        onFillSeam = onFillSeam,
                        onJumpToNewest = onJumpToNewest,
                        translations = translations,
                    )
                    // Over the list, not in it: the pill has to stay put while
                    // the timeline scrolls underneath, and it must not take a
                    // slot that the anchor's index arithmetic would have to
                    // account for.
                    NewPostsPill(
                        authors = newPosts,
                        onClick = onNewPostsClick,
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(effectivePadding)
                                .padding(top = PILL_TOP_INSET),
                    )
                }
        }
    }

    if (showChipRow && showPinnedListsSheet) {
        PinnedListsSheet(
            pinnedLists = pinnedLists,
            selectedFeedUri = selectedFeedUri,
            onSelectList = onSelectList,
            onDismiss = { showPinnedListsSheet = false },
        )
    }
}

// ---------- Previews -------------------------------------------------------

@Preview(name = "Empty", showBackground = true)
@Preview(name = "Empty — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenEmptyPreview() {
    NubecitaTheme {
        FeedScreenPreviewHost(viewState = FeedScreenViewState.Empty)
    }
}

@Preview(name = "InitialLoading", showBackground = true)
@Preview(name = "InitialLoading — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenInitialLoadingPreview() {
    NubecitaTheme {
        FeedScreenPreviewHost(viewState = FeedScreenViewState.InitialLoading)
    }
}

@Preview(name = "InitialError — Network", showBackground = true)
@Preview(name = "InitialError — Network — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenInitialErrorNetworkPreview() {
    NubecitaTheme {
        FeedScreenPreviewHost(viewState = FeedScreenViewState.InitialError(FeedError.Network))
    }
}

@Preview(name = "InitialError — Unauthenticated", showBackground = true)
@Preview(name = "InitialError — Unauthenticated — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenInitialErrorUnauthPreview() {
    NubecitaTheme {
        FeedScreenPreviewHost(viewState = FeedScreenViewState.InitialError(FeedError.Unauthenticated))
    }
}

@Preview(name = "InitialError — Unknown", showBackground = true)
@Preview(name = "InitialError — Unknown — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenInitialErrorUnknownPreview() {
    NubecitaTheme {
        FeedScreenPreviewHost(viewState = FeedScreenViewState.InitialError(FeedError.Unknown(cause = null)))
    }
}

@Preview(name = "Loaded", showBackground = true)
@Preview(name = "Loaded — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenLoadedPreview() {
    NubecitaTheme {
        FeedScreenPreviewHost(
            items = previewFeedItems(),
            viewState =
                FeedScreenViewState.Loaded(
                    isAppending = false,
                    isRefreshing = false,
                ),
        )
    }
}

@Preview(name = "Loaded + Refreshing", showBackground = true)
@Preview(name = "Loaded + Refreshing — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenLoadedRefreshingPreview() {
    NubecitaTheme {
        FeedScreenPreviewHost(
            items = previewFeedItems(),
            viewState =
                FeedScreenViewState.Loaded(
                    isAppending = false,
                    isRefreshing = true,
                ),
        )
    }
}

@Preview(name = "Loaded + Appending", showBackground = true)
@Preview(name = "Loaded + Appending — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenLoadedAppendingPreview() {
    NubecitaTheme {
        FeedScreenPreviewHost(
            items = previewFeedItems(),
            viewState =
                FeedScreenViewState.Loaded(
                    isAppending = true,
                    isRefreshing = false,
                ),
        )
    }
}

/**
 * A fixed list as [LazyPagingItems], for previews and screenshot tests.
 *
 * `FeedScreenContent` takes the paged items rather than a list, so a fixture
 * has to arrive as one. A single `PagingData.from` page is enough: nothing a
 * preview renders depends on there being a second page, and it keeps the
 * fixtures plain lists.
 */
@Composable
internal fun rememberPreviewPagingItems(items: ImmutableList<FeedItemUi>): LazyPagingItems<CachedFeedRow> = remember(items) { flowOf(PagingData.from(items.map<FeedItemUi, CachedFeedRow>(CachedFeedRow::Item))) }.collectAsLazyPagingItems()

/**
 * Stateless preview/test host — wraps [FeedScreenContent] with a
 * fresh `LazyListState` + `SnackbarHostState` so the call site only
 * supplies the `viewState` to vary across previews.
 */
@Composable
private fun FeedScreenPreviewHost(
    viewState: FeedScreenViewState,
    items: ImmutableList<FeedItemUi> = persistentListOf(),
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Provide a fixed clock so PostCard's relative-time label is
    // deterministic — pairs with PREVIEW_CREATED_AT below to render "2h".
    CompositionLocalProvider(LocalClock provides PreviewClock) {
        FeedScreenContent(
            viewState = viewState,
            items = rememberPreviewPagingItems(items),
            listState = listState,
            snackbarHostState = snackbarHostState,
            callbacks = PostCallbacks.None,
            onRefresh = {},
            onFillSeam = {},
            onJumpToNewest = {},
            onRetry = {},
            feedChips = persistentListOf(),
            pinnedLists = persistentListOf(),
            selectedFeedUri = null,
            chipListState = rememberLazyListState(),
            status = FeedHostStatus.Ready,
            onSelectFeed = {},
            onSelectList = {},
            onRetryFeeds = {},
            onNavigateTo = {},
        )
    }
}

// Fixed instants for previews + screenshots. Paired with
// `PreviewClock`, the rendered relative-time label is "2h" forever —
// no `Clock.System.now()` involved, so screenshots don't drift as
// wall-clock advances. Tests that want a different bucket override
// these locally rather than recomputing relative to a live clock.
private val PREVIEW_NOW = Instant.parse("2026-04-26T12:00:00Z")
private val PREVIEW_CREATED_AT = Instant.parse("2026-04-26T10:00:00Z")

private object PreviewClock : Clock {
    override fun now(): Instant = PREVIEW_NOW
}

private fun previewPost(
    id: String,
    text: String = "Preview post $id — sample timeline content for the feed-screen previews.",
): PostUi =
    PostUi(
        id = "post-$id",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author =
            AuthorUi(
                did = "did:plc:preview-$id",
                handle = "preview$id.bsky.social",
                displayName = "Preview $id",
                avatarUrl = null,
            ),
        createdAt = PREVIEW_CREATED_AT,
        text = text,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 1, repostCount = 2, likeCount = 12),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

/**
 * Mixed preview fixture with at least one [FeedItemUi.Single] and one
 * [FeedItemUi.ReplyCluster] (with ellipsis) so the visual contrast
 * between standalone posts and reply clusters is exercised in the IDE
 * preview pane and screenshot baselines.
 */
private fun previewFeedItems(): ImmutableList<FeedItemUi> =
    persistentListOf<FeedItemUi>(
        FeedItemUi.Single(post = previewPost("1", text = "Preview post 1 — a typical standalone feed entry.")),
        FeedItemUi.Single(post = previewPost("2", text = "Preview post 2 — another standalone entry.")),
        FeedItemUi.ReplyCluster(
            root = previewPost("root", text = "Root post that started the conversation."),
            parent = previewPost("parent", text = "Immediate parent — what the leaf is replying to."),
            leaf = previewPost("leaf", text = "Leaf reply — the post that surfaced in the timeline."),
            hasEllipsis = true,
        ),
        FeedItemUi.Single(post = previewPost("4", text = "Preview post 4 — back to a standalone entry.")),
        FeedItemUi.Single(post = previewPost("5", text = "Preview post 5 — closing out the fixture.")),
    )

/**
 * Offers to translate a whole language automatically, after the reader has
 * translated one post in it by hand.
 *
 * No download size. The pack is not on disk when this is asked — that is the
 * point of asking — and nothing can measure one that is not there, so the
 * copy says how it downloads rather than quoting a number that would have to
 * be invented and would drift as the packs change.
 */
@Composable
private fun AutoTranslateOfferDialog(
    sourceLanguage: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val language =
        remember(sourceLanguage, locale) {
            Locale.forLanguageTag(sourceLanguage).getDisplayLanguage(locale).ifBlank { sourceLanguage }
        }
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.feed_auto_translate_offer_title, language)) },
        text = { Text(stringResource(R.string.feed_auto_translate_offer_body, language)) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.feed_auto_translate_offer_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.feed_auto_translate_offer_decline))
            }
        },
    )
}
