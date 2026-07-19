package net.kikin.nubecita.feature.videos.impl

import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.core.video.playback.DataSaverStatus
import net.kikin.nubecita.core.video.playback.PlaylistPlaybackState
import net.kikin.nubecita.core.video.playback.VerticalVideoPlaylistPlayer
import net.kikin.nubecita.core.video.playback.VideoSource
import net.kikin.nubecita.core.videofeed.VideoFeedSource
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.feature.videos.api.VideoFeed
import timber.log.Timber

/**
 * Drives the vertical video feed: loads video posts from [VideoFeedSource]
 * (cursor-paginated), feeds them to the [VerticalVideoPlaylistPlayer] pool
 * (active + next-prewarmed), and paginates as the user nears the end.
 *
 * Decoder handoff (video-playback-engine design D3/D5): releases
 * [SharedVideoPlayer] on entry so its decoder is free for the pool's ≤2, and the
 * screen drives [onStart]/[onStop] on the surface lifecycle. The pool is
 * released in [onCleared].
 */
@HiltViewModel(assistedFactory = VideoFeedViewModel.Factory::class)
class VideoFeedViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: VideoFeed,
        private val source: VideoFeedSource,
        private val pool: VerticalVideoPlaylistPlayer,
        private val sharedVideoPlayer: SharedVideoPlayer,
        private val dataSaver: DataSaverStatus,
    ) : MviViewModel<VideoFeedState, VideoFeedEvent, VideoFeedEffect>(
            VideoFeedState(activeIndex = route.startIndex.coerceAtLeast(0)),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: VideoFeed): VideoFeedViewModel
        }

        /** The ExoPlayer for the active page — the screen renders `PlayerSurface(activePlayer)`. */
        val activePlayer: StateFlow<Player?> = pool.activePlayer
        val playbackState: StateFlow<PlaylistPlaybackState> = pool.playbackState

        private val loaded = mutableListOf<VideoFeedItem>()
        private var cursor: String? = null
        private var endReached = false
        private var loadingMore = false
        private var loadJob: Job? = null
        private var loadMoreJob: Job? = null

        init {
            // Free the feed player's decoder for the pool's budget (rebuilds lazily on return).
            sharedVideoPlayer.release()
            // Under Data Saver, don't prefetch the next clip (the active one still plays).
            pool.setPrewarmEnabled(!dataSaver.isActive())
            loadFirstPage()
        }

        override fun handleEvent(event: VideoFeedEvent) {
            when (event) {
                is VideoFeedEvent.ActiveIndexChanged -> onActiveIndexChanged(event.index)
                VideoFeedEvent.ToggleMute -> toggleMute()
                VideoFeedEvent.Retry -> loadFirstPage()
            }
        }

        private fun loadFirstPage() {
            // Cancel any in-flight loads so a retry can't race a pending page append onto the cleared list.
            loadJob?.cancel()
            loadMoreJob?.cancel()
            loadingMore = false
            setState { copy(status = VideoFeedStatus.Loading) }
            loadJob =
                viewModelScope.launch {
                    source
                        .loadPage(null)
                        .onSuccess { page ->
                            cursor = page.cursor
                            endReached = page.cursor == null
                            loaded.clear()
                            loaded += page.items.mapNotNull { it.toVideoFeedItemOrNull() }
                            if (loaded.isEmpty()) {
                                setState { copy(status = VideoFeedStatus.Error) }
                            } else {
                                val initialIndex = route.startIndex.coerceIn(0, loaded.lastIndex)
                                setState { copy(status = VideoFeedStatus.Content(loaded.toImmutableList()), activeIndex = initialIndex) }
                                pool.bind(loaded.map { it.source }, startIndex = initialIndex)
                            }
                        }.onFailure {
                            Timber.w(it, "video feed first page failed")
                            setState { copy(status = VideoFeedStatus.Error) }
                        }
                }
        }

        private fun onActiveIndexChanged(index: Int) {
            if (index == uiState.value.activeIndex) return
            setState { copy(activeIndex = index) }
            viewModelScope.launch { pool.onActiveIndexChanged(index) }
            maybeLoadMore(index)
        }

        private fun maybeLoadMore(index: Int) {
            if (loadingMore || endReached) return
            if (index < loaded.size - PREFETCH_THRESHOLD) return
            loadingMore = true
            loadMoreJob =
                viewModelScope.launch {
                    try {
                        source
                            .loadPage(cursor)
                            .onSuccess { page ->
                                cursor = page.cursor
                                endReached = page.cursor == null
                                val fresh = page.items.mapNotNull { it.toVideoFeedItemOrNull() }
                                if (fresh.isNotEmpty()) {
                                    loaded += fresh
                                    setState { copy(status = VideoFeedStatus.Content(loaded.toImmutableList())) }
                                    // Re-bind with the appended items so the pool can prewarm past the old tail.
                                    // settle() reuses the active/prewarm slots by index, so this doesn't restart playback.
                                    pool.bind(loaded.map { it.source }, startIndex = uiState.value.activeIndex)
                                }
                            }.onFailure { Timber.w(it, "video feed page failed") }
                    } finally {
                        // Reset even on cancellation, so a cancelled load can't leave the flag stuck true.
                        loadingMore = false
                    }
                }
        }

        private fun toggleMute() {
            val muted = !uiState.value.isMuted
            setState { copy(isMuted = muted) }
            pool.setMuted(muted)
        }

        /** Surface returned to the foreground — re-prepare playback. */
        fun onStart() {
            viewModelScope.launch { pool.onStart() }
        }

        /** Surface backgrounded — release the pool's decoders. */
        fun onStop() {
            viewModelScope.launch { pool.onStop() }
        }

        override fun onCleared() {
            pool.release()
            super.onCleared()
        }

        private fun PostUi.toVideoFeedItemOrNull(): VideoFeedItem? =
            (embed as? EmbedUi.Video)?.let { video ->
                VideoFeedItem(post = this, source = VideoSource(video.playlistUrl, video.posterUrl))
            }

        private companion object {
            /** Load the next page once the active index is within this many items of the tail. */
            const val PREFETCH_THRESHOLD = 3
        }
    }
