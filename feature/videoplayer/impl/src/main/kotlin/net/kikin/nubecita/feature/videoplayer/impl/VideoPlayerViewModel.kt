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
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.core.video.PlaybackMode
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute

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
 * On init: resolves the post via [PostRepository.getPost] (network
 * round-trip) — the project's single `getPosts` read surface, which
 * returns a fully-mapped `PostUi` carrying both the video embed and the
 * post's social metadata (author / stats / viewer). The VM extracts the
 * `EmbedUi.Video` for playback and projects the social fields into
 * [VideoPlayerState]; it checks if the holder is already bound to the
 * resolved URL (instance-transfer payoff if true), and either skips the
 * rebind or calls [SharedVideoPlayer.bind] before flipping the mode to
 * [PlaybackMode.Fullscreen]. A resolved post with no video embed is a
 * resolution error (the caller routed a non-video URI here).
 *
 * Observes [SharedVideoPlayer]'s state flows (isPlaying, positionMs,
 * durationMs, playbackError) and projects them into the flat
 * [VideoPlayerState] via a single `combine` operator. The chrome's
 * [ChromeVisibility] auto-hide ladder (Shown → Peeking → Hidden) is a
 * screen-local timeline armed off play/pause transitions (a separate
 * `isPlaying` collector): playback start reveals + arms it, any
 * interaction restarts it, and pausing pins it to Shown — so a slow
 * resolver or a paused/errored screen never hides the controls.
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
        private val postRepository: PostRepository,
    ) : MviViewModel<VideoPlayerState, VideoPlayerEvent, VideoPlayerEffect>(VideoPlayerState()) {
        @AssistedFactory
        interface Factory {
            fun create(route: VideoPlayerRoute): VideoPlayerViewModel
        }

        private val postUri: String = route.postUri

        private var chromeLadderJob: Job? = null

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

            // Drive the chrome auto-hide ladder off play/pause transitions:
            // playing → reveal + arm the Shown → Peeking → Hidden timeline;
            // paused → pin Shown (cancel the timeline). isPlaying is a StateFlow,
            // so it already only emits on change — the per-tick position updates
            // live in the separate combine above and never restart the ladder.
            // On a resolver failure isPlaying never goes true, so the chrome stays
            // Shown and the Retry button remains reachable.
            sharedVideoPlayer.isPlaying
                .onEach { playing -> if (playing) showChrome() else pinChromeShown() }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: VideoPlayerEvent) {
            when (event) {
                VideoPlayerEvent.PlayPauseClicked -> {
                    if (uiState.value.isPlaying) {
                        sharedVideoPlayer.pause()
                    } else {
                        sharedVideoPlayer.play()
                    }
                    showChrome()
                }
                VideoPlayerEvent.SkipBack -> {
                    val target = (uiState.value.positionMs - SKIP_INCREMENT_MS).coerceAtLeast(0L)
                    sharedVideoPlayer.seekTo(target)
                    showChrome()
                }
                VideoPlayerEvent.SkipForward -> {
                    // Clamp to duration only once it's known; while durationMs is
                    // still 0 (duration probe lagging) just advance by the
                    // increment so an early tap isn't pinned to 0.
                    val duration = uiState.value.durationMs
                    val advanced = uiState.value.positionMs + SKIP_INCREMENT_MS
                    val target = if (duration > 0L) advanced.coerceAtMost(duration) else advanced
                    sharedVideoPlayer.seekTo(target)
                    showChrome()
                }
                VideoPlayerEvent.MuteClicked -> {
                    sharedVideoPlayer.toggleMute()
                    setState { copy(isMuted = !isMuted) }
                    showChrome()
                }
                is VideoPlayerEvent.SeekTo -> {
                    sharedVideoPlayer.seekTo(event.positionMs)
                    showChrome()
                }
                // A surface tap always returns the chrome to Shown and restarts
                // the ladder (design: "any tap returns to Shown"); the ladder
                // takes it back down on idle. There is no tap-to-hide.
                VideoPlayerEvent.ToggleChrome -> showChrome()
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
                postRepository
                    .getPost(postUri)
                    .onSuccess { post ->
                        val video = post.embed as? EmbedUi.Video
                        if (video == null) {
                            // The post resolved but isn't a video post — the
                            // caller routed a non-video URI to the fullscreen
                            // player. Treat as a resolution error (no Retry
                            // will change the embed type, but the error layout
                            // is the right surface). Mirrors the old resolver's
                            // IllegalStateException → Unknown mapping.
                            setState {
                                copy(
                                    loadStatus =
                                        VideoPlayerLoadStatus.Error(
                                            VideoPlayerError.Unknown(cause = NO_VIDEO_EMBED),
                                        ),
                                )
                            }
                            return@onSuccess
                        }
                        val alreadyBound =
                            sharedVideoPlayer.boundPlaylistUrl.value == video.playlistUrl
                        if (!alreadyBound) {
                            sharedVideoPlayer.bind(
                                playlistUrl = video.playlistUrl,
                                posterUrl = video.posterUrl,
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
                        // On instance-transfer the holder may already
                        // have decoded the video and emitted a real
                        // aspectRatio into state.aspectRatio via the
                        // videoAspectRatio collector. Don't clobber that
                        // measured value with the (possibly fallback)
                        // lexicon hint — fall back to the hint only
                        // when no decoded value has arrived yet.
                        setState {
                            copy(
                                loadStatus = VideoPlayerLoadStatus.Ready,
                                posterUrl = video.posterUrl,
                                altText = video.altText,
                                aspectRatio =
                                    sharedVideoPlayer.videoAspectRatio.value
                                        ?: aspectRatio
                                        ?: video.aspectRatio,
                                // Social metadata from the resolved post —
                                // populated together on Ready (nubecita-6rdb.2).
                                author = post.author,
                                stats = post.stats,
                                viewer = post.viewer,
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

        /**
         * Reveal the chrome (→ Shown) and, while playing, arm the
         * Shown → Peeking → Hidden idle timeline. Cancels any in-flight
         * timeline first so an interaction restarts the countdown. When
         * paused the timeline is not armed, so the chrome stays Shown.
         */
        private fun showChrome() {
            chromeLadderJob?.cancel()
            setState { copy(chromeVisibility = ChromeVisibility.Shown) }
            if (uiState.value.isPlaying) {
                chromeLadderJob =
                    viewModelScope.launch {
                        delay(PEEK_DELAY_MS)
                        setState { copy(chromeVisibility = ChromeVisibility.Peeking) }
                        delay(HIDE_DELAY_MS)
                        setState { copy(chromeVisibility = ChromeVisibility.Hidden) }
                    }
            }
        }

        /** Cancel the idle timeline and pin the chrome to Shown (used on pause). */
        private fun pinChromeShown() {
            chromeLadderJob?.cancel()
            chromeLadderJob = null
            setState { copy(chromeVisibility = ChromeVisibility.Shown) }
        }

        override fun onCleared() {
            super.onCleared()
            sharedVideoPlayer.detachSurface()
        }

        private companion object {
            // Idle dwell on each rung of the auto-hide ladder while playing:
            // Shown → (PEEK_DELAY) → Peeking → (HIDE_DELAY) → Hidden.
            const val PEEK_DELAY_MS: Long = 3_000L
            const val HIDE_DELAY_MS: Long = 3_000L
            const val SKIP_INCREMENT_MS: Long = 10_000L
            const val NO_VIDEO_EMBED: String = "Post has no video embed"
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
