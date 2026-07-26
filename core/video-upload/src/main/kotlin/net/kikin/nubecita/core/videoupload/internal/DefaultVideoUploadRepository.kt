package net.kikin.nubecita.core.videoupload.internal

import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.videoupload.VideoSourceProbe
import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.core.videoupload.VideoUploadLimits.MAX_DURATION_MS
import net.kikin.nubecita.core.videoupload.VideoUploadRepository
import net.kikin.nubecita.core.videoupload.VideoUploadState
import net.kikin.nubecita.core.videoupload.deriveAspectRatio
import timber.log.Timber
import javax.inject.Inject

/**
 * The pipeline, assembled.
 *
 * Stage order is normative, not incidental: limits → duration gate →
 * compress → upload → poll. The two cheap rejections come first so an account
 * that cannot post video, or a clip that is too long, never reaches the
 * transcoder — the most expensive thing this app does to battery and thermal
 * budget.
 *
 * The flow is cold. Collection starts the work and cancelling the collector
 * aborts it, which is what makes "remove the video" and "discard the composer"
 * work without a bespoke cancel path.
 */
internal class DefaultVideoUploadRepository
    @Inject
    constructor(
        private val limitsProbe: UploadLimitsProbe,
        private val sourceProbe: VideoSourceProbe,
        private val compressor: VideoCompressor,
        private val uploader: VideoUploader,
        private val poller: JobStatusPoller,
        private val sessionStateProvider: SessionStateProvider,
    ) : VideoUploadRepository {
        override fun upload(uri: Uri): Flow<VideoUploadState> =
            channelFlow {
                send(VideoUploadState.CheckingLimits)

                when (val verdict = limitsProbe.check()) {
                    is UploadLimitsVerdict.Rejected -> {
                        send(VideoUploadState.Failed(verdict.error))
                        return@channelFlow
                    }

                    UploadLimitsVerdict.Permitted -> Unit
                }

                val metadata = sourceProbe.probe(uri)

                // Duration gate before the transcode, same reasoning as the
                // limits probe. An unreadable duration is NOT rejected — the
                // bitrate path already falls back for it, and the post-encode
                // size check catches an over-large result.
                val durationMs = metadata.durationMs
                if (durationMs != null && durationMs > MAX_DURATION_MS) {
                    send(
                        VideoUploadState.Failed(
                            VideoUploadError.TooLong(durationMs, MAX_DURATION_MS),
                        ),
                    )
                    return@channelFlow
                }

                send(VideoUploadState.Compressing(0f))
                val compressed =
                    when (val result = compressor.compress(uri) { trySend(VideoUploadState.Compressing(it)) }) {
                        is CompressionResult.Failure -> {
                            send(VideoUploadState.Failed(result.error))
                            return@channelFlow
                        }

                        is CompressionResult.Success -> result.file
                    }

                try {
                    send(VideoUploadState.Uploading(0f))
                    val accepted =
                        when (val outcome = uploader.upload(compressed, viewerDid()) { trySend(VideoUploadState.Uploading(it)) }) {
                            is UploadOutcome.Failed -> {
                                send(VideoUploadState.Failed(outcome.error))
                                return@channelFlow
                            }

                            is UploadOutcome.Accepted -> outcome
                        }

                    send(VideoUploadState.Processing(0f))
                    when (val outcome = poller.awaitBlob(accepted.jobId) { trySend(VideoUploadState.Processing(it)) }) {
                        is JobOutcome.Failed -> send(VideoUploadState.Failed(outcome.error))
                        is JobOutcome.Ready ->
                            send(
                                VideoUploadState.Ready(
                                    blob = outcome.blob,
                                    aspectRatio =
                                        deriveAspectRatio(
                                            metadata.widthPx,
                                            metadata.heightPx,
                                            metadata.rotationDegrees,
                                        ),
                                ),
                            )
                    }
                } finally {
                    // The compressed file is scratch. Deleting it here covers
                    // success, failure and cancellation alike — the caller
                    // never has to know a temp file existed.
                    if (compressed.delete()) {
                        Timber.tag(TAG).d("removed scratch upload file")
                    }
                }
            }.buffer(
                // Progress is lossy by nature: a dropped intermediate frame is
                // invisible, but a suspended producer would stall the transcode
                // or the upload to wait for the UI. Terminal states still
                // arrive because the newest value always wins.
                capacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        private fun viewerDid(): String =
            (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                ?: throw NoSessionException()

        private companion object {
            const val TAG = "VideoUpload"
        }
    }
