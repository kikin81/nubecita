package net.kikin.nubecita.feature.feed.impl

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalScrollToTopSignal
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.data.models.quotedRecord
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.designsystem.component.PostCardShimmer
import net.kikin.nubecita.designsystem.component.ThreadCluster
import net.kikin.nubecita.feature.feed.impl.share.launchPostShare
import net.kikin.nubecita.feature.feed.impl.ui.FeedAppendingIndicator
import net.kikin.nubecita.feature.feed.impl.ui.FeedEmptyState
import net.kikin.nubecita.feature.feed.impl.ui.FeedErrorState
import net.kikin.nubecita.feature.feed.impl.ui.PostCardVideoEmbed
import net.kikin.nubecita.feature.feed.impl.video.FeedVideoPlayerCoordinator
import net.kikin.nubecita.feature.feed.impl.video.createFeedVideoPlayerCoordinator
import net.kikin.nubecita.feature.feed.impl.video.mostVisibleVideoTarget
import kotlin.time.Clock
import kotlin.time.Instant
import android.net.Uri as AndroidUri

private const val PREFETCH_DISTANCE = 5
private const val SHIMMER_PREVIEW_COUNT = 6

/**
 * The FAB starts revealing once the user has scrolled five items past
 * the top — roughly one screen of feed posts on a phone. Lower thresholds
 * fire too eagerly (FAB appears after a tiny scroll); higher thresholds
 * make the user scroll a long way before the affordance shows up.
 */
