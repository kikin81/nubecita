package net.kikin.nubecita.core.videoupload

import net.kikin.nubecita.core.videoupload.VideoUploadLimits.DEFAULT_BITRATE_BPS
import net.kikin.nubecita.core.videoupload.VideoUploadLimits.SIZE_BUDGET_BYTES

/**
 * Pick a target video bitrate that keeps the encoded file inside the service's
 * size cap.
 *
 * The bitrate is derived from **duration** rather than fixed. Any fixed value
 * high enough to look acceptable on a short clip overflows the cap near the
 * duration limit: 1080p30 phone capture runs around 20 Mbps, so three minutes
 * is roughly 450 MB against a 100 MB ceiling. Scaling by duration makes the
 * bound structural instead of hoped-for.
 *
 * [durationMs] is guarded rather than trusted. `MediaMetadataRetriever` returns
 * null for a corrupt container, and dividing by zero would throw. A
 * non-positive or unknown duration falls back to [defaultBitrateBps] — which
 * necessarily abandons the computed bound, so the caller MUST still verify the
 * encoded file against [VideoUploadLimits.MAX_UPLOAD_BYTES] afterwards. That
 * check is what turns the bound from computed into enforced, and it is why this
 * function can afford to guess here.
 */
internal fun targetBitrateBps(
    durationMs: Long?,
    defaultBitrateBps: Long = DEFAULT_BITRATE_BPS,
    sizeBudgetBytes: Long = SIZE_BUDGET_BYTES,
): Long {
    if (durationMs == null || durationMs <= 0L) return defaultBitrateBps

    // Integer math throughout: durationMs is already the numerator's scale, so
    // multiply by 1000 rather than converting to fractional seconds.
    val budgetBits = sizeBudgetBytes * 8
    val durationDerived = budgetBits * 1000 / durationMs

    return minOf(defaultBitrateBps, durationDerived).coerceAtLeast(MIN_BITRATE_BPS)
}

/**
 * Floor for the duration-derived bitrate.
 *
 * A clip long enough to drive the computed value below this would encode to
 * something unwatchable. Clamping here means such a clip produces a file that
 * fails the post-encode size check with a clear [VideoUploadError.CompressionFailed]
 * rather than silently uploading a smear.
 */
internal const val MIN_BITRATE_BPS: Long = 500_000
