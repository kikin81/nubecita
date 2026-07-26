package net.kikin.nubecita.core.videoupload

/**
 * Service and encoder limits for a Bluesky video upload.
 *
 * These are **observed**, not lexicon-declared. `app.bsky.embed.video`'s own
 * KDoc records that the size cap already moved once ("May be up to 100mb,
 * formerly limited to 50mb"), so assume they will move again and keep them in
 * one place rather than scattered through the pipeline.
 */
internal object VideoUploadLimits {
    /** Hard cap the video service enforces on an uploaded file. */
    const val MAX_UPLOAD_BYTES: Long = 100L * 1024 * 1024

    /**
     * Bitrate target budget, deliberately under [MAX_UPLOAD_BYTES].
     *
     * The encoder hits a target rather than a ceiling, so a clip encoded to
     * exactly the cap routinely lands a few percent over it. The margin absorbs
     * that; the post-encode check in [VideoCompressor] is what actually
     * enforces the bound.
     */
    const val SIZE_BUDGET_BYTES: Long = 95L * 1024 * 1024

    /**
     * Bitrate used when the duration-derived value would be higher, and when
     * the duration cannot be read at all.
     *
     * 6 Mbps is a reasonable 1080p H.264 target — visually clean for typical
     * phone capture without spending the whole budget on a short clip.
     */
    const val DEFAULT_BITRATE_BPS: Long = 6_000_000

    /** Longest output edge. Downscaling beyond 1080p buys little at these bitrates. */
    const val MAX_DIMENSION_PX: Int = 1080

    /**
     * Longest clip the service accepts.
     *
     * **Not confirmed against a live account.** The commonly cited figure is
     * three minutes but it is absent from the lexicon, so this is a
     * conservative local gate; the authoritative rejection still arrives as
     * [VideoUploadError.NotPermitted] carrying the server's own message.
     * Task 7.2 replaces this with a measured value.
     */
    const val MAX_DURATION_MS: Long = 3 * 60 * 1000L
}
