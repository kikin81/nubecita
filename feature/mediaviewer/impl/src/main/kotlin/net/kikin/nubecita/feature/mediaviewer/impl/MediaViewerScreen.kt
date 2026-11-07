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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import net.kikin.nubecita.core.common.navigation.LocalGalleryStateSync
import net.kikin.nubecita.core.common.navigation.SharedImageCorners
import net.kikin.nubecita.core.common.navigation.SharedImageSurfaces
import net.kikin.nubecita.core.common.navigation.postImageMemoryCacheKey
import net.kikin.nubecita.core.common.navigation.sharedImageModifier
import net.kikin.nubecita.core.postinteractions.ui.comments.PostCommentsEffect
import net.kikin.nubecita.core.postinteractions.ui.comments.PostCommentsEvent
import net.kikin.nubecita.core.postinteractions.ui.comments.PostCommentsSheetContent
import net.kikin.nubecita.core.postinteractions.ui.comments.PostCommentsViewModel
import net.kikin.nubecita.designsystem.adaptive.PostCommentsSheetScaffold
import net.kikin.nubecita.designsystem.component.NubecitaOverlayIconButton
import net.kikin.nubecita.designsystem.component.NubecitaOverlayPill
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import kotlin.time.Duration.Companion.seconds

private val CHROME_AUTO_FADE_DELAY = 5.seconds
private const val SWIPE_DOWN_DISMISS_THRESHOLD_DP: Int = 120
private const val MIN_ZOOM_TOLERANCE: Float = 0.02f

@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun MediaViewerScreen(
    onDismiss: () -> Unit,
    onOpenInMainShell: (NavKey) -> Unit,
    onNavigate: (NavKey) -> Unit,
    viewModel: MediaViewerViewModel,
    modifier: Modifier = Modifier,
    commentsViewModel: PostCommentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val commentsState by commentsViewModel.uiState.collectAsStateWithLifecycle()
    val isCommentsOpen = (state.loadStatus as? MediaViewerLoadStatus.Loaded)?.isCommentsOpen == true
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnOpenInMainShell by rememberUpdatedState(onOpenInMainShell)

    val onRetry = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.Retry) } }
    val onDismissRequest = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnDismissRequest) } }
    val onPageChange = remember(viewModel) { { i: Int -> viewModel.handleEvent(MediaViewerEvent.OnPageChanged(i)) } }
    val onTapImage = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnTapImage) } }
    val onAltBadgeClick = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnAltBadgeClick) } }
    val onAltSheetDismiss = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnAltSheetDismiss) } }
    val onChromeAutoFadeTimeout = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnChromeAutoFadeTimeout) } }
    val onSaveClick = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnSaveClick) } }
    val onAuthorClick = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnAuthorClick) } }
    val onCaptionClick = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnCaptionClick) } }
    val onCommentsClick = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnCommentsClick) } }
    val onCommentsDismiss = remember(viewModel) { { viewModel.handleEvent(MediaViewerEvent.OnCommentsDismiss) } }

    LaunchedEffect(Unit) { viewModel.handleEvent(MediaViewerEvent.Load) }

    val snackbarHostState = remember { SnackbarHostState() }
    val postCallbacks = rememberMediaViewerInteractions(viewModel, snackbarHostState, onNavigate)
    val savedMessage by rememberUpdatedState(stringResource(R.string.mediaviewer_save_success))
    val retrievalFailedMessage by rememberUpdatedState(stringResource(R.string.mediaviewer_save_failed_retrieval))
    val storageFailedMessage by rememberUpdatedState(stringResource(R.string.mediaviewer_save_failed_storage))

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MediaViewerEffect.Dismiss -> currentOnDismiss()
                is MediaViewerEffect.OpenInMainShell -> currentOnOpenInMainShell(effect.route)
                is MediaViewerEffect.ShowSaveOutcome ->
                    launch {
                        snackbarHostState.showSnackbar(
                            when (effect.outcome) {
                                MediaViewerSaveOutcome.Saved -> savedMessage
                                MediaViewerSaveOutcome.RetrievalFailed -> retrievalFailedMessage
                                MediaViewerSaveOutcome.StorageFailed -> storageFailedMessage
                            },
                        )
                    }
            }
        }
    }

    LaunchedEffect(isCommentsOpen) {
        commentsViewModel.handleEvent(
            if (isCommentsOpen) {
                PostCommentsEvent.OpenComments(viewModel.postUri)
            } else {
                PostCommentsEvent.DismissComments
            },
        )
    }

    LaunchedEffect(commentsViewModel) {
        commentsViewModel.effects.collect { effect ->
            when (effect) {
                is PostCommentsEffect.NavigateToComposer ->
                    currentOnNavigate(ComposerRoute(replyToUri = effect.parentPostUri))
                is PostCommentsEffect.ShowError -> launch { snackbarHostState.showSnackbar(effect.message) }
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

    PostCommentsSheetScaffold(
        isCommentsOpen = isCommentsOpen,
        onDismiss = onCommentsDismiss,
        commentsContent = {
            PostCommentsSheetContent(
                state = commentsState,
                onLikeClick = { comment -> commentsViewModel.handleEvent(PostCommentsEvent.LikeComment(comment.id)) },
                onRetryClick = { commentsViewModel.handleEvent(PostCommentsEvent.RetryLoad) },
                onRefresh = { commentsViewModel.handleEvent(PostCommentsEvent.Refresh) },
                onOpenComposer = { commentsViewModel.handleEvent(PostCommentsEvent.OpenComposer) },
                onAuthorClick = { author -> onOpenInMainShell(Profile(handle = author.did)) },
                onCommentClick = { comment -> onOpenInMainShell(PostDetailRoute(postUri = comment.id)) },
            )
        },
    ) {
        MediaViewerScreenContent(
            state = state,
            onRetry = onRetry,
            onDismissRequest = onDismissRequest,
            onPageChange = onPageChange,
            onTapImage = onTapImage,
            onAltBadgeClick = onAltBadgeClick,
            onAltSheetDismiss = onAltSheetDismiss,
            onChromeAutoFadeTimeout = onChromeAutoFadeTimeout,
            onSaveClick = onSaveClick,
            modifier = modifier,
            postUri = viewModel.postUri,
            contextKey = viewModel.contextKey,
            snackbarHostState = snackbarHostState,
            postCallbacks = postCallbacks,
            onAuthorClick = onAuthorClick,
            onCaptionClick = onCaptionClick,
            onCommentsClick = onCommentsClick,
        )
    }
}

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
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
    postUri: String = "",
    contextKey: String = "",
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    postCallbacks: PostCallbacks = PostCallbacks(),
    onAuthorClick: () -> Unit = {},
    onCaptionClick: () -> Unit = {},
    onCommentsClick: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        postUri = postUri,
                        contextKey = contextKey,
                        onPageChange = onPageChange,
                        onTapImage = onTapImage,
                        onAltBadgeClick = onAltBadgeClick,
                        onAltSheetDismiss = onAltSheetDismiss,
                        onChromeAutoFadeTimeout = onChromeAutoFadeTimeout,
                        onDismissRequest = onDismissRequest,
                        onSaveClick = onSaveClick,
                        postCallbacks = postCallbacks,
                        onAuthorClick = onAuthorClick,
                        onCaptionClick = onCaptionClick,
                        onCommentsClick = onCommentsClick,
                    )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NubecitaWavyProgressIndicator(color = Color.White)
    }
}

