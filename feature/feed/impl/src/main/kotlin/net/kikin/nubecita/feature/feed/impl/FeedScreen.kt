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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
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
import androidx.window.core.layout.WindowSizeClass
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.core.common.haptic.rememberPostHaptics
import net.kikin.nubecita.core.common.navigation.LocalComposerSubmitEvents
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
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
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
    onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit = { _, _ -> },
    onComposeClick: () -> Unit = {},
    onReplyClick: (String) -> Unit = {},
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewState = remember(state) { state.toViewState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Reply tap is screen-level — does NOT pass through FeedViewModel
    // (per the unified-composer spec, step 10). The host wires
    // `onReplyClick` to the width-conditional composer launcher; keep
    // the lambda identity stable across recompositions via
    // rememberUpdatedState so the remembered PostCallbacks below
    // captures the live reference instead of a stale snapshot. Declared
    // before `callbacks` because the lambda inside `PostCallbacks.onReply`
    // closes over it.
    val currentOnReplyClick by rememberUpdatedState(onReplyClick)
    val haptics = rememberPostHaptics()
    val callbacks =
        remember(viewModel, context, haptics) {
            PostCallbacks(
                onTap = { viewModel.handleEvent(FeedEvent.OnPostTapped(it)) },
                onAuthorTap = { viewModel.handleEvent(FeedEvent.OnAuthorTapped(it.did)) },
                onLike = { post ->
                    if (post.viewer.isLikedByViewer) haptics.likeOff() else haptics.likeOn()
                    viewModel.handleEvent(FeedEvent.OnLikeClicked(post))
                },
                onRepost = { post ->
                    if (post.viewer.isRepostedByViewer) haptics.repostOff() else haptics.repostOn()
                    viewModel.handleEvent(FeedEvent.OnRepostClicked(post))
                },
                onReply = { post ->
                    haptics.lightTap()
                    currentOnReplyClick(post.id)
                },
                onShare = { post ->
                    haptics.lightTap()
                    viewModel.handleEvent(FeedEvent.OnShareClicked(post))
                },
                // Long-press already fires the system long-press haptic via
                // combinedClickable — don't double-tap the motor.
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
                onQuotedPostTap = { quoted ->
                    viewModel.handleEvent(FeedEvent.OnQuotedPostTapped(quoted.uri))
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
    val postPublishedMessage = stringResource(R.string.feed_snackbar_post_published)
    val replyPublishedMessage = stringResource(R.string.feed_snackbar_reply_published)
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
    val currentOnNavigateToMediaViewer by rememberUpdatedState(onNavigateToMediaViewer)
    // Per-PostCard image tap dispatcher. The PostCard.onImageClick slot
    // is `(Int) -> Unit` (index only); we close over each PostCard's
    // own `post` at the call site to form a `(PostUi, Int) -> Unit`
    // dispatch that the VM can turn into a NavigateToMediaViewer effect.
    val onImageTap =
        remember(viewModel) {
            { post: PostUi, index: Int -> viewModel.handleEvent(FeedEvent.OnImageTapped(post, index)) }
        }
    // Hoist the VM-dispatching callbacks. Inline lambdas at the
    // FeedScreenContent call site would create new instances per
    // recomposition; with the FeedScreenContent body skip-friendly
    // (all params @Stable / @Immutable), preserving lambda identity
    // here lets it skip recomposition when only `viewState` changes.
    val onRefresh = remember(viewModel) { { viewModel.handleEvent(FeedEvent.Refresh) } }
    val onRetry = remember(viewModel) { { viewModel.handleEvent(FeedEvent.Retry) } }
    val onLoadMore = remember(viewModel) { { viewModel.handleEvent(FeedEvent.LoadMore) } }

    // Coordinator is hoisted here so it can receive the Hilt-injected
    // SharedVideoPlayer from the ViewModel. Keyed on viewModel so a VM
    // re-creation (process death + restore) rebuilds the coordinator
    // with the fresh holder reference. Inspection mode skips construction
    // — ExoPlayer in layoutlib is unsafe.
    val inInspection = LocalInspectionMode.current
    val appContext = context.applicationContext
    val audioManager =
        remember(context) {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
    val coordinator: FeedVideoPlayerCoordinator? =
        remember(viewModel) {
            if (inInspection) {
                null
            } else {
                createFeedVideoPlayerCoordinator(
                    context = appContext,
                    audioManager = audioManager,
                    sharedVideoPlayer = viewModel.sharedVideoPlayer,
                )
            }
        }
    if (coordinator != null) {
        DisposableEffect(viewModel) {
            onDispose { coordinator.release() }
        }
    }

    LaunchedEffect(Unit) { viewModel.handleEvent(FeedEvent.Load) }

    // Composer submit-success bus. Fires when either composer host
    // (Compact NavDisplay route or Medium / Expanded Dialog overlay)
    // emits `OnSubmitSuccess`. Two side effects:
    //  - dispatch the optimistic `replyCount + 1` event when the
    //    submit was a reply, so the parent post in the feed reflects
    //    the new count without waiting for the next refresh.
    //  - show a confirmation snackbar with reply- vs new-post copy.
    //
    // `collectLatest` (not `collect`) — `showSnackbar` suspends until
    // dismissal, so back-to-back submits with plain `collect` would
    // queue behind the still-visible snackbar (they only resolve into
    // a fresh `dismiss + showSnackbar` once the previous one finally
    // closes, ~4s later). `collectLatest` cancels the in-flight body
    // when a new submit arrives, dismissing the prior snackbar and
    // showing the new confirmation immediately. Keyed on the flow +
    // viewModel + snackbarHostState so the collector restarts cleanly
    // across recompositions that re-create any of those references.
    val composerSubmitEvents = LocalComposerSubmitEvents.current
    LaunchedEffect(composerSubmitEvents, viewModel, snackbarHostState) {
        composerSubmitEvents.collectLatest { event ->
            event.replyToUri?.let { parentUri ->
                viewModel.handleEvent(FeedEvent.OnReplySubmittedToParent(parentUri))
            }
            val message =
                if (event.replyToUri != null) replyPublishedMessage else postPublishedMessage
            // Replace any pending snackbar — fresh confirmation outranks
            // any stale error message lingering from a prior load.
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = message)
        }
    }

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
                    // FeedEffect.ShowError is only emitted from the
                    // like/repost toggle failure paths today (the initial-
                    // load error path goes through sticky FeedLoadStatus.
                    // InitialError instead). Reject haptic here is the
                    // toggle-was-rejected cue the bd requested.
                    haptics.rejected()
                    // Replace, don't stack — successive errors during a flapping
                    // network spell would otherwise queue snackbars indefinitely.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }
                is FeedEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is FeedEffect.NavigateToAuthor -> currentOnNavigateToAuthor(effect.authorDid)
                is FeedEffect.NavigateToMediaViewer ->
                    currentOnNavigateToMediaViewer(effect.postUri, effect.imageIndex)
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
        onComposeClick = onComposeClick,
        onImageTap = onImageTap,
        coordinator = coordinator,
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
    modifier: Modifier = Modifier,
    onComposeClick: () -> Unit = {},
    onImageTap: (post: PostUi, imageIndex: Int) -> Unit = { _, _ -> },
    coordinator: FeedVideoPlayerCoordinator? = null,
) {
    // Tap-to-top: collect MainShell's tab-retap signal and scroll the
    // feed list to the top. The default empty SharedFlow (no provider in
    // previews / screenshot tests) never emits, so collecting in those
    // contexts is a runtime no-op. Keyed on (signal, listState) so the
    // collector restarts cleanly across recompositions that re-create
    // either reference.
    val scrollToTopSignal = LocalScrollToTopSignal.current
    LaunchedEffect(scrollToTopSignal, listState) {
        scrollToTopSignal.collect { listState.animateScrollToItem(0) }
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
    // via the `LocalScrollToTopSignal` collector above.
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
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    LargeFloatingActionButton(onClick = onComposeClick) {
                        NubecitaIcon(
                            name = NubecitaIconName.Edit,
                            contentDescription = stringResource(R.string.feed_compose_new_post),
                            filled = true,
                        )
                    }
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
                    onImageTap = onImageTap,
                    contentPadding = padding,
                    lastLikeTapPostUri = viewState.lastLikeTapPostUri,
                    lastRepostTapPostUri = viewState.lastRepostTapPostUri,
                    coordinator = coordinator,
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
    onImageTap: (post: PostUi, imageIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    lastLikeTapPostUri: String? = null,
    lastRepostTapPostUri: String? = null,
    coordinator: FeedVideoPlayerCoordinator? = null,
) {
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
                            onImageClick = { idx -> onImageTap(item.post, idx) },
                            animateLikeTap = item.post.id == lastLikeTapPostUri,
                            animateRepostTap = item.post.id == lastRepostTapPostUri,
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
                            onImageClick = onImageTap,
                            lastLikeTapPostUri = lastLikeTapPostUri,
                            lastRepostTapPostUri = lastRepostTapPostUri,
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
                                    onImageClick = { idx -> onImageTap(chainPost, idx) },
                                    animateLikeTap = chainPost.id == lastLikeTapPostUri,
                                    animateRepostTap = chainPost.id == lastRepostTapPostUri,
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
