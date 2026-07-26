@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.videoupload.internal

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.videoupload.VideoSourceProbe
import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.core.videoupload.VideoUploadLimits.MAX_DIMENSION_PX
import net.kikin.nubecita.core.videoupload.asUploadProgress
import net.kikin.nubecita.core.videoupload.targetBitrateBps
import timber.log.Timber
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Re-encodes a clip with Media3 [Transformer] so it fits the service cap.
 *
 * Without this the feature would reject most real recordings: 1080p30 phone
 * capture runs around 20 Mbps, so three minutes is roughly 450 MB against a
 * 100 MB ceiling.
 *
 * [Transformer] requires a `Looper`, so it is started on the main dispatcher.
 * The work itself happens on its own threads; only the start/cancel calls and
 * the progress poll need the main thread.
 */
internal class Media3VideoCompressor(
    private val context: Context,
    private val sourceProbe: VideoSourceProbe,
    private val outputDir: File,
) : VideoCompressor {
    override suspend fun compress(
        input: Uri,
        onProgress: (Float) -> Unit,
    ): CompressionResult {
        val durationMs = sourceProbe.probe(input).durationMs
        val bitrate = targetBitrateBps(durationMs)
        // UUID, not input.hashCode(): distinct URIs can collide, and a retry
        // or a concurrent compose would otherwise reuse the same path. The
        // caller owns deleting a successful output once it is uploaded.
        val output = File(outputDir, "upload-${UUID.randomUUID()}.mp4")

        Timber.tag(TAG).d("compressing durationMs=%s targetBitrate=%d", durationMs, bitrate)

        val result =
            try {
                runTransform(input, output, bitrate, onProgress)
            } catch (cancellation: CancellationException) {
                // Rethrow before the generic branch. runCatching would have
                // swallowed this and reported a compression failure, breaking
                // the cancel-on-remove contract the composer depends on.
                output.delete()
                throw cancellation
            } catch (cause: Exception) {
                output.delete()
                Timber.tag(TAG).w(cause, "transcode failed")
                return CompressionResult.Failure(VideoUploadError.CompressionFailed(cause.message))
            }

        if (result != null) {
            output.delete()
            return CompressionResult.Failure(result)
        }

        // The bound is enforced here, not assumed. targetBitrateBps falls back
        // to a default when the duration is unreadable, and an encoder targets
        // a rate rather than obeying a ceiling — so neither guarantees the
        // result. Checking the produced file does.
        verifyWithinCap(output.length())?.let { oversized ->
            output.delete()
            Timber.tag(TAG).w("encoded file over cap: %d bytes", output.length())
            return CompressionResult.Failure(oversized)
        }

        return CompressionResult.Success(output)
    }

    /** Returns null on success, or the error to report. */
    private suspend fun runTransform(
        input: Uri,
        output: File,
        bitrateBps: Long,
        onProgress: (Float) -> Unit,
    ): VideoUploadError? =
        withContext(Dispatchers.Main) {
            lateinit var transformer: Transformer
            withProgressPolling(
                pollIntervalMs = PROGRESS_POLL_MS,
                poll = { reportProgress(transformer, onProgress) },
            ) {
                suspendCancellableCoroutine { continuation ->
                    transformer = buildTransformer(bitrateBps, continuation)
                    val item =
                        EditedMediaItem
                            .Builder(MediaItem.fromUri(input))
                            .setEffects(
                                Effects(
                                    // audioProcessors =
                                    emptyList(),
                                    // Cap the longest edge rather than forcing a
                                    // resolution: upscaling a 720p source to 1080p
                                    // would spend bitrate inventing detail.
                                    listOf(Presentation.createForShortSide(MAX_DIMENSION_PX)),
                                ),
                            ).build()

                    continuation.invokeOnCancellation {
                        // Cancel must also happen on the Looper thread.
                        CoroutineScope(Dispatchers.Main).launch { transformer.cancel() }
                    }

                    transformer.start(item, output.absolutePath)
                }
            }
        }

    private fun buildTransformer(
        bitrateBps: Long,
        continuation: CancellableContinuation<VideoUploadError?>,
    ): Transformer =
        Transformer
            .Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory
                    .Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings
                            .Builder()
                            .setBitrate(bitrateBps.toInt())
                            .build(),
                    ).build(),
            ).addListener(
                object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        result: ExportResult,
                    ) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        // Device- and codec-specific. Log the configuration so a
                        // field report is diagnosable rather than just "it failed".
                        Timber.tag(TAG).w(
                            exception,
                            "export failed errorCode=%d bitrate=%d",
                            exception.errorCode,
                            bitrateBps,
                        )
                        if (continuation.isActive) {
                            continuation.resume(VideoUploadError.CompressionFailed(exception.message))
                        }
                    }
                },
            ).build()

    /**
     * One progress sample. [Transformer] exposes progress only by query, so
     * [withProgressPolling] calls this on an interval and owns the loop's
     * lifecycle — including stopping it when the export finishes.
     */
    private fun reportProgress(
        transformer: Transformer,
        onProgress: (Float) -> Unit,
    ) {
        val holder = ProgressHolder()
        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            onProgress((holder.progress / 100f).asUploadProgress())
        }
    }

    private companion object {
        const val TAG = "VideoUpload"
        const val PROGRESS_POLL_MS = 250L
    }
}
