package net.kikin.nubecita.feature.postdetail.impl

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.feature.postdetail.impl.data.ThreadItem
import timber.log.Timber
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Hilt-aware post-detail screen.
 *
 * Owns the screen's lifecycle wiring: state collection, the single
 * `effects` collector that surfaces snackbars + nav callbacks, the
 * `remember`-d `PostCallbacks` that dispatch to the VM, and the
 * one-shot `LaunchedEffect(Unit)` that fires `PostDetailEvent.Load` on
 * first composition. Delegates the actual rendering to
 * [PostDetailScreenContent] which previews and tests can call directly
 * with fixture inputs (no ViewModel, no Hilt graph).
 *
 * # m28.5.1 visual scope
 *
 * Plain `LazyColumn` rendering each [ThreadItem] as the existing
 * `:designsystem` PostCard, wrapped in a stock M3 `PullToRefreshBox`
 * with the M3 Expressive `PullToRefreshDefaults.LoadingIndicator`
 * (morphing-polygon shape) so swipe-down dispatches
 * [PostDetailEvent.Refresh] — snackbar copy already says "Pull to
 * retry". Standard M3 `TopAppBar` with back arrow. No expressive
 * container hierarchy, no carousel, no floating composer — those land
 * in m28.5.2. Reviewers should be able to tell at a glance "this PR
 * isn't trying to look pretty yet."
 */
