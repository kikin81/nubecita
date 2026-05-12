@file:OptIn(ExperimentalMaterial3Api::class)

package net.kikin.nubecita.feature.mediaviewer.impl

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import kotlin.time.Duration.Companion.seconds

/**
 * Auto-fade delay for the chrome overlay. Resets on every page change
 * and on every chrome-show transition driven by [MediaViewerEvent.OnTapImage].
 */
private val CHROME_AUTO_FADE_DELAY = 3.seconds

/**
 * Vertical drag distance (px) past which a swipe-down at min-zoom
 * dispatches [MediaViewerEvent.OnDismissRequest]. Resolved from a dp
 * value at the call site so it scales with display density.
 */
private const val SWIPE_DOWN_DISMISS_THRESHOLD_DP: Int = 120

/**
 * Tolerance for "is the active page at min-zoom?". `zoomFraction` is a
 * `Float` reported by telephoto that can hover near zero from anti-jitter
 * smoothing during a fling — anything below this counts as min-zoom for
 * the paging-enabled / swipe-down-dismiss-enabled gates.
 */
private const val MIN_ZOOM_TOLERANCE: Float = 0.02f

/**
 * Screen-level entry composable for the fullscreen image viewer.
 *
 * - [onDismiss] is the only nav side-effect — invoked when the
 *   ViewModel emits [MediaViewerEffect.Dismiss]. The screen does NOT
 *   import any nav-state holder; the entry-provider call site in
 *   `MediaViewerNavigationModule` wires `onDismiss` to
 *   `LocalAppNavigator.current.goBack()` on the outer `Navigator`.
 * - The `BackHandler` collects the system back press and dispatches
 *   `OnDismissRequest` (or `OnAltSheetDismiss` if the sheet is open).
 *
 * Event-dispatch lambdas are hoisted via `remember(viewModel) { … }`
 * so children (and especially the active page's `ZoomableAsyncImage`)
 * see stable references and don't recompose during pinch/zoom from
 * lambda-identity churn alone.
 */
@Composable
internal fun MediaViewerScreen(
    onDismiss: () -> Unit,
    viewModel: MediaViewerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val onRetry = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.Retry) } }
    val onDismissRequest = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnDismissRequest) } }
    val onPageChange = remember(viewModel) { { i: Int -> viewModel.handleEvent(MediaViewerEvent.OnPageChanged(i)) } }
    val onTapImage = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnTapImage) } }
    val onAltBadgeClick = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnAltBadgeClick) } }
    val onAltSheetDismiss = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnAltSheetDismiss) } }
    val onChromeAutoFadeTimeout = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnChromeAutoFadeTimeout) } }

    LaunchedEffect(Unit) { viewModel.handleEvent(MediaViewerEvent.Load) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MediaViewerEffect.Dismiss -> currentOnDismiss()
            }
        }
    }

    BackHandler {
        val current = state.loadStatus
        if (current is MediaViewerLoadStatus.Loaded && current.isAltSheetOpen) {
            viewModel.handleEvent(MediaViewerEvent.OnAltSheetDismiss)
        } else {
            viewModel.handleEvent(MediaViewerEvent.OnDismissRequest)
        }
    }

    MediaViewerScreenContent(
        state = state,
        onRetry = onRetry,
        onDismissRequest = onDismissRequest,
        onPageChange = onPageChange,
        onTapImage = onTapImage,
        onAltBadgeClick = onAltBadgeClick,
        onAltSheetDismiss = onAltSheetDismiss,
        onChromeAutoFadeTimeout = onChromeAutoFadeTimeout,
        modifier = modifier,
    )
}

/**
 * Stateless content composable. Previews and screenshot tests call this
 * directly with fixture state — no ViewModel, no Hilt graph, no live
 * `BackHandler`. Mirrors the `PostDetailScreenContent` split.
 */
