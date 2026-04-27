package net.kikin.nubecita.feature.feed.impl

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.designsystem.component.PostCardShimmer
import net.kikin.nubecita.feature.feed.impl.ui.FeedAppendingIndicator
import net.kikin.nubecita.feature.feed.impl.ui.FeedEmptyState
import net.kikin.nubecita.feature.feed.impl.ui.FeedErrorState
import net.kikin.nubecita.feature.feed.impl.ui.PostCardVideoEmbed
import net.kikin.nubecita.feature.feed.impl.video.FeedVideoPlayerCoordinator
import net.kikin.nubecita.feature.feed.impl.video.createFeedVideoPlayerCoordinator
import net.kikin.nubecita.feature.feed.impl.video.mostVisibleVideoTarget
import kotlin.time.Clock
import kotlin.time.Instant

private const val PREFETCH_DISTANCE = 5
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
 */
@Composable
internal fun FeedScreen(
    modifier: Modifier = Modifier,
    onNavigateToPost: (PostUi) -> Unit = {},
    onNavigateToAuthor: (String) -> Unit = {},
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewState = remember(state) { state.toViewState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val callbacks =
        remember(viewModel) {
            PostCallbacks(
                onTap = { viewModel.handleEvent(FeedEvent.OnPostTapped(it)) },
                onAuthorTap = { viewModel.handleEvent(FeedEvent.OnAuthorTapped(it.did)) },
                onLike = { viewModel.handleEvent(FeedEvent.OnLikeClicked(it)) },
                onRepost = { viewModel.handleEvent(FeedEvent.OnRepostClicked(it)) },
                onReply = { viewModel.handleEvent(FeedEvent.OnReplyClicked(it)) },
                onShare = { viewModel.handleEvent(FeedEvent.OnShareClicked(it)) },
            )
        }
    // Pre-resolve snackbar copy via stringResource() at composition time
    // so locale + dark-mode changes participate in recomposition. Reading
    // them via context.getString(...) inside the LaunchedEffect would
    // bypass Compose's resource tracking (lint: LocalContextGetResourceValueCall).
    val networkErrorMessage = stringResource(R.string.feed_snackbar_error_network)
    val unauthErrorMessage = stringResource(R.string.feed_snackbar_error_unauthenticated)
    val unknownErrorMessage = stringResource(R.string.feed_snackbar_error_unknown)
    // Wrap nav callbacks so the long-lived effect collector below keys
    // on `Unit` (one collector for the screen's lifetime) but always
    // calls the most recent lambda the host supplied. Without these,
    // ktlint's compose:lambda-param-in-effect flags the references and
    // a stale lambda would survive recomposition.
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToAuthor by rememberUpdatedState(onNavigateToAuthor)
    // Hoist the VM-dispatching callbacks. Inline lambdas at the
    // FeedScreenContent call site would create new instances per
    // recomposition; with the FeedScreenContent body skip-friendly
    // (all params @Stable / @Immutable), preserving lambda identity
    // here lets it skip recomposition when only `viewState` changes.
    val onRefresh = remember(viewModel) { { viewModel.handleEvent(FeedEvent.Refresh) } }
    val onRetry = remember(viewModel) { { viewModel.handleEvent(FeedEvent.Retry) } }
    val onLoadMore = remember(viewModel) { { viewModel.handleEvent(FeedEvent.LoadMore) } }

    LaunchedEffect(Unit) { viewModel.handleEvent(FeedEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FeedEffect.ShowError -> {
                    val message =
                        when (effect.error) {
                            FeedError.Network -> networkErrorMessage
                            FeedError.Unauthenticated -> unauthErrorMessage
                            is FeedError.Unknown -> unknownErrorMessage
                        }
                    // Replace, don't stack — successive errors during a flapping
                    // network spell would otherwise queue snackbars indefinitely.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }
                is FeedEffect.NavigateToPost -> currentOnNavigateToPost(effect.post)
                is FeedEffect.NavigateToAuthor -> currentOnNavigateToAuthor(effect.authorDid)
            }
        }
    }

    FeedScreenContent(
        viewState = viewState,
        listState = listState,
        snackbarHostState = snackbarHostState,
        callbacks = callbacks,
        onRefresh = onRefresh,
        onRetry = onRetry,
        onLoadMore = onLoadMore,
        modifier = modifier,
    )
}

/**
 * Stateless screen body. Takes the projected [FeedScreenViewState] and
 * the small set of callbacks the host wires to VM events. Previews and
 * Compose UI tests invoke this directly with fixture inputs — no
 * ViewModel, no Hilt graph, no live network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedScreenContent(
    viewState: FeedScreenViewState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    callbacks: PostCallbacks,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                ) {
                    items(count = SHIMMER_PREVIEW_COUNT, key = { "shimmer-$it" }) { index ->
                        PostCardShimmer(showImagePlaceholder = index % 2 == 0)
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
                LoadedFeedContent(
                    posts = viewState.posts,
                    isAppending = viewState.isAppending,
                    isRefreshing = viewState.isRefreshing,
                    listState = listState,
                    callbacks = callbacks,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                    contentPadding = padding,
                )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedFeedContent(
    posts: ImmutableList<PostUi>,
    isAppending: Boolean,
    isRefreshing: Boolean,
    listState: LazyListState,
    callbacks: PostCallbacks,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    // Coordinator is composition-scoped: created once per FeedScreen
    // entry, released on exit. No `remember(key)` because the screen's
    // composition lifetime IS the coordinator's lifetime. Inspection
    // mode (IDE preview / screenshot tests) skips construction —
    // ExoPlayer in layoutlib is unsafe.
    val context = LocalContext.current
    val inInspection = LocalInspectionMode.current
    val coordinator: FeedVideoPlayerCoordinator? =
        remember {
            if (inInspection) {
                null
            } else {
                createFeedVideoPlayerCoordinator(
                    context = context,
                    audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
                )
            }
        }
    if (coordinator != null) {
        DisposableEffect(Unit) {
            onDispose { coordinator.release() }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // contentPadding (NOT Modifier.padding on the parent) — keeps the
            // LazyColumn surface extending behind translucent system bars
            // while pushing the first/last items into the safe area. The
            // pagination snapshotFlow's visibleItemsInfo already accounts
            // for contentPadding so the prefetch threshold is unaffected.
            contentPadding = contentPadding,
        ) {
            items(items = posts, key = { it.id }, contentType = { "post" }) { post ->
                // Hoist the videoEmbedSlot lambda so it's stable across
                // recompositions of this item — without this, every
                // recomposition allocates a fresh closure per video card.
                // Inspection mode (preview / screenshot tests) gets the
                // phase-B static-poster variant so the screen-level
                // previews stay layoutlib-safe.
                val videoSlot: @Composable (EmbedUi.Video) -> Unit =
                    remember(post.id, coordinator) {
                        { video ->
                            if (coordinator != null) {
                                PostCardVideoEmbed(
                                    video = video,
                                    postId = post.id,
                                    coordinator = coordinator,
                                )
                            } else {
                                PostCardVideoEmbed(video = video)
                            }
                        }
                    }
                PostCard(
                    post = post,
                    callbacks = callbacks,
                    videoEmbedSlot = videoSlot,
                )
            }
            if (isAppending) {
                item(key = "appending", contentType = "appending") {
                    FeedAppendingIndicator()
                }
            }
        }
    }

    // Pagination trigger — emit exactly once per crossing of the
    // (lastVisibleIndex > posts.size - PREFETCH_DISTANCE) threshold.
    // The threshold check lives INSIDE snapshotFlow's lambda so
    // distinctUntilChanged() debounces the *boolean*, not the index;
    // without that, every visible-index change past the threshold would
    // re-fire onLoadMore (10–30/s during scroll). `rememberUpdatedState`
    // lets the long-lived collector read the latest `posts` and
    // `onLoadMore` without restarting the LaunchedEffect on every page
    // append (snapshotFlow re-emits when the wrapped State changes).
    val currentPosts by rememberUpdatedState(posts)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            lastVisible > currentPosts.size - PREFETCH_DISTANCE
        }.distinctUntilChanged()
            .collect { pastThreshold ->
                if (pastThreshold) currentOnLoadMore()
            }
    }

    // Scroll-gated bind flow (per design.md Decision 1 + spec). The
    // outer `snapshotFlow` watches `isScrollInProgress` and only when
    // it flips to `false` (scroll has settled) do we run the
    // visibility math — no per-frame `mostVisibleVideoTarget`
    // computation during a fling. `MostVisibleVideoTargetTest`
    // verifies the visibility math; this wiring is too thin to test
    // on its own.
    if (coordinator != null) {
        // Memoize the postId -> PostUi map across recompositions of
        // LoadedFeedContent (refresh tick, append, like-toggle, etc.).
        // Recomputed only when `posts` itself changes.
        val postsById = remember(posts) { posts.associateBy { it.id } }
        val currentPostsById by rememberUpdatedState(postsById)
        LaunchedEffect(listState, coordinator) {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .filter { scrolling -> !scrolling }
                .map {
                    mostVisibleVideoTarget(
                        layoutInfo = listState.layoutInfo,
                        postsById = currentPostsById,
                    )
                }.distinctUntilChanged()
                .collect { target -> coordinator.bindMostVisibleVideo(target) }
        }
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
                    posts = previewPosts(count = 5),
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
                    posts = previewPosts(count = 5),
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
                    posts = previewPosts(count = 5),
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

private fun previewPosts(count: Int): ImmutableList<PostUi> =
    (1..count)
        .map { id ->
            PostUi(
                id = "post-$id",
                author =
                    AuthorUi(
                        did = "did:plc:preview-$id",
                        handle = "preview$id.bsky.social",
                        displayName = "Preview $id",
                        avatarUrl = null,
                    ),
                createdAt = PREVIEW_CREATED_AT,
                text = "Preview post $id — sample timeline content for the feed-screen previews.",
                facets = persistentListOf(),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(replyCount = 1, repostCount = 2, likeCount = 12),
                viewer = ViewerStateUi(),
                repostedBy = null,
            )
        }.toImmutableList()
