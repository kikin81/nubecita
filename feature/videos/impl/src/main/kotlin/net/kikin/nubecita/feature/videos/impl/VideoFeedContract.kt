package net.kikin.nubecita.feature.videos.impl

import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.video.playback.VideoSource
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostUi

/** One item in the vertical video feed: the [source] to play + its [post] for chrome. */
data class VideoFeedItem(
    val post: PostUi,
    val source: VideoSource,
) {
    /** Poster frame for this clip, if the embed declared one. */
    val posterUrl: String? get() = (post.embed as? EmbedUi.Video)?.posterUrl

    /** Declared frame ratio, available before any decode. Falls back to portrait. */
    val aspectRatio: Float get() = (post.embed as? EmbedUi.Video)?.aspectRatio ?: DEFAULT_VIDEO_ASPECT_RATIO
}

/**
 * Fallback frame ratio (portrait, the common case) used whenever a real ratio isn't
 * yet known — before decode, and before any [VideoFeedItem] is available at all.
 * Single source of truth so the feed's surface/poster never disagree mid-fallback.
 */
internal const val DEFAULT_VIDEO_ASPECT_RATIO = 9f / 16f

/** Mutually-exclusive load lifecycle of the vertical video feed. */
sealed interface VideoFeedStatus {
    data object Loading : VideoFeedStatus

    data class Content(
        val items: ImmutableList<VideoFeedItem>,
    ) : VideoFeedStatus

    data object Error : VideoFeedStatus
}

data class VideoFeedState(
    val status: VideoFeedStatus = VideoFeedStatus.Loading,
    val activeIndex: Int = 0,
    val isMuted: Boolean = false,
) : UiState

sealed interface VideoFeedEvent : UiEvent {
    /** The visible page settled on [index]. */
    data class ActiveIndexChanged(
        val index: Int,
    ) : VideoFeedEvent

    data object ToggleMute : VideoFeedEvent

    data object Retry : VideoFeedEvent

    /** The author's avatar or display name was tapped — open their profile. */
    data class AuthorTapped(
        val post: PostUi,
    ) : VideoFeedEvent

    /** The post itself was tapped (e.g. the reply affordance) — open the thread. */
    data class PostTapped(
        val post: PostUi,
    ) : VideoFeedEvent
}

/**
 * Screen-specific effects only.
 *
 * Interaction side-effects (share sheet, clipboard, error snackbars, and
 * composer / report / block navigation) are deliberately NOT routed here: the
 * shared `rememberPostInteractions` helper observes `handler.interactionEffects`
 * directly, per the sanctioned delegation contract in CLAUDE.md. Forwarding them
 * onto this channel would double-handle every effect.
 */
sealed interface VideoFeedEffect : UiEffect {
    /**
     * Push a sub-route onto the MainShell back stack. The *screen* performs the
     * push via `LocalMainShellNavState` — a ViewModel can't reach a
     * CompositionLocal, which is exactly why this is an effect.
     */
    data class NavigateTo(
        val target: NavKey,
    ) : VideoFeedEffect
}