@Composable
internal fun MediaViewerScreenContent(
    state: MediaViewerState,
    onRetry: () -> Unit,
    onDismissRequest: () -> Unit,
    onPageChange: (Int) -> Unit,
    onTapImage: () -> Unit,
    onAltBadgeClick: () -> Unit,
    onAltSheetDismiss: () -> Unit,
    onChromeAutoFadeTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        when (val status = state.loadStatus) {
            MediaViewerLoadStatus.Loading -> LoadingState()
            is MediaViewerLoadStatus.Error ->
                ErrorState(
                    error = status.error,
                    onRetry = onRetry,
                    onDismiss = onDismissRequest,
                )
            is MediaViewerLoadStatus.Loaded ->
                LoadedState(
                    status = status,
                    onPageChange = onPageChange,
                    onTapImage = onTapImage,
                    onAltBadgeClick = onAltBadgeClick,
                    onAltSheetDismiss = onAltSheetDismiss,
                    onChromeAutoFadeTimeout = onChromeAutoFadeTimeout,
                    onDismissRequest = onDismissRequest,
                )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun ErrorState(
    error: MediaViewerError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        // Close affordance stays visible on error so the user can always
        // exit even if retry keeps failing.
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
        ) {
            NubecitaIcon(
                name = NubecitaIconName.Close,
                contentDescription = stringResource(R.string.mediaviewer_close_content_description),
                filled = false,
                tint = Color.White,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = error.localizedMessage(),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            // Retry is hidden for NoImages because re-fetching can't change
            // the embed type — the user has to dismiss and re-enter from a
            // different surface.
            if (error !is MediaViewerError.NoImages) {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.mediaviewer_retry))
                }
            }
        }
    }
}

