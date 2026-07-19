@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video.playback

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Short-video `LoadControl` buffer durations (ms). Defaults are tuned for long
 * video; a vertical short-video feed starts faster and rebuffers less with a
 * small, drip-fed buffer (Reddit ExoPlayer playbook — local digest
 * `docs/references/reddit-exoplayer-playbook.md`: >2s starts −17.7%, rebuffer
 * −4.8%). `MIN == MAX` is Reddit's drip-feed; a small `MIN < MAX` hysteresis gap
 * would trade a little rebuffering for fewer loader on/off cycles (lower CPU).
 * The exact values are a battery-vs-rebuffer decision validated in the perf pass
 * (epic nubecita-zdv8 Slice 5); these are the starting point.
 *
 * Invariant (enforced by `DefaultLoadControl`): `MIN >= BUFFER_FOR_PLAYBACK`,
 * `MIN >= BUFFER_FOR_PLAYBACK_AFTER_REBUFFER`, `MAX >= MIN`.
 */
public object VideoBufferConfig {
    public const val MIN_BUFFER_MS: Int = 20_000
    public const val MAX_BUFFER_MS: Int = 20_000
    public const val BUFFER_FOR_PLAYBACK_MS: Int = 1_000
    public const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS: Int = 1_000
}

/** Build the short-video-tuned [LoadControl] from [VideoBufferConfig]. */
public fun shortVideoLoadControl(): LoadControl =
    DefaultLoadControl
        .Builder()
        .setBufferDurationsMs(
            VideoBufferConfig.MIN_BUFFER_MS,
            VideoBufferConfig.MAX_BUFFER_MS,
            VideoBufferConfig.BUFFER_FOR_PLAYBACK_MS,
            VideoBufferConfig.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        ).build()
