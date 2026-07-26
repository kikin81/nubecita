package net.kikin.nubecita.feature.composer.impl.state

import android.net.Uri
import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.posting.ComposerVideoEmbed
import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.core.videoupload.VideoUploadState
import net.kikin.nubecita.core.videoupload.asUploadProgress

/**
 * Which step of the pipeline the video is on.
 *
 * Deliberately carries **no progress fraction**. The fraction changes about
 * four times a second, and anything inside `ComposerState` recomposes the whole
 * composer body when it does — including while the user is typing. The stage
 * changes about four times per *upload*, so keeping only the stage here means
 * the body recomposes when something actually happened.
 *
 * The fraction lives on `ComposerViewModel.videoProgress` instead, collected by
 * the progress bar alone.
 */
enum class ComposerVideoStage {
    CheckingLimits,
    Compressing,
    Uploading,
    Processing,
    Ready,
    Failed,
}

/**
 * The composer's single video attachment.
 *
 * A post carries at most one video and never alongside images, a GIF or a link
 * card — `app.bsky.embed.video` takes one blob and the lexicon provides no
 * multi-video form, so this is a permanent bound rather than a simplification.
 *
 * The upload starts when the video is picked, not when the post is submitted,
 * so by the time the user finishes typing the blob is usually already
 * available.
 *
 * @property uri the picked source, retained so a failed upload can be retried
 *   without asking the user to pick again.
 * @property alt accessibility description. Unlike the gallery rule, blank does
 *   not block submission — that gate exists because a 5-image post is
 *   unreadable without descriptions, and the reasoning does not carry to one
 *   video.
 * @property error set only when [stage] is [ComposerVideoStage.Failed].
 * @property embed the finished blob, set only when [stage] is
 *   [ComposerVideoStage.Ready].
 */
@Immutable
data class ComposerVideo(
    val uri: Uri,
    val alt: String = "",
    val stage: ComposerVideoStage = ComposerVideoStage.CheckingLimits,
    val error: VideoUploadError? = null,
    val embed: ComposerVideoEmbed? = null,
) {
    /** True once the blob exists and the post can be submitted. */
    val isReady: Boolean get() = stage == ComposerVideoStage.Ready

    /** True when the pipeline stopped and the user needs a retry affordance. */
    val hasFailed: Boolean get() = stage == ComposerVideoStage.Failed
}

/**
 * Fold a pipeline state into the composer's view of it.
 *
 * The progress fraction is dropped here on purpose — see [ComposerVideoStage].
 */
internal fun ComposerVideo.withUploadState(state: VideoUploadState): ComposerVideo =
    when (state) {
        VideoUploadState.CheckingLimits ->
            copy(stage = ComposerVideoStage.CheckingLimits, error = null, embed = null)

        is VideoUploadState.Compressing -> copy(stage = ComposerVideoStage.Compressing, error = null)
        is VideoUploadState.Uploading -> copy(stage = ComposerVideoStage.Uploading, error = null)
        is VideoUploadState.Processing -> copy(stage = ComposerVideoStage.Processing, error = null)

        is VideoUploadState.Ready ->
            copy(
                stage = ComposerVideoStage.Ready,
                error = null,
                embed =
                    ComposerVideoEmbed(
                        blob = state.blob,
                        alt = alt,
                        aspectRatio = state.aspectRatio,
                    ),
            )

        is VideoUploadState.Failed ->
            copy(stage = ComposerVideoStage.Failed, error = state.error, embed = null)
    }

/**
 * The finished blob to attach to a post, or `null` if the upload has not
 * reached a terminal success.
 *
 * Returning null rather than throwing keeps the submit path total: the gate
 * already refuses to submit while an upload is in flight, so this is the
 * second of two independent guards against publishing a post whose video was
 * silently dropped.
 *
 * Alt text is applied here rather than when the embed was built, so editing it
 * after the upload finishes still takes effect.
 */
internal fun ComposerVideo.readyEmbed(): ComposerVideoEmbed? = embed?.copy(alt = alt)

/**
 * The stage's completion fraction, or `null` for stages that have none.
 *
 * Normalized here because the pipeline's state types deliberately do not
 * enforce the range — crashing an in-flight upload over a cosmetic value is
 * worse than a clamped bar — so unnormalized values are representable by
 * construction and the last step before a rendering primitive has to handle
 * them. NaN needs the explicit branch inside `asUploadProgress`:
 * `NaN.coerceIn(0f, 1f)` returns NaN.
 */
internal fun VideoUploadState.stageProgress(): Float? =
    when (this) {
        is VideoUploadState.Compressing -> progress.asUploadProgress()
        is VideoUploadState.Uploading -> progress.asUploadProgress()
        is VideoUploadState.Processing -> progress.asUploadProgress()
        else -> null
    }
