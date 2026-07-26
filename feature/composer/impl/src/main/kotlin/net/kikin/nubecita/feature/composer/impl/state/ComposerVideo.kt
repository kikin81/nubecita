package net.kikin.nubecita.feature.composer.impl.state

import android.net.Uri
import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.videoupload.VideoUploadState

/**
 * The composer's single video attachment.
 *
 * A post carries at most one video and never alongside images, a GIF or a link
 * card — `app.bsky.embed.video` takes one blob and the lexicon provides no
 * multi-video form, so this is a permanent bound rather than a simplification.
 *
 * [uploadState] mirrors the pipeline. The upload starts when the video is
 * picked, not when the post is submitted, so by the time the user finishes
 * typing the blob is usually already available.
 *
 * @property uri the picked source, retained so a failed upload can be retried
 *   without asking the user to pick again.
 * @property alt accessibility description. Unlike the gallery rule, blank does
 *   not block submission — that gate exists because a 5-image post is
 *   unreadable without descriptions, and the reasoning does not carry to one
 *   video.
 */
@Immutable
data class ComposerVideo(
    val uri: Uri,
    val alt: String = "",
    val uploadState: VideoUploadState = VideoUploadState.CheckingLimits,
) {
    /** True once the blob exists and the post can be submitted. */
    val isReady: Boolean get() = uploadState is VideoUploadState.Ready

    /** True when the pipeline stopped and the user needs a retry affordance. */
    val hasFailed: Boolean get() = uploadState is VideoUploadState.Failed

    /**
     * Progress of the current stage, or `null` for stages that have none.
     *
     * Each stage reports its own 0..1 fraction rather than a single global bar:
     * compression, upload and server-side processing take wildly different
     * times, so a combined percentage would stall and jump misleadingly.
     */
    val stageProgress: Float?
        get() =
            when (val state = uploadState) {
                is VideoUploadState.Compressing -> state.progress
                is VideoUploadState.Uploading -> state.progress
                is VideoUploadState.Processing -> state.progress
                else -> null
            }
}

/**
 * The finished blob to attach to a post, or `null` if the upload has not
 * reached a terminal success.
 *
 * Returning null rather than throwing keeps the submit path total: the gate
 * already refuses to submit while an upload is in flight, so this is the
 * second of two independent guards against publishing a post whose video was
 * silently dropped.
 */
internal fun ComposerVideo.readyEmbed(): net.kikin.nubecita.core.posting.ComposerVideoEmbed? =
    (uploadState as? VideoUploadState.Ready)?.let {
        net.kikin.nubecita.core.posting.ComposerVideoEmbed(
            blob = it.blob,
            alt = alt,
            aspectRatio = it.aspectRatio,
        )
    }
