package net.kikin.nubecita.core.video

/*
 * Android rejects Picture-in-Picture aspect ratios outside roughly
 * [1:2.39, 2.39:1] — PictureInPictureParams.Builder.setAspectRatio throws
 * IllegalArgumentException for anything wider or taller. Video can legitimately
 * report extreme ratios (ultra-wide clips, vertical phone video), so the raw
 * decoded ratio must be clamped before it reaches the builder.
 */

/** Widest PiP ratio Android accepts (~2.39:1, cinematic scope). */
public const val MAX_PIP_ASPECT_RATIO: Float = 2.39f

/** Tallest PiP ratio Android accepts (~1:2.39). */
public const val MIN_PIP_ASPECT_RATIO: Float = 1f / 2.39f

/** 16:9 — used when the decoded ratio is unknown or nonsensical. */
public const val DEFAULT_PIP_ASPECT_RATIO: Float = 16f / 9f

/**
 * Coerce a decoded video aspect ratio (`width / height`) into the range
 * Android's PiP API accepts. A non-finite or non-positive input (unknown ratio,
 * not-yet-rendered frame) falls back to [DEFAULT_PIP_ASPECT_RATIO].
 */
public fun clampPipAspectRatio(ratio: Float): Float =
    if (!ratio.isFinite() || ratio <= 0f) {
        DEFAULT_PIP_ASPECT_RATIO
    } else {
        ratio.coerceIn(MIN_PIP_ASPECT_RATIO, MAX_PIP_ASPECT_RATIO)
    }
