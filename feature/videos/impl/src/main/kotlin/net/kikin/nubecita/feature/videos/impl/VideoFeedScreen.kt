@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.feature.videos.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPresentationState
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.core.common.haptic.rememberPostHaptics
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.feature.videos.impl.ui.VideoFeedPage
import net.kikin.nubecita.feature.videos.impl.ui.VideoPageChrome
import net.kikin.nubecita.feature.videos.impl.ui.posterAlphaTarget
import net.kikin.nubecita.feature.videos.impl.ui.rememberVideoFeedInteractions
import net.kikin.nubecita.feature.videos.impl.ui.surfaceTranslationPx

/**
 * Full-screen vertical video feed. A snapping [VerticalPager] whose settled page
 * drives the pooled player via the ViewModel; a single persistent `PlayerSurface`
 * sits behind the pager and re-binds to whichever pooled player is active, so it
 * is never recreated across swipes. Each page renders a poster OVER that surface
 * — an overlay, not an underlay — that fades out once its player has a decoded
 * frame. Overlay chrome (author, caption, action rail, mute) composes into each
 * page above the poster; its interactions are delegated to the shared
 * PostInteractionHandler. Tap gestures and playback progress land in PR3.
 */
@Composable
internal fun VideoFeedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activePlayer by viewModel.activePlayer.collectAsStateWithLifecycle()

    // Drive the pool's decoder handoff off the surface lifecycle (release on background).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onStart()
                    Lifecycle.Event.ON_STOP -> viewModel.onStop()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberPostHaptics()
    val callbacks = rememberVideoFeedInteractions(viewModel, snackbarHostState)
    // The nav state holder is a CompositionLocal, which a ViewModel cannot reach —
    // hence navigation arrives as an effect and the screen performs the push.
    val navState by rememberUpdatedState(LocalMainShellNavState.current)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is VideoFeedEffect.NavigateTo -> navState.add(effect.target)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (val status = state.status) {
            VideoFeedStatus.Loading ->
                Box(contentModifier, contentAlignment = Alignment.Center) { NubecitaWavyProgressIndicator() }

            VideoFeedStatus.Error ->
                Box(contentModifier, contentAlignment = Alignment.Center) {
                    Button(onClick = { viewModel.handleEvent(VideoFeedEvent.Retry) }) {
                        Text(stringResource(R.string.videos_retry))
                    }
                }

            is VideoFeedStatus.Content -> {
                // Open at the VM's initial active index (route.startIndex, e.g. from the
                // Trending carousel); rememberPagerState only reads initialPage on first use.
                val pagerState = rememberPagerState(initialPage = state.activeIndex, pageCount = { status.items.size })
                LaunchedEffect(pagerState) {
                    snapshotFlowSettledPage(pagerState) { settled ->
                        viewModel.handleEvent(VideoFeedEvent.ActiveIndexChanged(settled))
                    }
                }
                // Full-bleed reference for the drag translation below. The surface
                // itself is letterboxed by `.aspectRatio(settledAspectRatio)`, so its
                // own `size.height` inside graphicsLayer is SMALLER than the distance
                // the pager actually scrolls (e.g. a 9:16 video on a taller screen) —
                // using it would scale the translation down and detach the video from
                // the finger mid-drag. This outer Box is unclipped and always matches
                // the pager's true page height, so read from here instead. `by` +
                // mutableIntStateOf is a deferred state read inside graphicsLayer's
                // block, not a composition-scope val, so a swipe still costs zero
                // recompositions.
                var pageHeightPx by remember { mutableIntStateOf(0) }
                Box(contentModifier.onSizeChanged { pageHeightPx = it.height }) {
                    // ONE persistent video surface for the whole feed, sitting behind the
                    // pager. Because it is never recreated as the active page changes,
                    // promoting a pooled player only re-binds it — there is no async
                    // surface-attach race and no black first frame. The pool guarantees
                    // exactly one active player, so a single surface is all the feed needs.
                    val settledItem = status.items.getOrNull(pagerState.settledPage)
                    // rememberPresentationState accepts a nullable player, so it is called
                    // unconditionally rather than inside activePlayer?.let — the surface's
                    // presentation state must stay observable even in the brief window where
                    // the pool has no active player.
                    // key(activePlayer) is required: media3 1.10.1's rememberPresentationState
                    // is `remember { PresentationState(...) }`, UNKEYED, so without this the
                    // SAME instance — and its `coverSurface` value — survives a pool
                    // promotion. maybeHideSurface only sets coverSurface = true when the
                    // player has no tracks, or has tracks but no selected video track; a
                    // prewarmed player already has both, so neither branch fires and
                    // coverSurface stays false, carried over from the outgoing clip — the
                    // incoming page's poster reads as transparent while the surface still
                    // shows the outgoing clip's last frame. key(activePlayer) forces a fresh
                    // instance on every promotion, which starts at coverSurface = true and
                    // only clears on EVENT_RENDERED_FIRST_FRAME from the newly attached
                    // surface — exactly the crossfade signal.
                    val presentationState = key(activePlayer) { rememberPresentationState(activePlayer) }
                    val videoSize = presentationState.videoSizeDp
                    // Poster and surface MUST resolve to the same ratio or the crossfade
                    // reads as a jump. Prefer the decoded size once known; fall back to the
                    // embed's declared ratio, which is available before any decode (D4).
                    val settledAspectRatio =
                        if (videoSize != null && videoSize.width > 0f && videoSize.height > 0f) {
                            videoSize.width / videoSize.height
                        } else {
                            settledItem?.aspectRatio ?: DEFAULT_VIDEO_ASPECT_RATIO
                        }

                    activePlayer?.let { player ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PlayerSurface(
                                player = player,
                                surfaceType = FEED_SURFACE_TYPE,
                                modifier =
                                    Modifier
                                        .aspectRatio(settledAspectRatio)
                                        // Deferred read: a swipe re-runs only this layer
                                        // block, never composition or layout. That is what
                                        // holds the gesture at 120hz.
                                        .graphicsLayer {
                                            translationY =
                                                surfaceTranslationPx(
                                                    currentPage = pagerState.currentPage,
                                                    currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                                                    settledPage = pagerState.settledPage,
                                                    pageHeightPx = pageHeightPx.toFloat(),
                                                )
                                        },
                            )
                        }
                    }
                    // The pager is a transparent gesture + snapping layer on top. Its pages
                    // carry the poster (and, from PR2, the chrome), so the surface behind
                    // shows through wherever the poster has faded out. A stable per-item key
                    // keeps page state aligned as the feed paginates (appends).
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().testTag(VideoFeedTestTags.PAGER),
                        key = { index -> status.items[index].post.id },
                    ) { page ->
                        val item = status.items[page]
                        val isSettled = page == pagerState.settledPage
                        val targetAlpha =
                            posterAlphaTarget(
                                isSettledPage = isSettled,
                                coverSurface = presentationState.coverSurface,
                            )
                        // Keep this a State, not a `by`-unwrapped Float: unwrapping at
                        // composition scope would invalidate this page's composition (and its
                        // image subtree) on every animation frame. VideoFeedPage reads the
                        // value inside its graphicsLayer block instead, so a running crossfade
                        // costs zero recomposition.
                        val posterAlphaState =
                            animateFloatAsState(
                                targetValue = targetAlpha,
                                // MotionScheme, not a raw tween: defaultEffectsSpec()
                                // collapses to a short linear tween under reduce-motion — a
                                // hand-rolled tween() would silently ignore that preference.
                                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                label = "VideoFeedPoster-alpha",
                            )
                        // Caption expansion is per-page Compose state, not VM state: it is a
                        // presentation detail with no bearing on playback, and keying it on the
                        // post id keeps it correct as the pager recycles pages.
                        var captionExpanded by rememberSaveable(item.post.id) { mutableStateOf(false) }
                        VideoFeedPage(
                            posterUrl = item.posterUrl,
                            aspectRatio = if (isSettled) settledAspectRatio else item.aspectRatio,
                            posterAlpha = { posterAlphaState.value },
                            // isPaused is screen-level, so gate on isSettled or every
                            // composed neighbour would render the glyph too.
                            isPaused = state.isPaused && isSettled,
                            onTogglePlayPause = { viewModel.handleEvent(VideoFeedEvent.TogglePlayPause) },
                            onDoubleTapLike = {
                                if (!item.post.viewer.isLikedByViewer) haptics.likeOn()
                                viewModel.handleEvent(VideoFeedEvent.DoubleTapLike(item.post))
                            },
                        ) {
                            VideoPageChrome(
                                post = item.post,
                                isMuted = state.isMuted,
                                captionExpanded = captionExpanded,
                                onCaptionToggle = { captionExpanded = !captionExpanded },
                                onAuthorTap = { viewModel.handleEvent(VideoFeedEvent.AuthorTapped(item.post)) },
                                // Delegation-forwarded: the handler owns the optimistic write.
                                onLike = {
                                    if (item.post.viewer.isLikedByViewer) haptics.likeOff() else haptics.likeOn()
                                    viewModel.onLike(item.post)
                                },
                                onRepost = {
                                    if (item.post.viewer.isRepostedByViewer) haptics.repostOff() else haptics.repostOn()
                                    viewModel.onRepost(item.post)
                                },
                                // Routed through the helper's callbacks so its composer-navigation
                                // and share-sheet effects fire.
                                onReply = { callbacks.onReply(item.post) },
                                onShare = { callbacks.onShare(item.post) },
                                onBookmark = {
                                    if (item.post.viewer.isBookmarked) haptics.bookmarkOff() else haptics.bookmarkOn()
                                    callbacks.onBookmark(item.post)
                                },
                                onOverflowAction = { action -> callbacks.onOverflowAction?.invoke(item.post, action) },
                                onMuteToggle = { viewModel.handleEvent(VideoFeedEvent.ToggleMute) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun snapshotFlowSettledPage(
    pagerState: androidx.compose.foundation.pager.PagerState,
    onSettled: (Int) -> Unit,
) {
    androidx.compose.runtime
        .snapshotFlow { pagerState.settledPage }
        .distinctUntilChanged()
        .collect(onSettled)
}

/**
 * Surface backing for the feed's video.
 *
 * `TextureView` composites through the view hierarchy, so it translates and
 * alpha-blends exactly in step with the pager — a `SurfaceView`'s position is
 * owned by the window compositor and can visibly lag the app frame during a
 * drag. The cost is real: full-screen video goes through the GPU every frame
 * instead of a hardware overlay, which is a battery cost on the surface users
 * linger on longest. Deliberately isolated here so a battery pass can flip it
 * back to SURFACE_TYPE_SURFACE_VIEW as a one-line change — but flipping back is
 * not cost-free: it trades the slide-tracking behavior above away, since the
 * window compositor (not this composable) would then own the surface's
 * position during a drag. See design D1.
 */
private const val FEED_SURFACE_TYPE = SURFACE_TYPE_TEXTURE_VIEW
