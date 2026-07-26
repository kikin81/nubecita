package net.kikin.nubecita.core.videoupload.internal

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.videoupload.VideoSourceMetadata
import net.kikin.nubecita.core.videoupload.VideoSourceProbe
import timber.log.Timber

/**
 * Reads clip metadata with [MediaMetadataRetriever].
 *
 * Deliberately thin: it does nothing but turn platform strings into nullable
 * numbers. Every decision that depends on them — the rotation swap, the
 * bitrate, the duration gate — lives in pure functions that are unit-tested
 * without a device.
 *
 * Every field is optional in practice. The retriever returns null for keys a
 * container does not carry and for files it cannot parse at all, and it throws
 * on some malformed inputs rather than returning null — so the whole read is
 * wrapped rather than trusted key by key.
 */
internal class MediaMetadataVideoSourceProbe(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : VideoSourceProbe {
    override suspend fun probe(uri: Uri): VideoSourceMetadata =
        withContext(ioDispatcher) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                VideoSourceMetadata(
                    widthPx = retriever.intOrNull(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                    heightPx = retriever.intOrNull(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                    rotationDegrees = retriever.intOrNull(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                    durationMs = retriever.longOrNull(MediaMetadataRetriever.METADATA_KEY_DURATION),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                // setDataSource(Context, Uri) declares only IllegalArgumentException
                // and SecurityException, but Kotlin does not enforce checked
                // exceptions, so an undeclared platform throw would escape a
                // narrower catch. Exception is deliberately broad — with
                // CancellationException rethrown above, since catching it here
                // would break the cancel-on-remove contract.
                // An all-null result is a valid answer here: the callers
                // already treat missing metadata as "omit the aspect ratio"
                // and "fall back to the default bitrate", so failing the whole
                // upload at this point would be stricter than necessary.
                Timber.tag(TAG).w(e, "could not read video metadata")
                VideoSourceMetadata(null, null, null, null)
            } finally {
                retriever.release()
            }
        }

    private fun MediaMetadataRetriever.intOrNull(key: Int): Int? = extractMetadata(key)?.toIntOrNull()

    private fun MediaMetadataRetriever.longOrNull(key: Int): Long? = extractMetadata(key)?.toLongOrNull()

    private companion object {
        const val TAG = "VideoUpload"
    }
}