@Composable
private fun ErrorState(
    error: MediaViewerError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        NubecitaOverlayIconButton(
            icon = NubecitaIconName.Close,
            accessibilityLabel = stringResource(R.string.mediaviewer_close_content_description),
            onClick = onDismiss,
            modifier = Modifier.padding(MaterialTheme.spacing.s2).align(Alignment.TopStart),
        )
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
    postUri: String,
    contextKey: String,
    onPageChange: (Int) -> Unit,
    onTapImage: () -> Unit,
    onAltBadgeClick: () -> Unit,
    onAltSheetDismiss: () -> Unit,
    onChromeAutoFadeTimeout: () -> Unit,
    onDismissRequest: () -> Unit,
    onSaveClick: () -> Unit,
    postCallbacks: PostCallbacks,
    onAuthorClick: () -> Unit,
    onCaptionClick: () -> Unit,
    onCommentsClick: () -> Unit,
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
    val gallerySync = LocalGalleryStateSync.current

    var activePageZoomFraction by remember { mutableFloatStateOf(0f) }
    val isAtMinZoom = activePageZoomFraction <= MIN_ZOOM_TOLERANCE

    val swipeDownThresholdPx = with(LocalDensity.current) { SWIPE_DOWN_DISMISS_THRESHOLD_DP.dp.toPx() }
    var dismissDragOffset by remember { mutableFloatStateOf(0f) }
    val dismissDraggableState =
        rememberDraggableState { delta ->
            dismissDragOffset = (dismissDragOffset + delta).coerceAtLeast(0f)
        }

    LaunchedEffect(pagerState, postUri) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (postUri.isNotBlank()) {
                    gallerySync.setActiveIndex(postUri, page)
                }
                currentOnPageChange(page)
            }
    }

    LaunchedEffect(status.isChromeVisible, status.currentIndex, status.isSaving, status.isCommentsOpen) {
        if (status.isChromeVisible && !status.isSaving && !status.isCommentsOpen) {
            kotlinx.coroutines.delay(CHROME_AUTO_FADE_DELAY)
            currentOnChromeAutoFadeTimeout()
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
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
            userScrollEnabled = isAtMinZoom,
        ) { page ->
            val image = status.images[page]
            val zoomableState = rememberZoomableImageState(rememberZoomableState())
            val isActivePage = page == pagerState.currentPage

            LaunchedEffect(isActivePage, zoomableState) {
                if (isActivePage) {
                    snapshotFlow { zoomableState.zoomableState.zoomFraction ?: 0f }
                        .collect { activePageZoomFraction = it }
                }
            }

            val context = LocalContext.current
            // The placeholder is the card's already-decoded thumbnail, keyed
            // exactly as the card wrote it, so the first frame is the image
            // rather than black. crossfade(false) because the thumbnail and the
            // fullsize are the same picture — a crossfade reads as a flicker.
            val imageRequest =
                remember(image.fullsizeUrl, page, postUri, contextKey) {
                    ImageRequest
                        .Builder(context)
                        .data(image.fullsizeUrl)
                        .apply {
                            if (postUri.isNotBlank() && contextKey != SharedImageSurfaces.NONE) {
                                placeholderMemoryCacheKey(
                                    postImageMemoryCacheKey(contextKey, postUri, page),
                                )
                                crossfade(false)
                            }
                        }.build()
                }

            // Only the page the user is actually on takes part in the
            // transition — every composed page registering at once would
            // animate the off-screen neighbours too. Gating `enabled` rather
            // than the whole call keeps the registration stable across swipes.
            val sharedModifier =
                sharedImageModifier(
                    postUri = postUri,
                    index = page,
                    enabled = isActivePage,
                    surface = contextKey,
                    // Inverted relative to the card: the viewer rests square
                    // and rounds off toward the card's radius as it shrinks.
                    cornerRadius = SharedImageCorners.VIEWER,
                    peerCornerRadius = SharedImageCorners.CARD,
                )

            ZoomableAsyncImage(
                model = imageRequest,
                contentDescription = image.altText,
                state = zoomableState,
                modifier = Modifier.fillMaxSize().then(sharedModifier),
                contentScale = ContentScale.Fit,
                onClick = { onTapImage() },
            )
        }

        val chromeEnter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec())
        val chromeExit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
        AnimatedVisibility(
            visible = status.isChromeVisible,
            enter = chromeEnter,
            exit = chromeExit,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
        ) {
            PostDetailsOverlay(
                post = status.post,
                callbacks = postCallbacks,
                onAuthorClick = onAuthorClick,
                onCaptionClick = onCaptionClick,
                onCommentsClick = onCommentsClick,
            )
        }

        AnimatedVisibility(
            visible = status.isChromeVisible,
            enter = chromeEnter,
            exit = chromeExit,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        ) {
            OverlayChrome(
                currentIndex = status.currentIndex,
                totalImages = status.images.size,
                hasAltText = !status.images[status.currentIndex].altText.isNullOrBlank(),
                canSave = status.canSave,
                isSaving = status.isSaving,
                onClose = onDismissRequest,
                onAltBadgeClick = onAltBadgeClick,
                onSaveClick = onSaveClick,
            )
        }

        if (status.isAltSheetOpen) {
            val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
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
private fun OverlayChrome(
    currentIndex: Int,
    totalImages: Int,
    hasAltText: Boolean,
    canSave: Boolean,
    isSaving: Boolean,
    onClose: () -> Unit,
    onAltBadgeClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(MaterialTheme.spacing.s2),
    ) {
        NubecitaOverlayIconButton(
            icon = NubecitaIconName.Close,
            accessibilityLabel = stringResource(R.string.mediaviewer_close_content_description),
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart),
        )
        if (totalImages > 1) {
            NubecitaOverlayPill(
                text = stringResource(R.string.mediaviewer_page_indicator, currentIndex + 1, totalImages),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = MaterialTheme.spacing.s2),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasAltText) {
                NubecitaOverlayPill(
                    text = stringResource(R.string.mediaviewer_alt_badge_label),
                    onClick = onAltBadgeClick,
                    accessibilityLabel = stringResource(R.string.mediaviewer_alt_badge_content_description),
                )
            }
            if (canSave) {
                NubecitaOverlayIconButton(
                    icon = NubecitaIconName.Download,
                    accessibilityLabel = stringResource(R.string.mediaviewer_save_content_description),
                    onClick = onSaveClick,
                    busy = isSaving,
                )
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
