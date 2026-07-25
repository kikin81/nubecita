package net.kikin.nubecita.core.videoupload

import io.github.kikin81.atproto.app.bsky.embed.AspectRatio
import io.github.kikin81.atproto.runtime.Blob

/** Valid range for every `progress` value on this hierarchy. */
private val PROGRESS_RANGE = 0f..1f

/**
 * One observable step of the video publishing pipeline.
 *
 * Emitted in a strictly non-decreasing sequence terminating in exactly one of
 * [Ready] or [Failed]. Consumers see a linear progression and never learn that
 * the pipeline spans three hosts and two auth schemes.
 *
 * The stage order is load-bearing rather than cosmetic: [CheckingLimits] runs
 * **before** [Compressing] because transcoding is the most expensive thing this
 * app does to the battery and thermal budget, and both real rejection causes
 * (unverified account email, exhausted daily quota) are knowable up front.
 */
sealed interface VideoUploadState {
    /**
     * True for the two states after which the flow completes and no further
     * state is emitted. Exactly one terminal state occurs per upload.
     */
    val isTerminal: Boolean
        get() = this is Ready || this is Failed

    /** Asking the service whether this account may upload video at all. */
    data object CheckingLimits : VideoUploadState

    /**
     * Re-encoding the source clip to fit the service's size cap.
     *
     * @property progress fraction of the transcode completed, in `0f..1f`.
     */
    data class Compressing(
        val progress: Float,
    ) : VideoUploadState {
        init {
            require(progress in PROGRESS_RANGE) { "progress must be in 0f..1f, was $progress" }
        }
    }

    /**
     * Transmitting the compressed bytes to the video service.
     *
     * @property progress fraction of bytes sent, in `0f..1f`.
     */
    data class Uploading(
        val progress: Float,
    ) : VideoUploadState {
        init {
            require(progress in PROGRESS_RANGE) { "progress must be in 0f..1f, was $progress" }
        }
    }

    /**
     * Uploaded; waiting on the server-side transcode job.
     *
     * @property progress the job's self-reported progress, in `0f..1f`.
     */
    data class Processing(
        val progress: Float,
    ) : VideoUploadState {
        init {
            require(progress in PROGRESS_RANGE) { "progress must be in 0f..1f, was $progress" }
        }
    }

    /**
     * Terminal success. Everything needed to write `app.bsky.embed.video`.
     *
     * [aspectRatio] is rotation-corrected: a portrait recording is commonly
     * stored as landscape frames plus a 90° rotation flag, and this value is
     * published data that every AT Protocol client renders from — not a local
     * hint — so reporting the unrotated dimensions would letterbox the video
     * everywhere, not just here.
     */
    data class Ready(
        val blob: Blob,
        val aspectRatio: AspectRatio,
    ) : VideoUploadState

    /** Terminal failure. See [VideoUploadError] for which failures are retryable. */
    data class Failed(
        val error: VideoUploadError,
    ) : VideoUploadState
}
