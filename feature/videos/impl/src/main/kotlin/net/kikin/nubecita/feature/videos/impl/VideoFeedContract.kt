package net.kikin.nubecita.feature.videos.impl

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
    val aspectRatio: Float get() = (post.embed as? EmbedUi.Video)?.aspectRatio ?: (9f / 16f)
}

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
}

/** No screen-specific effects yet (navigation to author/detail arrives with chrome in a later slice). */
sealed interface VideoFeedEffect : UiEffect
