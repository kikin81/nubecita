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
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.PipAttempt
import net.kikin.nubecita.core.analytics.PipOutcome
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.core.video.PlaybackMode
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import timber.log.Timber

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
        private val postRepository: PostRepository,
        private val analytics: AnalyticsClient,
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
                    val mapped = playbackError.toVideoPlayerError()
                    // WARN so CrashlyticsTree forwards it as a breadcrumb: the
                    // three user-facing strings are deliberately vague, so
                    // without this the only record of WHY playback failed is a
                    // cable and a PID-filtered logcat (nubecita-o91i).
                    // `playbackError` is a Throwable, not a PlaybackException —
                    // the code name is read off the mapped result, which already
                    // extracted it in toVideoPlayerError().
                    Timber.w(
                        "video playback failed: %s (code=%s) uri=%s",
                        mapped::class.simpleName,
                        (mapped as? VideoPlayerError.Unknown)?.errorCodeName ?: "-",
                        postUri,
                    )
                    setState {
                        copy(loadStatus = VideoPlayerLoadStatus.Error(error = mapped))
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

        /**
         * Record a picture-in-picture reach from the fullscreen player. The
         * enter-vs-upsell *decision* stays in the Compose layer (design D5); this
         * only logs the outcome so the PiP → paywall funnel is measurable.
         */
        fun onPipReach(entered: Boolean) {
            analytics.log(PipAttempt(if (entered) PipOutcome.Entered else PipOutcome.Upsell))
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
                VideoPlayerEvent.SkipBack -> {
                    val target = (uiState.value.positionMs - SKIP_INCREMENT_MS).coerceAtLeast(0L)
                    sharedVideoPlayer.seekTo(target)
                    scheduleChromeAutoHide()
                }
                VideoPlayerEvent.SkipForward -> {
                    // Clamp to duration only once it's known; while durationMs is
                    // still 0 (duration probe lagging) just advance by the
                    // increment so an early tap isn't pinned to 0.
                    val duration = uiState.value.durationMs
                    val advanced = uiState.value.positionMs + SKIP_INCREMENT_MS
                    val target = if (duration > 0L) advanced.coerceAtMost(duration) else advanced
                    sharedVideoPlayer.seekTo(target)
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
                postRepository
                    .getPost(postUri)
                    .onSuccess { post ->
                        val video = post.embed.videoOrNull()
                        if (video == null) {
                            // The post resolved but carries no video anywhere in
                            // its embed. Treat as a resolution error (no Retry
                            // will change the embed type, but the error layout
                            // is the right surface). Mirrors the old resolver's
                            // IllegalStateException → Unknown mapping.
                            //
                            // Logged at WARN because this branch is where a
                            // too-narrow extraction hides: it renders the same
                            // "Something went wrong" as a genuine playback
                            // failure, which is how quote-post videos stayed
                            // broken (nubecita-o91i). The embed class name is
                            // the fact that identifies a repeat.
                            Timber.w(
                                "video resolve failed: no video in embed=%s uri=%s",
                                post.embed::class.simpleName,
                                postUri,
                            )
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

/**
 * The playable video carried by this embed, or `null` if there isn't one.
 *
 * A quote-post with a video maps to [EmbedUi.RecordWithMedia], whose video sits
 * in the `media` slot rather than at the top level. A flat
 * `as? EmbedUi.Video` therefore missed every quote-post video and surfaced
 * "Something went wrong" on a post that plays fine elsewhere (nubecita-o91i).
 *
 * Only these two shapes carry a directly playable video. `media` is typed
 * [EmbedUi.MediaEmbed], so images/external/gif land in the `else` branch and
 * still resolve to `null` — unwrapping must not turn every quote-post into a
 * video post.
 */
private fun EmbedUi.videoOrNull(): EmbedUi.Video? =
    when (this) {
        is EmbedUi.Video -> this
        is EmbedUi.RecordWithMedia -> media as? EmbedUi.Video
        else -> null
    }
