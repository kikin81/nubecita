package net.kikin.nubecita.core.videoupload.internal

import io.github.kikin81.atproto.app.bsky.video.VideoService
import io.github.kikin81.atproto.runtime.XrpcClient
import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.core.videoupload.VideoUploadError
import timber.log.Timber

/** Outcome of asking the service whether this account may upload video. */
internal sealed interface UploadLimitsVerdict {
    /** Proceed. */
    data object Permitted : UploadLimitsVerdict

    /** Stop, with the reason to surface. */
    data class Rejected(
        val error: VideoUploadError,
    ) : UploadLimitsVerdict
}

/**
 * Asks `app.bsky.video.getUploadLimits` whether the account may upload.
 *
 * This runs **before** compression, and that ordering is the point rather than
 * an implementation detail. Transcoding is the most expensive thing this app
 * does to the battery and thermal budget, and both real rejection causes — an
 * unverified account email and an exhausted daily quota — are knowable up
 * front. Re-encoding a three-minute clip and only then learning the account
 * cannot post video would be a straightforward defect.
 */
internal class UploadLimitsProbe(
    private val clientFactory: VideoServiceClientFactory,
) {
    suspend fun check(): UploadLimitsVerdict =
        try {
            VideoService(client()).getUploadLimits().let { limits ->
                if (limits.canUpload) {
                    Timber.tag(TAG).d(
                        "getUploadLimits ok — remainingVideos=%s remainingBytes=%s",
                        limits.remainingDailyVideos,
                        limits.remainingDailyBytes,
                    )
                    UploadLimitsVerdict.Permitted
                } else {
                    // The server's own text, verbatim. The two real causes
                    // are an unverified email and an exhausted quota, and
                    // Bluesky phrases those better than a guess would —
                    // and will keep phrasing new ones we do not know about.
                    // WARN, not INFO: CrashlyticsTree drops anything below
                    // WARN, so this was reaching nothing. It is the reason a
                    // user cannot upload, worth having as context on whatever
                    // fires next — but not ERROR, since a server policy answer
                    // is not our defect.
                    Timber.tag(TAG).w("getUploadLimits refused: %s", limits.error ?: limits.message)
                    UploadLimitsVerdict.Rejected(
                        VideoUploadError.NotPermitted(limits.message ?: limits.error),
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            // Rethrow ahead of the generic branch. runCatching swallowed this,
            // turning "the user removed the video" into a Network error.
            throw cancellation
        } catch (cause: Exception) {
            Timber.tag(TAG).w(cause, "getUploadLimits failed")
            UploadLimitsVerdict.Rejected(VideoUploadError.Network(cause.message))
        }

    private fun client(): XrpcClient = clientFactory.create(GET_UPLOAD_LIMITS_LXM)

    private companion object {
        const val TAG = "VideoUpload"
        const val GET_UPLOAD_LIMITS_LXM = "app.bsky.video.getUploadLimits"
    }
}
