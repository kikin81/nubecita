package net.kikin.nubecita.feature.feed.impl

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.haptic.rememberPostHaptics
import net.kikin.nubecita.core.common.navigation.LocalTabReTapSignal
import net.kikin.nubecita.core.common.time.LocalClock
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
import net.kikin.nubecita.feature.feed.impl.ui.FeedChipRow
import net.kikin.nubecita.feature.feed.impl.ui.FeedEmptyState
import net.kikin.nubecita.feature.feed.impl.ui.FeedErrorState
import net.kikin.nubecita.feature.feed.impl.ui.PinnedListsSheet
import net.kikin.nubecita.feature.feed.impl.ui.PostFeedList
import net.kikin.nubecita.feature.feed.impl.ui.rememberFeedInteractions
import net.kikin.nubecita.feature.feed.impl.video.FeedVideoPlayerCoordinator
import net.kikin.nubecita.feature.feeds.api.Feeds
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

private const val SHIMMER_PREVIEW_COUNT = 6

/**
 * Hilt-aware Following timeline screen.
 *
 * Owns the screen's lifecycle wiring: state collection, the single
 * `effects` collector that surfaces snackbars + nav callbacks, the
 * `LazyListState` hoisted via `rememberSaveable` (for back-nav and
 * config-change retention), the screen-internal `SnackbarHostState`,
 * the `remember`-d `PostCallbacks` that dispatch to the VM, and the
 * one-shot `LaunchedEffect(Unit)` that fires `FeedEvent.Load` on first
 * composition. Delegates the actual rendering to [FeedScreenContent]
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
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewState = remember(state) { state.toViewState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
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

    LaunchedEffect(Unit) { viewModel.handleEvent(FeedEvent.Load) }

    FeedScreenContent(
        viewState = viewState,
        listState = listState,
        snackbarHostState = snackbarHostState,
        callbacks = interactions.callbacks,
        onRefresh = interactions.onRefresh,
        onRetry = interactions.onRetry,
        onLoadMore = interactions.onLoadMore,
        feedChips = feedChips,
        pinnedLists = pinnedLists,
        selectedFeedUri = selectedFeedUri,
        chipListState = chipListState,
        status = status,
        onSelectFeed = onSelectFeed,
        onSelectList = onSelectList,
        onRetryFeeds = interactions.onRetry,
        onNavigateTo = onNavigateTo,
        onComposeClick = onComposeClick,
        onImageTap = interactions.onImageTap,
        onVideoTap = interactions.onVideoTap,
        coordinator = interactions.coordinator,
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
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    callbacks: PostCallbacks,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
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
    onComposeClick: () -> Unit = {},
    onImageTap: (post: PostUi, imageIndex: Int) -> Unit = { _, _ -> },
    onVideoTap: ((postUri: String) -> Unit)? = null,
    coordinator: FeedVideoPlayerCoordinator? = null,
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
    LaunchedEffect(tabReTapSignal, listState) {
        tabReTapSignal.collect {
            listState.animateScrollToItem(0)
            chipRowOffsetHeightPx.animateTo(0f)
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
    // LocalShapes individually. Per Compose perf docs: "CompositionLocal
    // lookups can cause unnecessary recompositions if used inside a lazy
    // list item." One subscription for the screen vs. one per visible
    // post on theme change. Passed into Surface wraps below and into
    // ThreadCluster's new color/shape params.
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val cardShape = MaterialTheme.shapes.medium

    val hasSelector = feedChips.isNotEmpty() || pinnedLists.isNotEmpty() || status == FeedHostStatus.Loading
    val nestedScrollModifier =
        if (hasSelector) {
            Modifier.nestedScroll(nestedScrollConnection)
        } else {
            Modifier
        }

    Scaffold(
        modifier = modifier.then(nestedScrollModifier),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        // EVERY branch must consume `padding` — without this, the status bar
        // and gesture bar overlap content under edge-to-edge. Scrollable
        // surfaces apply via `contentPadding` so the surface itself extends
        // behind translucent system bars; full-screen state composables
        // accept a contentPadding parameter and apply it to their root.
        when (viewState) {
            FeedScreenViewState.InitialLoading ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(count = SHIMMER_PREVIEW_COUNT, key = { "shimmer-$it" }) { index ->
                        Surface(
                            color = cardColor,
                            shape = cardShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PostCardShimmer(showImagePlaceholder = index % 2 == 0)
                        }
                    }
                }
            FeedScreenViewState.Empty ->
                FeedEmptyState(
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                )
            is FeedScreenViewState.InitialError ->
                FeedErrorState(
                    error = viewState.error,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                )
            is FeedScreenViewState.Loaded ->
                PostFeedList(
                    feedItems = viewState.feedItems,
                    isAppending = viewState.isAppending,
                    isRefreshing = viewState.isRefreshing,
                    listState = listState,
                    callbacks = callbacks,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                    onImageTap = onImageTap,
                    cardColor = cardColor,
                    cardShape = cardShape,
                    contentPadding = padding,
                    lastLikeTapPostUri = viewState.lastLikeTapPostUri,
                    lastRepostTapPostUri = viewState.lastRepostTapPostUri,
                    onVideoTap = onVideoTap,
                    coordinator = coordinator,
                )
        }
    }

    if (showPinnedListsSheet) {
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
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = previewFeedItems(),
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
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = previewFeedItems(),
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
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = previewFeedItems(),
                    isAppending = true,
                    isRefreshing = false,
                ),
        )
    }
}

/**
 * Stateless preview/test host — wraps [FeedScreenContent] with a
 * fresh `LazyListState` + `SnackbarHostState` so the call site only
 * supplies the `viewState` to vary across previews.
 */
@Composable
private fun FeedScreenPreviewHost(viewState: FeedScreenViewState) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Provide a fixed clock so PostCard's relative-time label is
    // deterministic — pairs with PREVIEW_CREATED_AT below to render "2h".
    CompositionLocalProvider(LocalClock provides PreviewClock) {
        FeedScreenContent(
            viewState = viewState,
            listState = listState,
            snackbarHostState = snackbarHostState,
            callbacks = PostCallbacks.None,
            onRefresh = {},
            onRetry = {},
            onLoadMore = {},
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
