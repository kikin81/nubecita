@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.feature.videos.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.PlayerSurface
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator

/**
 * Full-screen vertical video feed. A snapping [VerticalPager] whose settled page
 * drives the pooled player via the ViewModel; a single persistent `PlayerSurface`
 * sits behind the pager and re-binds to whichever pooled player is active, so it
 * is never recreated across swipes. Overlay chrome (author, caption, interactions,
 * mute control) and the poster underlay land in the next slice.
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

    Scaffold(modifier = modifier, containerColor = Color.Black) { innerPadding ->
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
                Box(contentModifier) {
                    // ONE persistent video surface for the whole feed, sitting behind the
                    // pager. Because it is never recreated as the active page changes,
                    // promoting a pooled player only re-binds it (`setVideoSurface` on the
                    // already-attached Surface) — there is no async surface-attach race, so
                    // the next video presents on settle with no relayout "nudge", and the
                    // SurfaceView keeps its efficient hardware-overlay path (battery). The
                    // pool guarantees exactly one active player, so a single surface is all
                    // the feed needs. Poster underlay, per-page slide, and a first-frame
                    // crossfade arrive in Slice 3b.
                    activePlayer?.let { player ->
                        PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
                    }
                    // The pager is a transparent gesture + snapping layer on top; its pages
                    // carry no content yet (3b), so the surface behind shows through. It owns
                    // the swipe gesture and reports the settled page to the ViewModel.
                    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize())
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
