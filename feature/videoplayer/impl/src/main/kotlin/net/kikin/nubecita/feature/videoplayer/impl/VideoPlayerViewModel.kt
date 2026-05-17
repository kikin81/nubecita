package net.kikin.nubecita.feature.videoplayer.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import net.kikin.nubecita.feature.videoplayer.impl.data.VideoPostResolver

/**
 * Presenter for the fullscreen video player.
 *
 * Uses Hilt's assisted-injection bridge so the [VideoPlayerRoute] (the
 * Nav 3 NavKey carrying the post AT URI) flows from the entry-provider
 * call site into the VM constructor without a SavedStateHandle decode
 * step. The canonical Nav 3 pattern documented in the official Hilt
 * recipe — `hiltViewModel<VM, Factory>(creationCallback = { it.create(route) })`
 * — preserves a per-NavEntry VM instance via the
 * `rememberViewModelStoreNavEntryDecorator` already wired in `MainShell`.
 * (NavKey types aren't reachable through SavedStateHandle by default; see
 * `ChatScreenInstrumentationTest.kt` for the failure mode the assisted-
 * inject route prevents.)
 *
 * On init: resolves the post URI via [VideoPostResolver] (network
 * round-trip), checks if the holder is already bound to the resolved
 * URL (instance-transfer payoff if true), and either skips the rebind
 * or calls [SharedVideoPlayer.bind] before flipping the mode to
 * [PlaybackMode.Fullscreen].
 *
 * Observes [SharedVideoPlayer]'s state flows (isPlaying, positionMs,
 * durationMs, playbackError) and projects them into the flat
 * [VideoPlayerState] via a single `combine` operator. Chrome auto-hide
 * is a screen-local timer; the initial timer arms on first entry to
 * [VideoPlayerLoadStatus.Ready] (not in init), so a slow resolver doesn't
 * hide the controls before the user ever sees them.
 *
 * Restores `PlaybackMode.FeedPreview` on `viewModelScope.cancellation`
 * via the registered `addCloseable { … }` — this is the symmetric
 * dispose of the init-time `setMode(Fullscreen)`.
 *
 * [sharedVideoPlayer] is `val` (not `private val`) so the Screen
 * composable can read `sharedVideoPlayer.player` to render
 * `PlayerSurface(player = …)`.
 */
@HiltViewModel(assistedFactory = VideoPlayerViewModel.Factory::class)
internal class VideoPlayerViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: VideoPlayerRoute,
        val sharedVideoPlayer: SharedVideoPlayer,
        private val resolver: VideoPostResolver,
    ) : MviViewModel<VideoPlayerState, VideoPlayerEvent, VideoPlayerEffect>(VideoPlayerState()) {
        @AssistedFactory
        interface Factory {
            fun create(route: VideoPlayerRoute): VideoPlayerViewModel
        }

        private val postUri: String = route.postUri

        private var autoHideJob: Job? = null
        private var autoHideArmed: Boolean = false

        // Tracks whether this VM has incremented the holder's refcount so
        // a successful Retry doesn't double-attach. `onCleared` always
        // calls `detachSurface`, which is refcount-zero-safe inside the
        // holder, so a never-attached VM is harmless.
        private var surfaceAttached: Boolean = false

        init {
            // Restore FeedPreview mode when the VM is destroyed (back-nav,
            // process death, screen leaves the back stack).
            addCloseable {
                sharedVideoPlayer.setMode(PlaybackMode.FeedPreview)
            }

            // Begin resolution + bind + setMode(Fullscreen).
            resolveAndBind()

            // Override the lexicon's aspectRatio hint with the actual
            // decoded video dimensions once ExoPlayer reports them. The
            // lexicon's app.bsky.embed.video#view.aspectRatio field is
            // optional, so a video without it would otherwise be
            // letterboxed at the FeedMapping fallback (16:9) regardless
            // of its real frame shape. Once the decoder produces the
            // first frame, VideoSize is authoritative.
            sharedVideoPlayer.videoAspectRatio
                .onEach { ratio ->
                    if (ratio != null) setState { copy(aspectRatio = ratio) }
                }.launchIn(viewModelScope)

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
                            // If we previously transitioned to Error because of a
                            // playback failure that has since cleared (ExoPlayer
                            // internally recovered → STATE_READY clears
                            // `_playbackError`, or a Retry succeeded), bring the
                            // screen back to Ready. Gated on `surfaceAttached` so
                            // a resolver-failure Error (player never bound) can't
                            // be silently recovered by an unrelated holder-flow
                            // tick — those failures require an explicit Retry.
                            loadStatus =
                                if (loadStatus is VideoPlayerLoadStatus.Error && surfaceAttached) {
                                    VideoPlayerLoadStatus.Ready
                                } else {
                                    loadStatus
                                },
                        )
                    }
                }
            }.launchIn(viewModelScope)
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
                    // Clear the sticky playback error first so the
                    // combine(...) projection doesn't bounce the screen
                    // straight back into Error between Retry and the next
                    // STATE_READY arriving. force=true makes the success
                    // branch call prepareCurrent() even if the holder is
                    // already bound to the same URL (the typical retry
                    // case after a transient playback failure).
                    sharedVideoPlayer.clearPlaybackError()
                    resolveAndBind(force = true)
                }
            }
        }

        private fun resolveAndBind(force: Boolean = false) {
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
                        } else if (force) {
                            // Retry path with the same URL: ask ExoPlayer
                            // to re-prepare the existing media item.
                            sharedVideoPlayer.prepareCurrent()
                        }
                        sharedVideoPlayer.setMode(PlaybackMode.Fullscreen)
                        if (!surfaceAttached) {
                            sharedVideoPlayer.attachSurface()
                            surfaceAttached = true
                        }
                        sharedVideoPlayer.play()
                        setState {
                            copy(
                                loadStatus = VideoPlayerLoadStatus.Ready,
                                posterUrl = resolved.posterUrl,
                                altText = resolved.altText,
                                aspectRatio = resolved.aspectRatio,
                            )
                        }
                        // Arm the auto-hide timer the first time the
                        // screen reaches Ready (subsequent Ready entries
                        // — e.g. retry — don't re-arm; the user's
                        // interactions are the only thing that does).
                        if (!autoHideArmed) {
                            autoHideArmed = true
                            scheduleChromeAutoHide()
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
