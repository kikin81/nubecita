package net.kikin.nubecita.feature.postdetail.impl

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import net.kikin.nubecita.core.common.haptic.rememberPostHaptics
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.core.postinteractions.sharing.launchPostShare
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.data.models.quotedRecord
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.BlockedPostCard
import net.kikin.nubecita.designsystem.component.MediaCover
import net.kikin.nubecita.designsystem.component.NotFoundPostCard
import net.kikin.nubecita.designsystem.component.NubecitaPullToRefreshBox
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.designsystem.component.VideoPosterEmbed
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import kotlin.time.Clock
import kotlin.time.Instant
import android.net.Uri as AndroidUri

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
 * `:designsystem` PostCard, wrapped in the shared
 * [net.kikin.nubecita.designsystem.component.NubecitaPullToRefreshBox]
 * (M3 Expressive morphing-polygon LoadingIndicator) so swipe-down dispatches
 * [PostDetailEvent.Refresh] — snackbar copy already says "Pull to
 * retry". Standard M3 `TopAppBar` with back arrow. No expressive
 * container hierarchy, no carousel, no floating composer — those land
 * in m28.5.2. Reviewers should be able to tell at a glance "this PR
 * isn't trying to look pretty yet."
 *
 * Suppresses VM-forwarding lints — see ComposerScreen / ProfileScreen
 * for the full rationale (slack compose-lints 1.5.0+ tightened
 * ComposeViewModelForwarding's data-flow analysis; conflicts with
 * ComposeViewModelInjection on stateful screens that hoist state).
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun PostDetailScreen(
    viewModel: PostDetailViewModel,
    onNavigateTo: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit = { _, _ -> },
    onNavigateToVideoPlayer: (postUri: String) -> Unit = {},
    onReplyClick: (String) -> Unit = {},
    onQuoteClick: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Reply tap on a per-post action row does NOT pass through
    // PostDetailViewModel (same shape as FeedScreen). The host wires
    // `onReplyClick` to the width-conditional composer launcher; keep
    // the lambda identity stable across recompositions via
    // rememberUpdatedState so the remembered PostCallbacks below
    // captures the live reference instead of a stale snapshot.
    val currentOnReplyClick by rememberUpdatedState(onReplyClick)
    val currentOnQuoteClick by rememberUpdatedState(onQuoteClick)

    val haptics = rememberPostHaptics()
    val callbacks =
        remember(viewModel, haptics, context) {
            PostCallbacks(
                onTap = { viewModel.handleEvent(PostDetailEvent.OnPostTapped(it.id)) },
                onAuthorTap = { viewModel.handleEvent(PostDetailEvent.OnAuthorTapped(it.did)) },
                onLike = { post ->
                    if (post.viewer.isLikedByViewer) haptics.likeOff() else haptics.likeOn()
                    viewModel.handleEvent(PostDetailEvent.OnLikeClicked(post))
                },
                onRepost = { post ->
                    if (post.viewer.isRepostedByViewer) haptics.repostOff() else haptics.repostOn()
                    viewModel.handleEvent(PostDetailEvent.OnRepostClicked(post))
                },
                onReply = { post ->
                    haptics.lightTap()
                    currentOnReplyClick(post.id)
                },
                onQuote = { post ->
                    haptics.lightTap()
                    currentOnQuoteClick(post.id)
                },
                onShare = { post ->
                    haptics.lightTap()
                    viewModel.handleEvent(PostDetailEvent.OnShareClicked(post))
                },
                // Long-press already fires the system long-press haptic via
                // combinedClickable — don't double-tap the motor.
                onShareLongPress = { viewModel.handleEvent(PostDetailEvent.OnShareLongPressed(it)) },
                onExternalEmbedTap = { uri ->
                    // Opening a URL is a stateless platform action, so do it
                    // inline (no VM round-trip) exactly like FeedScreen. Narrowed
                    // catch: silent no-op only for the "no CCT-capable browser
                    // installed" case; other launch failures propagate so genuine
                    // bugs surface in logcat instead of a blanket catch.
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
                    viewModel.handleEvent(PostDetailEvent.OnQuotedPostTapped(quoted.uri))
                },
                onOverflowAction = { post, action ->
                    viewModel.handleEvent(PostDetailEvent.OnOverflowAction(post, action))
                },
            )
        }

    val onRetry = remember(viewModel) { { viewModel.handleEvent(PostDetailEvent.Retry) } }
    val onRefresh = remember(viewModel) { { viewModel.handleEvent(PostDetailEvent.Refresh) } }
    val onReply =
        remember(viewModel, haptics) {
            {
                haptics.lightTap()
                viewModel.handleEvent(PostDetailEvent.OnReplyClicked)
            }
        }
    val onFocusImageClick =
        remember(viewModel) {
            { index: Int -> viewModel.handleEvent(PostDetailEvent.OnFocusImageClicked(imageIndex = index)) }
        }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToAuthor by rememberUpdatedState(onNavigateToAuthor)
    val currentOnNavigateToMediaViewer by rememberUpdatedState(onNavigateToMediaViewer)
    val currentOnNavigateToVideoPlayer by rememberUpdatedState(onNavigateToVideoPlayer)
    val currentOnNavigateTo by rememberUpdatedState(onNavigateTo)

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
    val linkCopiedMessage = stringResource(R.string.postdetail_snackbar_link_copied)
    val clipLabel = stringResource(R.string.postdetail_clipboard_label_post_link)
    // Pre-resolve PostCard overflow-menu coming-soon snackbars at composition time.
    val overflowReportComingSoon =
        stringResource(R.string.postdetail_snackbar_overflow_report_coming_soon)
    val overflowMuteComingSoon =
        stringResource(R.string.postdetail_snackbar_overflow_mute_coming_soon)
    val overflowUnmuteComingSoon =
        stringResource(R.string.postdetail_snackbar_overflow_unmute_coming_soon)
    val overflowBlockComingSoon =
        stringResource(R.string.postdetail_snackbar_overflow_block_coming_soon)
    val overflowUnblockComingSoon =
        stringResource(R.string.postdetail_snackbar_overflow_unblock_coming_soon)
    val overflowMuteThreadComingSoon =
        stringResource(R.string.postdetail_snackbar_overflow_mute_thread_coming_soon)
    val overflowUnmuteThreadComingSoon =
        stringResource(R.string.postdetail_snackbar_overflow_unmute_thread_coming_soon)
    val overflowCopyTextComingSoon =
        stringResource(R.string.postdetail_snackbar_overflow_copy_text_coming_soon)
    val clipboardManager =
        remember(context) {
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }

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
                    // Reject haptic — primarily a toggle-rejected cue (like /
                    // repost failed). Over-fires on the refresh-failure path
                    // too; acceptable since a buzz on any failed user-visible
                    // action isn't misleading.
                    haptics.rejected()
                    // Replace, don't stack — successive errors during a flapping
                    // network spell would otherwise queue snackbars indefinitely.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }
                is PostDetailEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is PostDetailEffect.NavigateToAuthor -> currentOnNavigateToAuthor(effect.authorDid)
                is PostDetailEffect.NavigateToComposer ->
                    currentOnReplyClick(effect.parentPostUri)
                is PostDetailEffect.NavigateToMediaViewer ->
                    currentOnNavigateToMediaViewer(effect.postUri, effect.imageIndex)
                is PostDetailEffect.NavigateToVideoPlayer ->
                    currentOnNavigateToVideoPlayer(effect.postUri)
                is PostDetailEffect.ShowComingSoon -> {
                    val message =
                        when (effect.action) {
                            // ReportPost graduated out of the coming-soon stub in
                            // oftc.3.1; it now flows through NavigateTo(Report(...)).
                            // The VM never emits ShowComingSoon for ReportPost, so
                            // this branch should be unreachable — but exhaustive
                            // `when` over a sealed enum needs the case. Surface the
                            // generic copy as a defensive fallback rather than
                            // crashing if something dispatches ReportPost here.
                            PostOverflowAction.ReportPost -> overflowReportComingSoon
                            PostOverflowAction.MuteAuthor -> overflowMuteComingSoon
                            PostOverflowAction.UnmuteAuthor -> overflowUnmuteComingSoon
                            PostOverflowAction.BlockAuthor -> overflowBlockComingSoon
                            PostOverflowAction.UnblockAuthor -> overflowUnblockComingSoon
                            PostOverflowAction.MuteThread -> overflowMuteThreadComingSoon
                            PostOverflowAction.UnmuteThread -> overflowUnmuteThreadComingSoon
                            PostOverflowAction.CopyPostText -> overflowCopyTextComingSoon
                        }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }
                is PostDetailEffect.SharePost -> context.launchPostShare(effect.intent)
                is PostDetailEffect.CopyPermalink -> {
                    clipboardManager.setPrimaryClip(
                        ClipData.newPlainText(clipLabel, effect.permalink),
                    )
                    // Replace any pending snackbar — fresh "link copied"
                    // confirmation outranks a stale error message.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = linkCopiedMessage)
                }
                is PostDetailEffect.NavigateTo -> currentOnNavigateTo(effect.key)
            }
        }
    }

    val onVideoTap =
        remember(viewModel) {
            { uri: String -> viewModel.handleEvent(PostDetailEvent.OnVideoTapped(uri)) }
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
        onVideoTap = onVideoTap,
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
    onVideoTap: (postUri: String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.postdetail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.postdetail_back_content_description),
                            filled = true,
                            modifier = Modifier.mirror(),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // Design Decision 3 says "always visible, no hide-on-scroll",
            // which refers to scroll behavior. `showReplyFab` keeps the FAB
            // hidden through the default-Idle pre-load frame (a fresh
            // `PostDetailState()` is `Idle` with empty items, no focus) AND on
            // reply-gated threads where the focus post's threadgate disallows
            // this viewer (`canViewerReply == false`) — showing a reply
            // affordance the user can't act on would just dump them into a
            // composer that rejects the reply. Once an allowed Focus is in
            // items, the FAB stays visible across Idle ↔ Refreshing (refresh in
            // progress doesn't hide it).
            //
            // Standard FloatingActionButton (M3 baseline elevation, circle
            // shape, primaryContainer tint); the catalog's material3
            // 1.5.0-alpha18 ships M3 Expressive size variants but the design
            // intent is "Threads-style without the Threads-shape" — the
            // standard FAB nails the vocabulary without reaching for an
            // Expressive size we'd then have to tune for bottom-padding
            // clearance.
            if (state.showReplyFab) {
                FloatingActionButton(onClick = onReply) {
                    NubecitaIcon(
                        name = NubecitaIconName.Reply,
                        contentDescription = stringResource(R.string.postdetail_reply_fab_content_description),
                        filled = false,
                        modifier = Modifier.mirror(),
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
                    onVideoTap = onVideoTap,
                    contentPadding = padding,
                    lastLikeTapPostUri = state.lastLikeTapPostUri,
                    lastRepostTapPostUri = state.lastRepostTapPostUri,
                )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadedThread(
    items: ImmutableList<ThreadItem>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    callbacks: PostCallbacks,
    onFocusImageClick: (Int) -> Unit,
    onVideoTap: (postUri: String) -> Unit,
    contentPadding: PaddingValues,
    lastLikeTapPostUri: String? = null,
    lastRepostTapPostUri: String? = null,
) {
    // Per-thread reveal state for covered (NSFW-labelled) media. Post-detail
    // covers (never drops) warned media, so every ancestor / focus / reply
    // PostCard can be revealed independently. Same @Stable PersistentSet +
    // listSaver shape as the feed.
    var revealedMedia by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toPersistentSet() }),
    ) { mutableStateOf(persistentSetOf<String>()) }
    // Bottom contentPadding clearance for the FAB so the bottom-most reply
    // can scroll fully above the floating composer affordance at end-of-
    // thread scroll position. Per design.md Decision 3 occlusion safeguard
    // (~80–100dp combined): 56dp standard FAB + 16dp Material edge spacing
    // + 16dp safety margin = 88dp.
    //
    // Preserves Scaffold's start/end insets (landscape system bars,
    // adaptive split-pane horizontal safe-area padding) instead of
    // hardcoding them to 0.dp — without this the thread content would
    // render under those insets on layouts that supply non-zero
    // horizontal padding. Resolution requires the LayoutDirection so RTL
    // locales pick up the right side of the inset pair.
    val layoutDirection = LocalLayoutDirection.current
    val mergedContentPadding =
        remember(contentPadding, layoutDirection) {
            PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + FAB_BOTTOM_CLEARANCE,
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
            )
        }
    NubecitaPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        // Offset the indicator below the thread's TopAppBar. (nubecita-tfbc)
        indicatorPadding = contentPadding,
    ) {
        LazyColumn(
            // Stable tag (surfaced as a bare resource-id via testTagsAsResourceId)
            // so the marketing screenshot journey can await the loaded thread.
            modifier = Modifier.fillMaxSize().testTag(POST_DETAIL_LIST_TEST_TAG),
            contentPadding = mergedContentPadding,
        ) {
            items(items = items, key = { it.key }) { item ->
                when (item) {
                    is ThreadItem.Ancestor -> {
                        val (videoSlot, quotedVideoSlot) =
                            rememberThreadPostVideoSlots(item.post, onVideoTap)
                        PostCard(
                            post = item.post,
                            callbacks = callbacks,
                            videoEmbedSlot = videoSlot,
                            quotedVideoEmbedSlot = quotedVideoSlot,
                            animateLikeTap = item.post.id == lastLikeTapPostUri,
                            animateRepostTap = item.post.id == lastRepostTapPostUri,
                            isMediaRevealed = item.post.id in revealedMedia,
                            onRevealMedia = { revealedMedia = revealedMedia.add(item.post.id) },
                        )
                    }
                    is ThreadItem.Focus -> {
                        val (videoSlot, quotedVideoSlot) =
                            rememberThreadPostVideoSlots(item.post, onVideoTap)
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
                            // the per-image-index callback. Video taps DO
                            // route on every PostCard in the thread — there's
                            // no fullscreen-viewer detour to skip the way
                            // images go through PostDetail.
                            PostCard(
                                post = item.post,
                                callbacks = callbacks,
                                onImageClick = onFocusImageClick,
                                videoEmbedSlot = videoSlot,
                                quotedVideoEmbedSlot = quotedVideoSlot,
                                animateLikeTap = item.post.id == lastLikeTapPostUri,
                                animateRepostTap = item.post.id == lastRepostTapPostUri,
                                isMediaRevealed = item.post.id in revealedMedia,
                                onRevealMedia = { revealedMedia = revealedMedia.add(item.post.id) },
                            )
                        }
                    }
                    is ThreadItem.Reply -> {
                        val (videoSlot, quotedVideoSlot) =
                            rememberThreadPostVideoSlots(item.post, onVideoTap)
                        PostCard(
                            post = item.post,
                            callbacks = callbacks,
                            videoEmbedSlot = videoSlot,
                            quotedVideoEmbedSlot = quotedVideoSlot,
                            animateLikeTap = item.post.id == lastLikeTapPostUri,
                            animateRepostTap = item.post.id == lastRepostTapPostUri,
                            isMediaRevealed = item.post.id in revealedMedia,
                            onRevealMedia = { revealedMedia = revealedMedia.add(item.post.id) },
                        )
                    }
                    is ThreadItem.Blocked ->
                        // m31.6 (oftc.6) — proper tombstone visual replaces the
                        // earlier inline "Post unavailable" Text. The Unblock
                        // CTA is intentionally null here until oftc.4 lands the
                        // real unblock RPC + optimistic state — wiring an empty
                        // onClick lambda would mislead users about what tapping
                        // does (silently nothing). NotFound has no recovery
                        // path, so [NotFoundPostCard] never carries a CTA.
                        BlockedPostCard(onUnblock = null)
                    is ThreadItem.NotFound -> NotFoundPostCard()
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

/**
 * Builds the parent + quoted `VideoPosterEmbed` slot lambdas for one
 * thread PostCard, hoisting them inside a `remember` keyed on the
 * post URI + tap callback so the per-item closures stay stable across
 * recompositions (PostCallbacks-style stability — without it the slot
 * lambdas would re-allocate every recomposition and defeat PostCard's
 * skip optimizations). Quoted slot is `null` when the post's embed
 * doesn't carry a quoted record, so `EmbedSlot` falls through to the
 * outer-card tap as the KDoc contract requires.
 */
@Composable
private fun rememberThreadPostVideoSlots(
    post: PostUi,
    onVideoTap: (postUri: String) -> Unit,
): Pair<@Composable (EmbedUi.Video, MediaCover?) -> Unit, (@Composable (QuotedEmbedUi.Video) -> Unit)?> {
    val parentUri = post.id
    val videoSlot: @Composable (EmbedUi.Video, MediaCover?) -> Unit =
        remember(parentUri, onVideoTap) {
            val tap = { onVideoTap(parentUri) }
            val slot: @Composable (EmbedUi.Video, MediaCover?) -> Unit = { video, cover ->
                VideoPosterEmbed(
                    posterUrl = video.posterUrl,
                    aspectRatio = video.aspectRatio,
                    altText = video.altText,
                    onTap = tap,
                    cover = cover,
                )
            }
            slot
        }
    val quotedUri = post.embed.quotedRecord?.uri
    val quotedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? =
        remember(quotedUri, onVideoTap) {
            if (quotedUri == null) {
                null
            } else {
                val tap = { onVideoTap(quotedUri) }
                val slot: @Composable (QuotedEmbedUi.Video) -> Unit = { qVideo ->
                    VideoPosterEmbed(
                        posterUrl = qVideo.posterUrl,
                        aspectRatio = qVideo.aspectRatio,
                        altText = qVideo.altText,
                        onTap = tap,
                    )
                }
                slot
            }
        }
    return videoSlot to quotedSlot
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
 * Stable `testTag` on the loaded thread's `LazyColumn`. Surfaced as a bare
 * resource-id via `testTagsAsResourceId` so UiAutomator (the marketing
 * screenshot journey) can await a fully-loaded post-detail before capturing.
 */
internal const val POST_DETAIL_LIST_TEST_TAG: String = "post_detail_list"

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
        ThreadItem.Blocked(uri = "at://did:plc:blocked/app.bsky.feed.post/blocked", authorDid = "did:plc:blocked"),
        ThreadItem.Reply(post = previewPost("reply-1", text = "Top-level reply — direct child of the focus."), depth = 1),
        ThreadItem.Reply(post = previewPost("reply-2", text = "Another top-level reply — sibling of reply-1."), depth = 1),
    )
