@file:OptIn(ExperimentalMaterial3Api::class)

package net.kikin.nubecita.feature.mediaviewer.impl

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import kotlin.time.Duration.Companion.seconds

private const val TAG = "MediaViewerScreen"

/**
 * Auto-fade delay for the chrome overlay. Resets on every page change
 * and on every chrome-show transition driven by [MediaViewerEvent.OnTapImage].
 */
private val CHROME_AUTO_FADE_DELAY = 3.seconds

/**
 * Screen-level entry composable for the fullscreen image viewer.
 *
 * - [onDismiss] is the only nav side-effect — invoked when the
 *   ViewModel emits [MediaViewerEffect.Dismiss]. The screen does NOT
 *   import `LocalMainShellNavState`; the entry-provider call site in
 *   `MediaViewerNavigationModule` wires `onDismiss` to
 *   `LocalMainShellNavState.current.removeLast()`.
 * - The `BackHandler` collects the system back press and dispatches
 *   `OnDismissRequest` (or `OnAltSheetDismiss` if the sheet is open).
 */
@Composable
internal fun MediaViewerScreen(
    onDismiss: () -> Unit,
    viewModel: MediaViewerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(Unit) { viewModel.handleEvent(MediaViewerEvent.Load) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MediaViewerEffect.Dismiss -> currentOnDismiss()
                is MediaViewerEffect.ShowError -> {
                    // No persistent surface for snackbars on the black viewer
                    // canvas (and v1 has no non-sticky errors that aren't
                    // already routed through state.Error). Reserved for future
                    // share/save action failures.
                }
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        when (val status = state.loadStatus) {
            MediaViewerLoadStatus.Loading -> LoadingState()
            is MediaViewerLoadStatus.Error ->
                ErrorState(
                    error = status.error,
                    onRetry = { viewModel.handleEvent(MediaViewerEvent.Retry) },
                    onDismiss = { viewModel.handleEvent(MediaViewerEvent.OnDismissRequest) },
                )
            is MediaViewerLoadStatus.Loaded ->
                LoadedState(
                    status = status,
                    onPageChange = { viewModel.handleEvent(MediaViewerEvent.OnPageChanged(it)) },
                    onTapImage = { viewModel.handleEvent(MediaViewerEvent.OnTapImage) },
                    onAltBadgeClick = { viewModel.handleEvent(MediaViewerEvent.OnAltBadgeClick) },
                    onAltSheetDismiss = { viewModel.handleEvent(MediaViewerEvent.OnAltSheetDismiss) },
                    onChromeAutoFadeTimeout = {
                        viewModel.handleEvent(MediaViewerEvent.OnChromeAutoFadeTimeout)
                    },
                    onDismissRequest = { viewModel.handleEvent(MediaViewerEvent.OnDismissRequest) },
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
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.mediaviewer_close_content_description),
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
                text = error.toMessage(LocalContext.current),
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

    // Sync pager → state. distinctUntilChanged avoids the loop with
    // OnPageChanged → setState → recomposition → snapshotFlow re-emit.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .filter { it != status.currentIndex }
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

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val image = status.images[page]
            ZoomableAsyncImage(
                model = image.fullsizeUrl(),
                contentDescription = image.altText,
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
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.mediaviewer_close_content_description),
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
                Surface(
                    onClick = onAltBadgeClick,
                    modifier = Modifier.align(Alignment.CenterEnd),
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
private fun MediaViewerError.toMessage(context: android.content.Context): String =
    when (this) {
        MediaViewerError.Network -> context.getString(R.string.mediaviewer_error_network)
        MediaViewerError.Unauthenticated -> context.getString(R.string.mediaviewer_error_unauthenticated)
        MediaViewerError.NotFound -> context.getString(R.string.mediaviewer_error_not_found)
        MediaViewerError.NoImages -> context.getString(R.string.mediaviewer_error_no_images)
        is MediaViewerError.Unknown -> context.getString(R.string.mediaviewer_error_unknown)
    }
