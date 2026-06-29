package net.kikin.nubecita.feature.feed.impl

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.common.haptic.rememberPostHaptics
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCardShimmer
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.feed.impl.ui.FeedEmptyState
import net.kikin.nubecita.feature.feed.impl.ui.FeedErrorState
import net.kikin.nubecita.feature.feed.impl.ui.PostFeedList
import net.kikin.nubecita.feature.feed.impl.ui.rememberFeedInteractions
import net.kikin.nubecita.feature.feed.impl.video.FeedVideoPlayerCoordinator
import kotlin.time.Clock
import kotlin.time.Instant

private const val SHIMMER_PREVIEW_COUNT = 6

/**
 * Hilt-aware custom / generator feed screen.
 *
 * Reuses [FeedViewModel] (bound to [feedUri] via [FeedEvent.Bind]) and
 * [PostFeedList] for content rendering. Adds a [TopAppBar] with a back
 * button, the feed's display name, and a pin/unpin toggle driven by
 * [FeedPinViewModel].
 *
 * Navigation callbacks mirror [FeedScreen]'s shape exactly — the module
 * ([FeedNavigationModule]) wires them to `LocalMainShellNavState` so the
 * screen composable stays host-agnostic (previews / screenshot tests that
 * don't stand up `MainShell` can render [FeedViewScreenContent] unchanged).
 *
 * [ViewFeed(FeedType.Custom)] is fired for free by the reused [FeedViewModel]
 * in [FeedEvent.Bind] → initial page success.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedViewScreen(
    feedUri: String,
    displayName: String?,
    onBack: () -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit = { _, _ -> },
    onNavigateToVideoPlayer: (postUri: String) -> Unit = {},
    onNavigateTo: (NavKey) -> Unit = {},
    onReplyClick: (String) -> Unit = {},
    onQuoteClick: (String) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: FeedViewModel = hiltViewModel(),
    pinViewModel: FeedPinViewModel =
        hiltViewModel<FeedPinViewModel, FeedPinViewModel.Factory>(
            creationCallback = { factory -> factory.create(feedUri) },
        ),
) {
    // Bind the VM to this feed's URI once per navigation entry, then
    // immediately trigger the initial load. Both are sequential in a single
    // effect so Bind always precedes Load (two separate LaunchedEffects are
    // concurrent coroutines whose ordering is non-deterministic; worst-case
    // Load fires first and is immediately cancelled when Bind resets the VM).
    LaunchedEffect(feedUri) {
        viewModel.handleEvent(
            FeedEvent.Bind(feedUri, FeedKind.Generator, surface = PostSurface.FeedView),
        )
        viewModel.handleEvent(FeedEvent.Load)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewState = remember(state) { state.toViewState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val pinState by pinViewModel.uiState.collectAsStateWithLifecycle()
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
        )

    val pinErrorMessage = stringResource(R.string.feed_view_snackbar_pin_error)

    // FeedPinViewModel effects — show error snackbar on pin/unpin failure.
    LaunchedEffect(pinViewModel, snackbarHostState) {
        pinViewModel.effects.collect { effect ->
            when (effect) {
                FeedPinEffect.ShowError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = pinErrorMessage)
                }
            }
        }
    }

    FeedViewScreenContent(
        viewState = viewState,
        listState = listState,
        snackbarHostState = snackbarHostState,
        callbacks = interactions.callbacks,
        onRefresh = interactions.onRefresh,
        onRetry = interactions.onRetry,
        onLoadMore = interactions.onLoadMore,
        displayName = displayName,
        isPinned = pinState.isPinned,
        onPinToggle = { pinViewModel.handleEvent(FeedPinEvent.TogglePin) },
        onBack = onBack,
        onImageTap = interactions.onImageTap,
        onVideoTap = interactions.onVideoTap,
        coordinator = interactions.coordinator,
    )
}

/**
 * Stateless body for [FeedViewScreen].
 *
 * Drives the [TopAppBar] (back + feed title + pin toggle), the
 * [PostFeedList] for the [FeedScreenViewState.Loaded] branch, and the
 * shimmer / empty / error states. Screenshot tests and previews call this
 * directly — no ViewModels, no Hilt graph, no live network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedViewScreenContent(
    viewState: FeedScreenViewState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    callbacks: PostCallbacks,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    displayName: String?,
    isPinned: Boolean,
    onPinToggle: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onImageTap: (post: PostUi, imageIndex: Int) -> Unit = { _, _ -> },
    onVideoTap: ((postUri: String) -> Unit)? = null,
    coordinator: FeedVideoPlayerCoordinator? = null,
) {
    // Hoist per-card Surface tokens so the LazyColumn items lambda doesn't
    // re-subscribe to LocalColorScheme / LocalShapes per visible item.
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val cardShape = MaterialTheme.shapes.medium

    val pinContentDescription =
        if (isPinned) {
            stringResource(R.string.feed_view_unpin_content_description)
        } else {
            stringResource(R.string.feed_view_pin_content_description)
        }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayName ?: stringResource(R.string.feed_view_title_generic),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.feed_view_back_content_description),
                        )
                    }
                },
                actions = {
                    IconToggleButton(
                        checked = isPinned,
                        onCheckedChange = { onPinToggle() },
                    ) {
                        NubecitaIcon(
                            name = NubecitaIconName.Bookmark,
                            contentDescription = pinContentDescription,
                            filled = isPinned,
                        )
                    }
                },
                // TopAppBar consumes the top inset (status bar) so the
                // body content doesn't need to add a separate top padding.
                windowInsets = TopAppBarDefaults.windowInsets,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        when (viewState) {
            FeedScreenViewState.InitialLoading ->
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(padding),
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
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(padding),
                    contentPadding = padding,
                )
            is FeedScreenViewState.InitialError ->
                FeedErrorState(
                    error = viewState.error,
                    onRetry = onRetry,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(padding),
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
                    modifier = Modifier.consumeWindowInsets(padding),
                )
        }
    }
}

// ---------- Previews -------------------------------------------------------

@Preview(name = "Empty", showBackground = true)
@Preview(name = "Empty — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedViewScreenEmptyPreview() {
    NubecitaTheme {
        FeedViewScreenPreviewHost(viewState = FeedScreenViewState.Empty, isPinned = false)
    }
}

@Preview(name = "InitialLoading", showBackground = true)
@Preview(name = "InitialLoading — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedViewScreenInitialLoadingPreview() {
    NubecitaTheme {
        FeedViewScreenPreviewHost(viewState = FeedScreenViewState.InitialLoading, isPinned = false)
    }
}

@Preview(name = "Loaded (pinned)", showBackground = true)
@Preview(name = "Loaded (pinned) — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedViewScreenLoadedPinnedPreview() {
    NubecitaTheme {
        FeedViewScreenPreviewHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = previewFeedViewItems(),
                    isAppending = false,
                    isRefreshing = false,
                ),
            isPinned = true,
        )
    }
}

@Preview(name = "Loaded (not pinned)", showBackground = true)
@Composable
private fun FeedViewScreenLoadedUnpinnedPreview() {
    NubecitaTheme {
        FeedViewScreenPreviewHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = previewFeedViewItems(),
                    isAppending = false,
                    isRefreshing = false,
                ),
            isPinned = false,
        )
    }
}

@Composable
private fun FeedViewScreenPreviewHost(
    viewState: FeedScreenViewState,
    isPinned: Boolean,
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalClock provides FeedViewPreviewClock) {
        FeedViewScreenContent(
            viewState = viewState,
            listState = listState,
            snackbarHostState = snackbarHostState,
            callbacks = PostCallbacks.None,
            onRefresh = {},
            onRetry = {},
            onLoadMore = {},
            displayName = "Tech News",
            isPinned = isPinned,
            onPinToggle = {},
            onBack = {},
        )
    }
}

private val FEED_VIEW_PREVIEW_NOW = Instant.parse("2026-04-26T12:00:00Z")
private val FEED_VIEW_PREVIEW_CREATED_AT = Instant.parse("2026-04-26T10:00:00Z")

private object FeedViewPreviewClock : Clock {
    override fun now(): Instant = FEED_VIEW_PREVIEW_NOW
}

private fun previewFeedViewPost(
    id: String,
    text: String = "Preview post $id — sample content for the feed-view screen previews.",
): PostUi =
    PostUi(
        id = "feedview-post-$id",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author =
            AuthorUi(
                did = "did:plc:feedview-preview-$id",
                handle = "feedviewpreview$id.bsky.social",
                displayName = "Preview $id",
                avatarUrl = null,
            ),
        createdAt = FEED_VIEW_PREVIEW_CREATED_AT,
        text = text,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 1, repostCount = 2, likeCount = 12),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

private fun previewFeedViewItems(): ImmutableList<FeedItemUi> =
    persistentListOf<FeedItemUi>(
        FeedItemUi.Single(post = previewFeedViewPost("1")),
        FeedItemUi.Single(post = previewFeedViewPost("2")),
        FeedItemUi.Single(post = previewFeedViewPost("3")),
    )
