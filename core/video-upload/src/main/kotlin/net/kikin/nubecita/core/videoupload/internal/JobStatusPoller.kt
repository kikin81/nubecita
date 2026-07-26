package net.kikin.nubecita.core.videoupload.internal

import io.github.kikin81.atproto.app.bsky.video.GetJobStatusRequest
import io.github.kikin81.atproto.app.bsky.video.JobStatus
import io.github.kikin81.atproto.app.bsky.video.VideoService
import io.github.kikin81.atproto.runtime.Blob
import kotlinx.coroutines.delay
import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.core.videoupload.asUploadProgress
import timber.log.Timber

/** Where a poll left the job. */
internal sealed interface JobOutcome {
    data class Ready(
        val blob: Blob,
    ) : JobOutcome

    data class Failed(
        val error: VideoUploadError,
    ) : JobOutcome
}

/**
 * Interpret one `getJobStatus` response.
 *
 * Pure, so the state machine is testable without a server.
 *
 * **Unknown states mean "still running".** The lexicon says so explicitly:
 * *"All values not listed as a known value indicate that the job is in
 * process."* A strict enum would fail on any state Bluesky adds later, turning
 * a successful upload into a spurious error — so only an explicit failure
 * state fails, and anything unrecognised keeps polling.
 */
internal fun interpretJobStatus(status: JobStatus): JobOutcome? {
    val blob = status.blob
    if (blob != null) return JobOutcome.Ready(blob)

    return if (status.state.equals(STATE_FAILED, ignoreCase = true)) {
        JobOutcome.Failed(VideoUploadError.ProcessingFailed(status.error ?: status.message))
    } else {
        // Null means "keep polling" — including for JOB_STATE_COMPLETED
        // without a blob yet, and for any state string we do not recognise.
        null
    }
}

private const val STATE_FAILED = "JOB_STATE_FAILED"

/**
 * Polls `app.bsky.video.getJobStatus` until the server-side transcode produces
 * a blob.
 */
internal class JobStatusPoller(
    private val clientFactory: VideoServiceClientFactory,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    suspend fun awaitBlob(
        jobId: String,
        onProgress: (Float) -> Unit,
    ): JobOutcome {
        val service = VideoService(clientFactory.create())

        repeat(maxAttempts) { attempt ->
            val status =
                try {
                    service.getJobStatus(GetJobStatusRequest(jobId = jobId)).jobStatus
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (cause: Exception) {
                    Timber.tag(TAG).w(cause, "getJobStatus failed on attempt %d", attempt)
                    return JobOutcome.Failed(VideoUploadError.Network(cause.message))
                }

            status.progress?.let { onProgress((it / 100f).asUploadProgress()) }

            interpretJobStatus(status)?.let { return it }
            delay(pollIntervalMs)
        }

        // A job that never resolves is a failure, not an infinite wait. Without
        // this the composer's submit gate would stay disabled forever with no
        // explanation and no retry affordance.
        Timber.tag(TAG).w("job %s did not complete within %d attempts", jobId, maxAttempts)
        return JobOutcome.Failed(
            VideoUploadError.ProcessingFailed("Video processing did not finish in time"),
        )
    }

    private companion object {
        const val TAG = "VideoUpload"
        const val DEFAULT_POLL_INTERVAL_MS = 1_000L

        /** ~5 minutes at the default interval. */
        const val DEFAULT_MAX_ATTEMPTS = 300
    }
}
