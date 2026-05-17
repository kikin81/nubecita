package net.kikin.nubecita.feature.videoplayer.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.video.PlaybackMode
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.feature.videoplayer.impl.data.VideoPostResolver
import javax.inject.Inject

/**
 * Presenter for the fullscreen video player.
 *
 * On init: reads the postUri from the navigation handle, resolves it
 * via [VideoPostResolver] (network round-trip), checks if the holder is
 * already bound to the resolved URL (instance-transfer payoff if true),
 * and either skips the rebind or calls [SharedVideoPlayer.bind] before
 * flipping the mode to [PlaybackMode.Fullscreen].
 *
 * Observes [SharedVideoPlayer]'s state flows (isPlaying, positionMs,
 * durationMs, playbackError) and projects them into the flat
 * [VideoPlayerState] via a single `combine` operator. Chrome auto-hide
 * is a screen-local timer keyed on user interaction (events that fire
 * the timer reset: ToggleChrome, PlayPauseClicked, MuteClicked, SeekTo).
 *
 * Restores `PlaybackMode.FeedPreview` on `viewModelScope.cancellation`
 * via the registered `addCloseable { … }` — this is the symmetric
 * dispose of the init-time `setMode(Fullscreen)`.
 *
 * [sharedVideoPlayer] is `val` (not `private val`) so the Screen
 * composable can read `sharedVideoPlayer.player` to render
 * `PlayerSurface(player = …)`.
 */
@HiltViewModel
internal class VideoPlayerViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        val sharedVideoPlayer: SharedVideoPlayer,
        private val resolver: VideoPostResolver,
    ) : MviViewModel<VideoPlayerState, VideoPlayerEvent, VideoPlayerEffect>(VideoPlayerState()) {
        private val postUri: String =
            savedStateHandle.get<String>("postUri") ?: error("postUri missing from SavedStateHandle")

        private var autoHideJob: Job? = null

        init {
            // Restore FeedPreview mode when the VM is destroyed (back-nav,
            // process death, screen leaves the back stack).
            addCloseable {
                sharedVideoPlayer.setMode(PlaybackMode.FeedPreview)
            }

            // Begin resolution + bind + setMode(Fullscreen).
            resolveAndBind()

            // Project holder flows → flat VideoPlayerState.
            combine(
                sharedVideoPlayer.isPlaying,
                sharedVideoPlayer.positionMs,
                sharedVideoPlayer.durationMs,
                sharedVideoPlayer.playbackError,
            ) { isPlaying, positionMs, durationMs, playbackError ->
                Quad(isPlaying, positionMs, durationMs, playbackError)
            }.onEach { (isPlaying, positionMs, durationMs, playbackError) ->
                if (playbackError != null) {
                    setState {
                        copy(
                            loadStatus =
                                VideoPlayerLoadStatus.Error(
                                    error = playbackError.toVideoPlayerError(),
                                ),
                        )
                    }
                } else {
                    setState {
                        copy(
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            durationMs = durationMs,
                        )
                    }
                }
            }.launchIn(viewModelScope)

            // Auto-hide chrome on entry — kick off the initial timer.
            scheduleChromeAutoHide()
        }

        override fun handleEvent(event: VideoPlayerEvent) {
            when (event) {
                VideoPlayerEvent.PlayPauseClicked -> {
                    if (uiState.value.isPlaying) {
                        sharedVideoPlayer.pause()
                    } else {
                        sharedVideoPlayer.play()
                    }
                    scheduleChromeAutoHide()
                }
                VideoPlayerEvent.MuteClicked -> {
                    sharedVideoPlayer.toggleMute()
                    setState { copy(isMuted = !isMuted) }
                    scheduleChromeAutoHide()
                }
                is VideoPlayerEvent.SeekTo -> {
                    sharedVideoPlayer.seekTo(event.positionMs)
                    scheduleChromeAutoHide()
                }
                VideoPlayerEvent.ToggleChrome -> {
                    val next = !uiState.value.chromeVisible
                    setState { copy(chromeVisible = next) }
                    if (next) scheduleChromeAutoHide() else cancelChromeAutoHide()
                }
                VideoPlayerEvent.BackClicked -> {
                    sendEffect(VideoPlayerEffect.NavigateBack)
                }
                VideoPlayerEvent.RetryClicked -> {
                    resolveAndBind()
                }
            }
        }

        private fun resolveAndBind() {
            setState { copy(loadStatus = VideoPlayerLoadStatus.Resolving) }
            viewModelScope.launch {
                resolver
                    .resolve(postUri)
                    .onSuccess { resolved ->
                        val alreadyBound =
                            sharedVideoPlayer.boundPlaylistUrl.value == resolved.playlistUrl
                        if (!alreadyBound) {
                            sharedVideoPlayer.bind(
                                playlistUrl = resolved.playlistUrl,
                                posterUrl = resolved.posterUrl,
                            )
                        }
                        sharedVideoPlayer.setMode(PlaybackMode.Fullscreen)
                        sharedVideoPlayer.attachSurface()
                        sharedVideoPlayer.play()
                        setState {
                            copy(
                                loadStatus = VideoPlayerLoadStatus.Ready,
                                posterUrl = resolved.posterUrl,
                            )
                        }
                    }.onFailure { throwable ->
                        setState {
                            copy(
                                loadStatus =
                                    VideoPlayerLoadStatus.Error(
                                        throwable.toVideoPlayerError(),
                                    ),
                            )
                        }
                    }
            }
        }

        private fun scheduleChromeAutoHide() {
            autoHideJob?.cancel()
            setState { copy(chromeVisible = true) }
            autoHideJob =
                viewModelScope.launch {
                    delay(CHROME_AUTO_HIDE_MS)
                    setState { copy(chromeVisible = false) }
                }
        }

        private fun cancelChromeAutoHide() {
            autoHideJob?.cancel()
            autoHideJob = null
        }

        override fun onCleared() {
            super.onCleared()
            sharedVideoPlayer.detachSurface()
        }

        private companion object {
            const val CHROME_AUTO_HIDE_MS: Long = 3_000L
        }

        // Tiny helper for combine destructuring readability — the 4-flow
        // combine uses a generic lambda; Quad gives named component functions.
        private data class Quad<A, B, C, D>(
            val a: A,
            val b: B,
            val c: C,
            val d: D,
        )
    }
