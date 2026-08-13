package net.kikin.nubecita.feature.videoplayer.impl

import androidx.media3.common.PlaybackException
import java.io.IOException

/**
 * UI-resolvable error categories surfaced by the fullscreen player.
 *
 * - [Network]: post-resolution failure (atproto getPosts) or HLS playlist
 *   network error from the underlying ExoPlayer.
 * - [Decode]: ExoPlayer rendering failure (unsupported codec, malformed
 *   segment). Distinct from Network because Retry won't help.
 * - [Unknown]: anything else (server 5xx, decode failure during resolution,
 *   unexpected throwable).
 */
internal sealed interface VideoPlayerError {
    data object Network : VideoPlayerError

    data object Decode : VideoPlayerError

    /**
     * Anything not classified above.
     *
     * [errorCodeName] carries Media3's symbolic error-code name when the cause
     * was a [PlaybackException], and is null otherwise. It exists because this
     * bucket used to keep only [cause] — so a real bug (nubecita-o91i, videos
     * on quote-posts never resolving) surfaced as an unadorned "Something went
     * wrong", and pinning it down took a device, a deep link and a
     * PID-filtered logcat. The numeric code is the single most diagnostic fact
     * available at this boundary; dropping it is what made the failure opaque.
     */
    data class Unknown(
        val cause: String?,
        val errorCodeName: String? = null,
    ) : VideoPlayerError
}

internal fun Throwable.toVideoPlayerError(): VideoPlayerError =
    when (this) {
        is IOException -> VideoPlayerError.Network
        is PlaybackException ->
            when (errorCode) {
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                -> VideoPlayerError.Decode
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                -> VideoPlayerError.Network
                else ->
                    VideoPlayerError.Unknown(
                        cause = message,
                        errorCodeName = PlaybackException.getErrorCodeName(errorCode),
                    )
            }
        else -> VideoPlayerError.Unknown(cause = message)
    }