@Composable
internal fun PostDetailScreen(
    viewModel: PostDetailViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToAuthor: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val callbacks =
        remember(viewModel) {
            // m28.5.1 wires only the navigation callbacks. Like / repost /
            // share / reply land with the visual treatment in m28.5.2; the
            // `PostCallbacks.None`-shaped defaults make those gestures
            // no-op silently here without TalkBack announcing them (see
            // PostCallbacks KDoc on `onShareLongPress = null`).
            PostCallbacks(
                onTap = { viewModel.handleEvent(PostDetailEvent.OnPostTapped(it.id)) },
                onAuthorTap = { viewModel.handleEvent(PostDetailEvent.OnAuthorTapped(it.did)) },
            )
        }

    val onRetry = remember(viewModel) { { viewModel.handleEvent(PostDetailEvent.Retry) } }
    val onRefresh = remember(viewModel) { { viewModel.handleEvent(PostDetailEvent.Refresh) } }
    val onReply = remember(viewModel) { { viewModel.handleEvent(PostDetailEvent.OnReplyClicked) } }
    val onFocusImageClick =
        remember(viewModel) {
            { index: Int -> viewModel.handleEvent(PostDetailEvent.OnFocusImageClicked(imageIndex = index)) }
        }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToAuthor by rememberUpdatedState(onNavigateToAuthor)

    // Pre-resolve snackbar copy at composition time so locale changes
    // participate in recomposition (lint: LocalContextGetResourceValueCall).
    // The four resolutions always run; cost is a cached resource lookup.
    // Don't push these into the LaunchedEffect's `when` branch — that
    // re-trips the lint, and the effect collector doesn't see Configuration
    // changes the way composition does.
    val networkErrorMessage = stringResource(R.string.postdetail_snackbar_error_network)
    val unauthErrorMessage = stringResource(R.string.postdetail_snackbar_error_unauthenticated)
    val notFoundErrorMessage = stringResource(R.string.postdetail_snackbar_error_notfound)
    val unknownErrorMessage = stringResource(R.string.postdetail_snackbar_error_unknown)
    val composerComingSoonMessage = stringResource(R.string.postdetail_snackbar_composer_coming_soon)
    val mediaViewerComingSoonMessage = stringResource(R.string.postdetail_snackbar_media_viewer_coming_soon)

    LaunchedEffect(Unit) { viewModel.handleEvent(PostDetailEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PostDetailEffect.ShowError -> {
                    val message =
                        when (effect.error) {
                            PostDetailError.Network -> networkErrorMessage
                            PostDetailError.Unauthenticated -> unauthErrorMessage
                            PostDetailError.NotFound -> notFoundErrorMessage
                            is PostDetailError.Unknown -> unknownErrorMessage
                        }
                    // Replace, don't stack — successive errors during a flapping
                    // network spell would otherwise queue snackbars indefinitely.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }
                is PostDetailEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is PostDetailEffect.NavigateToAuthor -> currentOnNavigateToAuthor(effect.authorDid)
                is PostDetailEffect.NavigateToComposer -> {
                    // The composer feature module's NavKey is tracked under
                    // nubecita-8f6.3 and is not yet wired into
                    // :core:common:navigation. Until it lands, log a Timber
                    // breadcrumb and surface a transient acknowledgement
                    // Snackbar so the FAB tap registers tactile feedback
                    // without blocking the user the way a dialog would.
                    Timber.tag("PostDetailScreen").d(
                        "NavigateToComposer for parent=%s — composer route not yet wired (nubecita-8f6.3); falling back to Snackbar",
                        effect.parentPostUri,
                    )
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = composerComingSoonMessage)
                }
                is PostDetailEffect.NavigateToMediaViewer -> {
                    // The fullscreen media viewer route does not yet exist
                    // in :core:common:navigation — tracked under nubecita-e02.
                    // Same acknowledgement-not-broken pattern as
                    // NavigateToComposer above. Removal site is grep-able via
                    // this Timber tag + the Snackbar string id.
                    Timber.tag("PostDetailScreen").d(
                        "NavigateToMediaViewer for post=%s index=%d — media viewer route not yet wired (nubecita-e02); falling back to Snackbar",
                        effect.postUri,
                        effect.imageIndex,
                    )
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = mediaViewerComingSoonMessage)
                }
            }
        }
    }

    PostDetailScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        callbacks = callbacks,
        onBack = currentOnBack,
        onRetry = onRetry,
        onRefresh = onRefresh,
        onReply = onReply,
        onFocusImageClick = onFocusImageClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostDetailScreenContent(
    state: PostDetailState,
    snackbarHostState: SnackbarHostState,
    callbacks: PostCallbacks,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onReply: () -> Unit = {},
    onFocusImageClick: (Int) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.postdetail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.postdetail_back_content_description),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // Visible in the loaded states (Idle / Refreshing) — design
            // Decision 3 says "always visible, no hide-on-scroll", which
            // refers to scroll behavior; gating against initial-loading /
            // initial-error keeps the FAB from advertising a "Reply"
            // action when the post hasn't resolved yet (TalkBack would
            // announce it as actionable). Standard FloatingActionButton
            // (M3 baseline elevation, circle shape, primaryContainer tint
            // by default); the catalog's material3 1.5.0-alpha18 ships
            // M3 Expressive size variants but the design intent is
            // "Threads-style without the Threads-shape" — the standard
            // FAB nails the vocabulary without reaching for an Expressive
            // size we'd then have to tune for the bottom-padding clearance.
            val showFab =
                state.loadStatus is PostDetailLoadStatus.Idle ||
                    state.loadStatus is PostDetailLoadStatus.Refreshing
            if (showFab) {
                FloatingActionButton(onClick = onReply) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = stringResource(R.string.postdetail_reply_fab_content_description),
                    )
                }
            }
        },
    ) { padding ->
        when (val status = state.loadStatus) {
            PostDetailLoadStatus.InitialLoading ->
                LoadingState(contentPadding = padding)
            is PostDetailLoadStatus.InitialError ->
                ErrorState(
                    error = status.error,
                    onRetry = onRetry,
                    contentPadding = padding,
                )
            PostDetailLoadStatus.Idle,
            PostDetailLoadStatus.Refreshing,
            ->
                LoadedThread(
                    items = state.items,
                    isRefreshing = status is PostDetailLoadStatus.Refreshing,
                    onRefresh = onRefresh,
                    callbacks = callbacks,
                    onFocusImageClick = onFocusImageClick,
                    contentPadding = padding,
                )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedThread(
    items: ImmutableList<ThreadItem>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    callbacks: PostCallbacks,
    onFocusImageClick: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    // Hoist the state so the same instance feeds both PullToRefreshBox
    // (which drives `distanceFraction` from the gesture) and the indicator
    // slot (which reads `distanceFraction` to morph the polygon shapes).
    // Without sharing, the indicator would render against an independent
    // state and never animate.
    val pullState = rememberPullToRefreshState()
    // Bottom contentPadding clearance for the FAB so the bottom-most reply
    // can scroll fully above the floating composer affordance at end-of-
    // thread scroll position. Per design.md Decision 3 occlusion safeguard
    // (~80–100dp combined): 56dp standard FAB + 16dp Material edge spacing
    // + 16dp safety margin = 88dp.
    val mergedContentPadding =
        PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + FAB_BOTTOM_CLEARANCE,
            start = 0.dp,
            end = 0.dp,
        )
    PullToRefreshBox(
        state = pullState,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            // M3 Expressive contained LoadingIndicator — the morphing
            // polygon shape from material.io's pull-to-refresh sample,
            // pinned to the box's top-center per Material guidance.
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = mergedContentPadding,
        ) {
            items(items = items, key = { it.key }) { item ->
                when (item) {
                    is ThreadItem.Ancestor -> PostCard(post = item.post, callbacks = callbacks)
                    is ThreadItem.Focus ->
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(FOCUS_CONTAINER_CORNER_RADIUS),
                        ) {
                            // Per task 4.3: ancestor / reply PostCards do NOT
                            // wire onImageClick — taps on those images stay
                            // no-op for v1. Only the Focus PostCard surfaces
                            // the per-image-index callback.
                            PostCard(
                                post = item.post,
                                callbacks = callbacks,
                                onImageClick = onFocusImageClick,
                            )
                        }
                    is ThreadItem.Reply -> PostCard(post = item.post, callbacks = callbacks)
                    is ThreadItem.Blocked ->
                        InlineUnavailableRow(
                            label = stringResource(R.string.postdetail_inline_blocked),
                        )
                    is ThreadItem.NotFound ->
                        InlineUnavailableRow(
                            label = stringResource(R.string.postdetail_inline_notfound),
                        )
                    is ThreadItem.Fold -> {
                        // m28.5.1 mapper does not emit Fold; leaving the case
                        // explicit here so the exhaustive-when stays compile-
                        // checked. m28.5.2's visual treatment will render a
                        // "View more" affordance here.
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineUnavailableRow(label: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingState(contentPadding: PaddingValues) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    error: PostDetailError,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
) {
    val titleRes =
        when (error) {
            PostDetailError.Network -> R.string.postdetail_error_network_title
            PostDetailError.Unauthenticated -> R.string.postdetail_error_unauthenticated_title
            PostDetailError.NotFound -> R.string.postdetail_error_notfound_title
            is PostDetailError.Unknown -> R.string.postdetail_error_unknown_title
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            // NotFound is terminal — retry can't recover (the post is
            // gone). Suppress the action button for that variant; show
            // it for every recoverable error.
            if (error !is PostDetailError.NotFound) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.postdetail_error_action))
                }
            }
        }
    }
}

