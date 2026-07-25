package net.kikin.nubecita.core.videoupload

import io.github.kikin81.atproto.app.bsky.embed.AspectRatio
import io.github.kikin81.atproto.runtime.Blob

/**
 * Clamp a reported fraction into the `0f..1f` that every `progress` field on
 * this hierarchy promises.
 *
 * Producers MUST route through this. `Transformer` and Ktor's `onUpload` both
 * derive fractions from integer counters, which can land marginally outside the
 * range, and a video whose duration metadata is unreadable can produce `NaN`.
 * None of that is worth crashing an upload over — a progress bar is cosmetic,
 * and the pipeline's correctness does not depend on it.
 *
 * `NaN` maps to `0f` rather than being clamped: `Float.NaN.coerceIn(0f, 1f)`
 * returns `NaN` (every NaN comparison is false), so coercion alone would let it
 * through to the UI.
 */
internal fun Float.asUploadProgress(): Float = if (isNaN()) 0f else coerceIn(0f, 1f)

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
 *
 * Every `progress` field is in `0f..1f`. The constructors do not enforce it —
 * crashing an in-flight upload over a cosmetic value would be a worse outcome
 * than a clamped bar — so producers normalize through [asUploadProgress].
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
    ) : VideoUploadState

    /**
     * Transmitting the compressed bytes to the video service.
     *
     * @property progress fraction of bytes sent, in `0f..1f`.
     */
    data class Uploading(
        val progress: Float,
    ) : VideoUploadState

    /**
     * Uploaded; waiting on the server-side transcode job.
     *
     * @property progress the job's self-reported progress, in `0f..1f`.
     */
    data class Processing(
        val progress: Float,
    ) : VideoUploadState

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
