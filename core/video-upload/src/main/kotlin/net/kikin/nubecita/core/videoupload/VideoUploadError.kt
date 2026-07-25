package net.kikin.nubecita.core.videoupload

/**
 * Why a video upload stopped.
 *
 * Variants are distinguished so a caller can pick an actionable message and
 * decide retryability **without inspecting strings**. A quota rejection and a
 * dropped socket both end the pipeline, but only one of them is worth a retry
 * button, and only one of them can be fixed by the user.
 */
sealed interface VideoUploadError {
    /**
     * The account may not upload video right now — `getUploadLimits` returned
     * `canUpload = false`.
     *
     * [message] is the server's own text, passed through verbatim rather than
     * re-worded locally: the two real causes are an unverified account email
     * and an exhausted daily quota, and Bluesky phrases those better than a
     * guess at the cause would. Null when the server supplied none.
     *
     * Not retryable within the session.
     */
    data class NotPermitted(
        val message: String?,
    ) : VideoUploadError

    /**
     * The source clip is longer than the service accepts.
     *
     * Detected locally from the source's duration metadata, before any
     * transcoding work — re-encoding a clip that will be rejected on arrival
     * wastes the most expensive stage in the pipeline.
     */
    data class TooLong(
        val durationMs: Long,
        val maxDurationMs: Long,
    ) : VideoUploadError

    /** The Media3 transcode failed. Device- and codec-specific; worth a retry. */
    data class CompressionFailed(
        val message: String?,
    ) : VideoUploadError

    /** The upload request reached the video service and was rejected. */
    data class UploadFailed(
        val statusCode: Int?,
        val message: String?,
    ) : VideoUploadError

    /**
     * The upload landed but server-side processing failed.
     *
     * [message] carries the job's `error` / `message` field.
     */
    data class ProcessingFailed(
        val message: String?,
    ) : VideoUploadError

    /** Transport failure at any stage — no response, or the connection dropped. Retryable. */
    data class Network(
        val message: String?,
    ) : VideoUploadError
}