// ---------- Previews -------------------------------------------------------

@Preview(name = "InitialLoading", showBackground = true)
@Preview(name = "InitialLoading — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenInitialLoadingPreview() {
    NubecitaTheme {
        PostDetailScreenPreviewHost(
            state = PostDetailState(loadStatus = PostDetailLoadStatus.InitialLoading),
        )
    }
}

@Preview(name = "InitialError — Network", showBackground = true)
@Preview(name = "InitialError — Network — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenInitialErrorNetworkPreview() {
    NubecitaTheme {
        PostDetailScreenPreviewHost(
            state =
                PostDetailState(
                    loadStatus = PostDetailLoadStatus.InitialError(PostDetailError.Network),
                ),
        )
    }
}

@Preview(name = "InitialError — NotFound", showBackground = true)
@Preview(name = "InitialError — NotFound — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenInitialErrorNotFoundPreview() {
    NubecitaTheme {
        PostDetailScreenPreviewHost(
            state =
                PostDetailState(
                    loadStatus = PostDetailLoadStatus.InitialError(PostDetailError.NotFound),
                ),
        )
    }
}

@Preview(name = "Loaded", showBackground = true)
@Preview(name = "Loaded — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenLoadedPreview() {
    NubecitaTheme {
        PostDetailScreenPreviewHost(
            state = PostDetailState(items = previewThread(), loadStatus = PostDetailLoadStatus.Idle),
        )
    }
}