private const val SCROLL_TO_TOP_FAB_THRESHOLD = 5

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
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToAuthor: (String) -> Unit = {},
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewState = remember(state) { state.toViewState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val callbacks =
        remember(viewModel, context) {
            PostCallbacks(
                onTap = { viewModel.handleEvent(FeedEvent.OnPostTapped(it)) },
                onAuthorTap = { viewModel.handleEvent(FeedEvent.OnAuthorTapped(it.did)) },
                onLike = { viewModel.handleEvent(FeedEvent.OnLikeClicked(it)) },
                onRepost = { viewModel.handleEvent(FeedEvent.OnRepostClicked(it)) },
                onReply = { viewModel.handleEvent(FeedEvent.OnReplyClicked(it)) },
                onShare = { viewModel.handleEvent(FeedEvent.OnShareClicked(it)) },
                onShareLongPress = { viewModel.handleEvent(FeedEvent.OnShareLongPressed(it)) },
                onExternalEmbedTap = { uri ->
                    // Narrowed catch: silent no-op only for the documented
                    // "no CCT-capable browser installed" case (per
                    // nubecita-aku scope). Other launch failures (rare
                    // SecurityException / RuntimeException from the
                    // browser lib) propagate so genuine bugs surface in
                    // logcat instead of being hidden by a blanket catch.
                    try {
                        CustomTabsIntent
                            .Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(context, AndroidUri.parse(uri))
                    } catch (_: ActivityNotFoundException) {
                        // No browser available — silent no-op.
                    }
                },
            )
        }
    // Pre-resolve snackbar copy via stringResource() at composition time
    // so locale + dark-mode changes participate in recomposition. Reading
    // them via context.getString(...) inside the LaunchedEffect would
    // bypass Compose's resource tracking (lint: LocalContextGetResourceValueCall).
    val networkErrorMessage = stringResource(R.string.feed_snackbar_error_network)
    val unauthErrorMessage = stringResource(R.string.feed_snackbar_error_unauthenticated)
    val unknownErrorMessage = stringResource(R.string.feed_snackbar_error_unknown)
    val linkCopiedMessage = stringResource(R.string.feed_snackbar_link_copied)
    val clipLabel = stringResource(R.string.feed_clipboard_label_post_link)
    val clipboardManager =
        remember(context) {
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }
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
                is FeedEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is FeedEffect.NavigateToAuthor -> currentOnNavigateToAuthor(effect.authorDid)
                is FeedEffect.SharePost -> context.launchPostShare(effect.intent)
                is FeedEffect.CopyPermalink -> {
                    clipboardManager.setPrimaryClip(
                        ClipData.newPlainText(clipLabel, effect.permalink),
                    )
                    // Replace any pending error snackbar — a fresh "link
                    // copied" confirmation outranks a stale network error
                    // for the moment the user just took action.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = linkCopiedMessage)
                }
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
    // Tap-to-top: collect MainShell's tab-retap signal and scroll the
    // feed list to the top. The default empty SharedFlow (no provider in
    // previews / screenshot tests) never emits, so collecting in those
    // contexts is a runtime no-op. Keyed on (signal, listState) so the
    // collector restarts cleanly across recompositions that re-create
    // either reference.
    val scrollToTopSignal = LocalScrollToTopSignal.current
    val scrollScope = rememberCoroutineScope()
    LaunchedEffect(scrollToTopSignal, listState) {
        scrollToTopSignal.collect { listState.animateScrollToItem(0) }
    }
    // FAB visibility: gated on BOTH the loaded viewState AND the scroll
    // threshold. The listState is hoisted at the FeedScreen level and
    // retains `firstVisibleItemIndex` across viewState transitions, so
    // checking only the index would let the FAB linger over Empty /
    // InitialLoading / InitialError surfaces (e.g. sign-out → feed
    // becomes Empty while the prior scroll position is still cached).
    // The viewState gate keeps the affordance scoped to the only state
    // that actually has a list to scroll.
    //
    // `derivedStateOf` debounces against per-frame scroll updates: the
    // surrounding composition only invalidates when the boolean flips
    // (a few times per scroll session, not 60–120 fps). Same Compose-
    // perf pattern used by m28.5.2's PostDetail FAB.
    val showScrollToTopFab by remember(listState, viewState) {
        derivedStateOf {
            viewState is FeedScreenViewState.Loaded &&
                listState.firstVisibleItemIndex >= SCROLL_TO_TOP_FAB_THRESHOLD
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // AnimatedVisibility wraps the FAB so the appearance / dismissal
            // fades + scales rather than popping in.
            AnimatedVisibility(
                visible = showScrollToTopFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                SmallFloatingActionButton(
                    onClick = { scrollScope.launch { listState.animateScrollToItem(0) } },
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.feed_scroll_to_top),
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
                    feedItems = viewState.feedItems,
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
    feedItems: ImmutableList<FeedItemUi>,
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
            items(
                items = feedItems,
                key = { it.key },
                contentType = {
                    when (it) {
                        is FeedItemUi.Single -> "single"
                        is FeedItemUi.ReplyCluster -> "cluster"
                        is FeedItemUi.SelfThreadChain -> "chain"
                    }
                },
            ) { item ->
                // The video coordinator binds against the leaf URI for
                // Single, ReplyCluster, AND SelfThreadChain — clusters
                // and chains are leaf-only video targets (see
                // ThreadCluster KDoc + design.md decision D3 from m28.3,
                // applied here by symmetry per
                // `add-feed-same-author-thread-chain` task 5.2).
                val leaf =
                    when (item) {
                        is FeedItemUi.Single -> item.post
                        is FeedItemUi.ReplyCluster -> item.leaf
                        is FeedItemUi.SelfThreadChain -> item.posts.last()
                    }
                // Hoist the videoEmbedSlot lambda so it's stable across
                // recompositions of this item — without this, every
                // recomposition allocates a fresh closure per video card.
                // Inspection mode (preview / screenshot tests) gets the
                // phase-B static-poster variant so the screen-level
                // previews stay layoutlib-safe.
                val videoSlot: @Composable (EmbedUi.Video) -> Unit =
                    remember(leaf.id, coordinator) {
                        { video ->
                            if (coordinator != null) {
                                PostCardVideoEmbed(
                                    video = video,
                                    postId = leaf.id,
                                    coordinator = coordinator,
                                )
                            } else {
                                PostCardVideoEmbed(video = video)
                            }
                        }
                    }
                // Quoted-post video slot. Bind identity is the QUOTED
                // post's URI (per mostVisibleVideoTarget's
                // videoBindingFor) so the coordinator naturally
                // distinguishes parent vs quoted videos. Slot is null
                // when this item doesn't carry a quoted post (whether
                // top-level EmbedUi.Record or nested inside an
                // EmbedUi.RecordWithMedia.record) — the `quotedRecord`
                // extension covers both shapes via a single source of
                // truth in :data:models. The remember sits at the
                // same call site every recomposition (key flip drops
                // the lambda cleanly).
                val quotedVideoUri = leaf.embed.quotedRecord?.uri
                val quotedVideoSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? =
                    remember(quotedVideoUri, coordinator) {
                        if (quotedVideoUri == null) {
                            null
                        } else {
                            { qVideo ->
                                if (coordinator != null) {
                                    PostCardVideoEmbed(
                                        quotedVideo = qVideo,
                                        postId = quotedVideoUri,
                                        coordinator = coordinator,
                                    )
                                } else {
                                    PostCardVideoEmbed(quotedVideo = qVideo)
                                }
                            }
                        }
                    }
                when (item) {
                    is FeedItemUi.Single ->
                        PostCard(
                            post = item.post,
                            callbacks = callbacks,
                            videoEmbedSlot = videoSlot,
                            quotedVideoEmbedSlot = quotedVideoSlot,
                        )
                    is FeedItemUi.ReplyCluster ->
                        ThreadCluster(
                            root = item.root,
                            parent = item.parent,
                            leaf = item.leaf,
                            callbacks = callbacks,
                            hasEllipsis = item.hasEllipsis,
                            leafVideoEmbedSlot = videoSlot,
                            leafQuotedVideoEmbedSlot = quotedVideoSlot,
                            // Tapping "View full thread" routes to the cluster's
                            // leaf URI — same MVI dispatch a body tap on the
                            // leaf would use. Per CLAUDE.md / m28.5.1 acceptance
                            // criteria: "ThreadFold tap on a m28.3 cluster routes
                            // to PostDetail." The wiring was missed at m28.3
                            // ship time; restoring it here.
                            onFoldTap = { callbacks.onTap(item.leaf) },
                        )
                    is FeedItemUi.SelfThreadChain -> {
                        // Same-author chain: render N PostCards stacked
                        // vertically with avatar-gutter connector flags
                        // wired by index. Per design Decision 5: first
                        // → connectBelow only; middle → both; last →
                        // connectAbove only. Modifier.threadConnector
                        // (PR #77) draws the line through PostCard's
                        // existing connector machinery — no new
                        // :designsystem composable needed.
                        //
                        // Only the leaf post participates in the video
                        // coordinator (matches ReplyCluster's leaf-only
                        // video binding). Non-leaf chain posts get
                        // videoEmbedSlot = null, so any video embed in
                        // those positions collapses cleanly per
                        // PostCard's videoEmbedSlot KDoc.
                        val chainLastIndex = item.posts.lastIndex
                        Column {
                            item.posts.forEachIndexed { index, chainPost ->
                                val isLeaf = index == chainLastIndex
                                PostCard(
                                    post = chainPost,
                                    callbacks = callbacks,
                                    connectAbove = index > 0,
                                    connectBelow = index < chainLastIndex,
                                    videoEmbedSlot = if (isLeaf) videoSlot else null,
                                    quotedVideoEmbedSlot = if (isLeaf) quotedVideoSlot else null,
                                )
                            }
                        }
                    }
                }
            }
            if (isAppending) {
                item(key = "appending", contentType = "appending") {
                    FeedAppendingIndicator()
                }
            }
        }
    }

    // Pagination trigger — emit exactly once per crossing of the
    // (lastVisibleIndex > feedItems.size - PREFETCH_DISTANCE) threshold.
    // The threshold check lives INSIDE snapshotFlow's lambda so
    // distinctUntilChanged() debounces the *boolean*, not the index;
    // without that, every visible-index change past the threshold would
    // re-fire onLoadMore (10–30/s during scroll). `rememberUpdatedState`
    // lets the long-lived collector read the latest `feedItems` and
    // `onLoadMore` without restarting the LaunchedEffect on every page
    // append (snapshotFlow re-emits when the wrapped State changes).
    val currentFeedItems by rememberUpdatedState(feedItems)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            lastVisible > currentFeedItems.size - PREFETCH_DISTANCE
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
        // Memoize the leafId -> PostUi map across recompositions of
        // LoadedFeedContent (refresh tick, append, like-toggle, etc.).
        // Recomputed only when `feedItems` itself changes. For ReplyCluster
        // entries, only the leaf is registered — root + parent posts in a
        // cluster receive videoEmbedSlot = null and don't participate in
        // the coordinator (design D3).
        val postsById =
            remember(feedItems) {
                feedItems.associate { item ->
                    when (item) {
                        is FeedItemUi.Single -> item.post.id to item.post
                        is FeedItemUi.ReplyCluster -> item.leaf.id to item.leaf
                        is FeedItemUi.SelfThreadChain -> item.posts.last().id to item.posts.last()
                    }
                }
            }
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
