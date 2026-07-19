@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video.playback

import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.VideoPlaybackError
import net.kikin.nubecita.core.analytics.VideoPlaybackStats
import net.kikin.nubecita.core.analytics.VideoSurface

/**
 * Attach playback-quality + error analytics to this [ExoPlayer], sourced from
 * Media3 analytics listeners (epic nubecita-zdv8 Slice 5b):
 *
 * - a [PlaybackStatsListener] reports one [VideoPlaybackStats] per finished
 *   playback session (join time ≈ time-to-first-frame, rebuffer count/time,
 *   play time) — sessions that never played (a prewarmed player released
 *   before promotion) are skipped;
 * - a fatal player error logs a [VideoPlaybackError] with its Media3 code.
 *
 * Both listeners are cleared when the player is released, so callers need no
 * explicit teardown. [analytics] defaults are the caller's concern — pass the
 * injected [AnalyticsClient] (a no-op in bench/keyless builds).
 */
internal fun ExoPlayer.installPlaybackAnalytics(
    analytics: AnalyticsClient,
    surface: VideoSurface,
) {
    // keepHistory = false: we only need the finished-session summary, not the
    // full per-event history (which would grow unbounded across swipes).
    addAnalyticsListener(
        PlaybackStatsListener(false) { _, stats ->
            if (stats.totalPlayTimeMs <= 0L) return@PlaybackStatsListener
            analytics.log(
                VideoPlaybackStats(
                    surface = surface,
                    timeToFirstFrameMs = stats.meanJoinTimeMs,
                    rebufferCount = stats.totalRebufferCount.toLong(),
                    rebufferMs = stats.totalRebufferTimeMs,
                    playMs = stats.totalPlayTimeMs,
                ),
            )
        },
    )
    addAnalyticsListener(
        object : AnalyticsListener {
            override fun onPlayerError(
                eventTime: AnalyticsListener.EventTime,
                error: PlaybackException,
            ) {
                analytics.log(VideoPlaybackError(surface, error.errorCode.toLong()))
            }
        },
    )
}