@Preview(name = "Loaded + Refreshing", showBackground = true)
@Preview(name = "Loaded + Refreshing — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenLoadedRefreshingPreview() {
    NubecitaTheme {
        PostDetailScreenPreviewHost(
            state = PostDetailState(items = previewThread(), loadStatus = PostDetailLoadStatus.Refreshing),
        )
    }
}

/**
 * Stateless preview/test host — wraps [PostDetailScreenContent] with a
 * fresh `SnackbarHostState` and a fixed clock so the call site only
 * supplies the `state` to vary across previews.
 */
@Composable
private fun PostDetailScreenPreviewHost(state: PostDetailState) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Provide a fixed clock so PostCard's relative-time label stays
    // deterministic across IDE re-renders — pairs with PREVIEW_CREATED_AT
    // below so the rendered relative-time label is "2h" forever.
    CompositionLocalProvider(LocalClock provides PreviewClock) {
        PostDetailScreenContent(
            state = state,
            snackbarHostState = snackbarHostState,
            callbacks = PostCallbacks.None,
            onBack = {},
            onRetry = {},
            onRefresh = {},
        )
    }
}

/**
 * Container shape radius for the Focus Post per `add-postdetail-m3-expressive-treatment`
 * design Decision 1. 24dp gives an ~8dp visual breathing margin between
 * PostCard's internal 16dp text padding and the surface's rounded edge.
 * Risks-section fallback: if the corner clips PostCard's padding awkwardly
 * (e.g. on a wide-screen tablet split-pane), drop to 20dp before introducing
 * any custom drawing.
 */
private val FOCUS_CONTAINER_CORNER_RADIUS = 24.dp

/**
 * Bottom contentPadding added to the LazyColumn so the bottom-most reply
 * scrolls fully above the floating composer FAB at end-of-thread. 88dp =
 * 56dp standard FAB diameter + 16dp Material edge spacing + 16dp safety
 * margin (per design.md Decision 3 occlusion safeguard, target 80–100dp
 * combined). Captured by the screenshot fixture at end-of-thread scroll
 * position.
 */
private val FAB_BOTTOM_CLEARANCE = 88.dp

private val PREVIEW_NOW = Instant.parse("2026-04-26T12:00:00Z")
private val PREVIEW_CREATED_AT = Instant.parse("2026-04-26T10:00:00Z")

private object PreviewClock : Clock {
    override fun now(): Instant = PREVIEW_NOW
}

private fun previewPost(
    id: String,
    text: String = "Preview post $id — sample post-detail content.",
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
 * Mixed thread fixture for the loaded previews: one ancestor, the
 * focus, an inline blocked sibling, and two top-level replies. Hits
 * every rendered ThreadItem variant in m28.5.1's mapper output (Fold
 * is reserved for m28.5.2 so it's omitted).
 */
private fun previewThread(): ImmutableList<ThreadItem> =
    persistentListOf<ThreadItem>(
        ThreadItem.Ancestor(post = previewPost("ancestor", text = "Ancestor — what kicked off the thread.")),
        ThreadItem.Focus(post = previewPost("focus", text = "Focused post — the one tapped from the feed.")),
        ThreadItem.Blocked(uri = "at://did:plc:blocked/app.bsky.feed.post/blocked"),
        ThreadItem.Reply(post = previewPost("reply-1", text = "Top-level reply — direct child of the focus."), depth = 1),
        ThreadItem.Reply(post = previewPost("reply-2", text = "Another top-level reply — sibling of reply-1."), depth = 1),
    )
