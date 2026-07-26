package net.kikin.nubecita.core.videoupload.internal

import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.core.videoupload.VideoUploadLimits.MAX_UPLOAD_BYTES
import java.io.File

/** Result of transcoding a clip for upload. */
internal sealed interface CompressionResult {
    data class Success(
        val file: File,
    ) : CompressionResult

    data class Failure(
        val error: VideoUploadError,
    ) : CompressionResult
}

/**
 * Re-encodes a clip to fit the service's size cap.
 *
 * Progress is reported through [onProgress] as a `0f..1f` fraction that the
 * caller wraps in `Compressing`.
 */
internal interface VideoCompressor {
    suspend fun compress(
        input: android.net.Uri,
        onProgress: (Float) -> Unit,
    ): CompressionResult
}

/**
 * Verify an encoded file against the hard service cap.
 *
 * This is what turns the size bound from *computed* into *enforced*.
 * [targetBitrateBps] derives a rate from duration, but an unreadable duration
 * makes it fall back to a default, and an encoder targets a rate rather than
 * obeying a ceiling — so neither path actually guarantees the result. Checking
 * the produced file does.
 *
 * Failing here costs one wasted transcode. Not failing here costs a transcode
 * *and* a full upload of a file the service will refuse, on a connection the
 * user is watching.
 */
internal fun verifyWithinCap(
    sizeBytes: Long,
    maxBytes: Long = MAX_UPLOAD_BYTES,
): VideoUploadError? =
    if (sizeBytes <= maxBytes) {
        null
    } else {
        VideoUploadError.CompressionFailed(
            "Encoded video is ${sizeBytes / 1024 / 1024}MB ($sizeBytes bytes), " +
                "over the ${maxBytes / 1024 / 1024}MB limit ($maxBytes bytes)",
        )
    }