@Composable
private fun LoadedState(
    status: MediaViewerLoadStatus.Loaded,
    onPageChange: (Int) -> Unit,
    onTapImage: () -> Unit,
    onAltBadgeClick: () -> Unit,
    onAltSheetDismiss: () -> Unit,
    onChromeAutoFadeTimeout: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState =
        rememberPagerState(
            initialPage = status.currentIndex,
            pageCount = { status.images.size },
        )
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    val currentOnChromeAutoFadeTimeout by rememberUpdatedState(onChromeAutoFadeTimeout)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    // Active page's zoom factor, written by the active slot's
    // snapshotFlow collector below. The pager's `userScrollEnabled` and
    // the swipe-down-dismiss draggable both read this to gate gestures —
    // paging only at min-zoom, dismiss only at min-zoom. Above min-zoom,
    // telephoto's pan/zoom absorbs all gestures on the active page.
    var activePageZoomFraction by remember { mutableFloatStateOf(0f) }
    val isAtMinZoom = activePageZoomFraction <= MIN_ZOOM_TOLERANCE

    val swipeDownThresholdPx = with(LocalDensity.current) { SWIPE_DOWN_DISMISS_THRESHOLD_DP.dp.toPx() }
    var dismissDragOffset by remember { mutableFloatStateOf(0f) }
    val dismissDraggableState =
        rememberDraggableState { delta ->
            // Only accumulate downward motion; an upward drag at min-zoom
            // does nothing (no gesture is bound to it). Negative delta
            // (finger moved up) resets the accumulator so half-down /
            // half-up wobbles don't latch into a dismiss.
            dismissDragOffset = (dismissDragOffset + delta).coerceAtLeast(0f)
        }

    // Sync pager → state. distinctUntilChanged absorbs the round-trip
    // (OnPageChanged → setState → recomposition → snapshotFlow re-emit).
    // No `.filter { it != status.currentIndex }` guard — the captured
    // status would go stale after the first emission, blocking subsequent
    // page changes; the VM's own `if (status.currentIndex == index) return`
    // guard in onPageChanged() makes the filter redundant anyway.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { currentOnPageChange(it) }
    }

    // Auto-fade timer. Restarts whenever the visibility flag flips to
    // true OR the page changes. The status fields are the trigger keys
    // so the LaunchedEffect coroutine cancels and restarts on every
    // relevant state change.
    LaunchedEffect(status.isChromeVisible, status.currentIndex) {
        if (status.isChromeVisible) {
            kotlinx.coroutines.delay(CHROME_AUTO_FADE_DELAY)
            currentOnChromeAutoFadeTimeout()
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Vertical-only draggable for swipe-down dismiss. Enabled
                // only at min-zoom so an above-min-zoom vertical motion is
                // absorbed by telephoto's pan instead. `Orientation.Vertical`
                // tells Compose's gesture system to leave horizontal drags
                // alone — they pass through to HorizontalPager.
                .draggable(
                    orientation = Orientation.Vertical,
                    state = dismissDraggableState,
                    enabled = isAtMinZoom,
                    onDragStarted = { dismissDragOffset = 0f },
                    onDragStopped = {
                        if (dismissDragOffset >= swipeDownThresholdPx) {
                            currentOnDismissRequest()
                        }
                        dismissDragOffset = 0f
                    },
                ),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Disable paging when the active page is zoomed — telephoto's
            // pan absorbs the horizontal gesture instead of advancing the
            // page. At min-zoom the user can swipe horizontally to page.
            userScrollEnabled = isAtMinZoom,
        ) { page ->
            val image = status.images[page]
            val zoomableState = rememberZoomableImageState(rememberZoomableState())
            // Only the active page reports its zoom factor up to LoadedState.
            // Off-screen pages also have their own state but their zoom is
            // unobserved (and resets on next composition anyway).
            val isActivePage = page == pagerState.currentPage
            LaunchedEffect(isActivePage, zoomableState) {
                if (isActivePage) {
                    // zoomFraction is nullable while the image content is
                    // still being measured (no intrinsic size yet). Treat
                    // null as min-zoom — the image isn't laid out so there's
                    // nothing to gate gestures on anyway.
                    snapshotFlow { zoomableState.zoomableState.zoomFraction ?: 0f }
                        .collect { activePageZoomFraction = it }
                }
            }
            ZoomableAsyncImage(
                // The viewer is full-screen — always pick the fullsize
                // variant. The thumb variant exists for n×n grids (Profile
                // Media tab) but would be visibly soft at this scale.
                model = image.fullsizeUrl,
                contentDescription = image.altText,
                state = zoomableState,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onClick = { onTapImage() },
            )
        }

        AnimatedVisibility(
            visible = status.isChromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        ) {
            ChromeBar(
                currentIndex = status.currentIndex,
                totalImages = status.images.size,
                hasAltText = !status.images[status.currentIndex].altText.isNullOrBlank(),
                onClose = onDismissRequest,
                onAltBadgeClick = onAltBadgeClick,
            )
        }

        if (status.isAltSheetOpen) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = onAltSheetDismiss,
                sheetState = sheetState,
            ) {
                Text(
                    text = status.images[status.currentIndex].altText.orEmpty(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun ChromeBar(
    currentIndex: Int,
    totalImages: Int,
    hasAltText: Boolean,
    onClose: () -> Unit,
    onAltBadgeClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        color = Color.Black.copy(alpha = 0.45f),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.Close,
                    contentDescription = stringResource(R.string.mediaviewer_close_content_description),
                    filled = false,
                    tint = Color.White,
                )
            }
            if (totalImages > 1) {
                Text(
                    text =
                        stringResource(
                            R.string.mediaviewer_page_indicator,
                            currentIndex + 1,
                            totalImages,
                        ),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (hasAltText) {
                val altBadgeContentDescription =
                    stringResource(R.string.mediaviewer_alt_badge_content_description)
                Surface(
                    onClick = onAltBadgeClick,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .semantics {
                                contentDescription = altBadgeContentDescription
                                role = Role.Button
                            },
                    color = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = stringResource(R.string.mediaviewer_alt_badge_label),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                // Reserve the space so the page indicator stays centered
                // regardless of whether the badge renders.
                Spacer(modifier = Modifier.size(48.dp).align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun MediaViewerError.localizedMessage(): String =
    when (this) {
        MediaViewerError.Network -> stringResource(R.string.mediaviewer_error_network)
        MediaViewerError.Unauthenticated -> stringResource(R.string.mediaviewer_error_unauthenticated)
        MediaViewerError.NotFound -> stringResource(R.string.mediaviewer_error_not_found)
        MediaViewerError.NoImages -> stringResource(R.string.mediaviewer_error_no_images)
        is MediaViewerError.Unknown -> stringResource(R.string.mediaviewer_error_unknown)
    }
