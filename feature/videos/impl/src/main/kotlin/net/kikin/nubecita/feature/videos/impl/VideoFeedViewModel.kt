package net.kikin.nubecita.feature.videos.impl

import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.core.video.playback.DataSaverStatus
import net.kikin.nubecita.core.video.playback.PlaylistPlaybackState
import net.kikin.nubecita.core.video.playback.VerticalVideoPlaylistPlayer
import net.kikin.nubecita.core.video.playback.VideoSource
import net.kikin.nubecita.core.videofeed.VideoFeedSource
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
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
        private val handler: PostInteractionHandler,
        private val postInteractionsCache: PostInteractionsCache,
    ) : MviViewModel<VideoFeedState, VideoFeedEvent, VideoFeedEffect>(
            VideoFeedState(),
        ),
        PostInteractionHandler by handler {
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
            handler.bind(PostSurface.Videos, viewModelScope)
            // READ-merge only: the handler owns writes and tap markers. Without this the
            // optimistic like/repost state never reaches the rail, so a tap appears to
            // work and then reverts — a regression that has happened before on a
            // handler migration, hence its own test.
            viewModelScope.launch {
                // The read and the merge happen atomically INSIDE setState. Mapping over
                // `uiState.value` outside it would let a concurrent page-append land
                // between the read and the write and be silently overwritten.
                postInteractionsCache.state.collect { snapshot ->
                    setState {
                        val current = status
                        if (current is VideoFeedStatus.Content) {
                            copy(status = current.copy(items = current.items.applyInteractions(snapshot)))
                        } else {
                            this
                        }
                    }
                }
            }
            // Free the feed player's decoder for the pool's budget (rebuilds lazily on return).
            sharedVideoPlayer.release()
            // Under Data Saver, don't prefetch the next clip (the active one still plays).
            pool.setPrewarmEnabled(!dataSaver.isActive())
            loadFirstPage()
        }

        private fun ImmutableList<VideoFeedItem>.applyInteractions(
            interactionMap: PersistentMap<String, PostInteractionState>,
        ): ImmutableList<VideoFeedItem> =
            map { item ->
                interactionMap[item.post.id]
                    ?.let { state -> item.copy(post = item.post.mergeInteractionState(state)) }
                    ?: item
            }.toImmutableList()

        override fun handleEvent(event: VideoFeedEvent) {
            when (event) {
                is VideoFeedEvent.ActiveIndexChanged -> onActiveIndexChanged(event.index)
                VideoFeedEvent.ToggleMute -> toggleMute()
                VideoFeedEvent.Retry -> loadFirstPage()
                is VideoFeedEvent.AuthorTapped ->
                    sendEffect(VideoFeedEffect.NavigateTo(Profile(handle = event.post.author.did)))

                is VideoFeedEvent.PostTapped ->
                    sendEffect(VideoFeedEffect.NavigateTo(PostDetailRoute(postUri = event.post.id)))
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
                            // distinctBy: the appview can return the same post twice, which
                            // would render duplicated in the carousel AND produce duplicate
                            // keys in the pager (it keys on post.id) — a lazy layout rejects
                            // those. See nubecita-zdv8.13.
                            loaded += page.items.mapNotNull { it.toVideoFeedItemOrNull() }.distinctBy { it.post.id }
                            if (loaded.isEmpty()) {
                                setState { copy(status = VideoFeedStatus.Error) }
                            } else {
                                // Resolve by identity, not position: the carousel's page and
                                // this one are separate fetches of a live feed, so an index
                                // would denote a different post. A post that has aged out of
                                // the page opens at the top rather than failing.
                                val initialIndex = loaded.indexOfFirst { it.post.id == route.startPostUri }.coerceAtLeast(0)
                                // Merge the cache NOW rather than waiting for the collector's next
                                // emission: a post already liked on another surface would otherwise
                                // render unliked for a frame before snapping.
                                val merged = loaded.toImmutableList().applyInteractions(postInteractionsCache.state.value)
                                setState { copy(status = VideoFeedStatus.Content(merged), activeIndex = initialIndex) }
                                pool.bind(loaded.map { it.source }, startIndex = initialIndex)
                                postInteractionsCache.seed(loaded.map { it.post })
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
                                // Filter against what is already loaded as well as within the
                                // page: overlapping pages are normal, and a repeat would
                                // duplicate a pager key.
                                val seen = loaded.mapTo(mutableSetOf()) { it.post.id }
                                val fresh = page.items.mapNotNull { it.toVideoFeedItemOrNull() }.filter { seen.add(it.post.id) }
                                if (fresh.isNotEmpty()) {
                                    loaded += fresh
                                    val merged = loaded.toImmutableList().applyInteractions(postInteractionsCache.state.value)
                                    setState { copy(status = VideoFeedStatus.Content(merged)) }
                                    // Re-bind with the appended items so the pool can prewarm past the old tail.
                                    // settle() reuses the active/prewarm slots by index, so this doesn't restart playback.
                                    pool.bind(loaded.map { it.source }, startIndex = uiState.value.activeIndex)
                                    postInteractionsCache.seed(fresh.map { it.post })
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
