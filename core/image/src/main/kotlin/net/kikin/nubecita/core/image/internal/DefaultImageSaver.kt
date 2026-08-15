package net.kikin.nubecita.core.image.internal

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.image.ImageRetrievalException
import net.kikin.nubecita.core.image.ImageSaveUnsupportedException
import net.kikin.nubecita.core.image.ImageSaver
import net.kikin.nubecita.core.image.ImageStorageException
import net.kikin.nubecita.core.image.SIGNATURE_LENGTH
import net.kikin.nubecita.core.image.SavedImageFormat
import net.kikin.nubecita.core.image.savedImageDisplayName
import net.kikin.nubecita.core.image.sniffImageFormat
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * [ImageSaver] backed by Coil's disk cache for reads and `MediaStore` for
 * writes.
 *
 * The bytes are copied straight from the cache file the viewer already
 * populated — never decoded and re-encoded — so the saved file is
 * byte-identical to what the server served and the save costs no network,
 * no decode, and no `Bitmap` allocation.
 */
internal class DefaultImageSaver
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val imageLoader: ImageLoader,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ImageSaver {
        override val isSupported: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        override suspend fun saveToGallery(url: String): Result<Uri> {
            // API 28 would need WRITE_EXTERNAL_STORAGE, which this app does not
            // declare: it would be the first dangerous permission on the Play
            // listing — seen by 100% of prospective users — to serve the 1.0%
            // (76 of 7,258 active users, 90d GA4) still on Android 9. See the
            // add-media-viewer-save-to-gallery design, decision D3, before
            // "fixing" this by adding the permission.
            if (!isSupported) return Result.failure(ImageSaveUnsupportedException())

            return withContext(dispatcher) {
                try {
                    // The snapshot stays open across the whole copy. It is
                    // Coil's read lock: closing it first and keeping only the
                    // path would let the eviction policy (or another request
                    // for the same key) delete or overwrite the file we are
                    // mid-way through reading.
                    val uri =
                        openSnapshotOrFetch(url).use { snapshot ->
                            val file = File(snapshot.data.toString())
                            if (!file.isFile || file.length() <= 0L) throw ImageRetrievalException(url)
                            writeToGallery(file)
                        }
                    Result.success(uri)
                } catch (cancellation: CancellationException) {
                    // Never fold cancellation into a failed Result — doing so
                    // reports "save failed" for a user who simply navigated
                    // away, and breaks cooperative cancellation for the caller.
                    throw cancellation
                } catch (failure: ImageRetrievalException) {
                    Timber.w("image save failed: could not retrieve bytes")
                    Result.failure(failure)
                } catch (failure: ImageStorageException) {
                    Timber.w("image save failed: could not write to the gallery")
                    Result.failure(failure)
                } catch (failure: Exception) {
                    // Backstop. resolver.insert sits outside writeToGallery's own
                    // try, and OEM ContentResolver implementations are a known
                    // source of surprises (SecurityException, SQLiteException).
                    // Without this the throw escapes into viewModelScope.launch
                    // and takes the app down on a save tap. Exception, not
                    // Throwable, so Errors still propagate — and the
                    // CancellationException arm above still wins by ordering.
                    Timber.w(failure, "image save failed: unexpected %s", failure.javaClass.name)
                    Result.failure(ImageStorageException("unexpected failure saving the image", failure))
                }
            }
        }

        /**
         * Opens the disk-cache snapshot for [url], fetching first on a miss.
         *
         * The caller owns the returned snapshot and MUST keep it open for the
         * duration of the read — see the note at the call site.
         */
        private suspend fun openSnapshotOrFetch(url: String): DiskCache.Snapshot {
            // Coil's diskCacheKey defaults to the data string, so the fullsize
            // URL is the snapshot key the viewer wrote under.
            val cache = imageLoader.diskCache ?: throw ImageRetrievalException(url)
            cache.openSnapshot(url)?.let { return it }
            fetchIntoCache(url)
            return cache.openSnapshot(url) ?: throw ImageRetrievalException(url)
        }

        /**
         * Populates the disk cache for [url].
         *
         * Only reached when the user presses save before the image finished
         * loading; the common path never gets here.
         */
        private suspend fun fetchIntoCache(url: String) {
            val result =
                try {
                    imageLoader.execute(ImageRequest.Builder(context).data(url).build())
                } catch (cancellation: CancellationException) {
                    // NOT runCatching: it catches Throwable, which includes
                    // CancellationException, and wrapping that into an
                    // ImageRetrievalException would both break cooperative
                    // cancellation and tell a user who merely navigated away
                    // that their download failed.
                    throw cancellation
                } catch (failure: Throwable) {
                    throw ImageRetrievalException(url, failure)
                }

            // Carry the real cause (timeout, 404, decode) rather than dropping it.
            // ImageResult is a plain interface, not a sealed type, so !is
            // SuccessResult does not smart-cast to ErrorResult — hence `as?`.
            if (result !is SuccessResult) {
                throw ImageRetrievalException(url, (result as? ErrorResult)?.throwable)
            }
        }

        /**
         * Copies [source] into the shared gallery and returns the new item's
         * `Uri`.
         *
         * Deliberately **not** a suspending function. Coroutine cancellation
         * only takes effect at suspension points, so keeping the whole
         * insert-write-publish sequence non-suspending means it either does not
         * start or runs to completion — the `catch` below is therefore
         * guaranteed to run and clean up, with no `NonCancellable` needed.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun writeToGallery(source: File): Uri {
            val format = sniffFormat(source)
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val pending =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, savedImageDisplayName(format, System.currentTimeMillis()))
                    put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_RELATIVE_PATH)
                    // Hide the row from the gallery until every byte has landed,
                    // so an interrupted save cannot surface as a truncated image.
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                    // No DESCRIPTION write: that column is declared readOnly and
                    // is derived by the provider from the file's EXIF
                    // ImageDescription tag, so a ContentValues write is silently
                    // discarded. Carrying alt text would mean injecting EXIF,
                    // which breaks the byte-identical copy above. Verified on
                    // device — the column reads back null.
                }

            val uri =
                resolver.insert(collection, pending)
                    ?: throw ImageStorageException("MediaStore refused to create an entry for the image")

            try {
                resolver.openOutputStream(uri).use { stream ->
                    stream ?: throw ImageStorageException("MediaStore returned no output stream")
                    source.inputStream().use { it.copyToStream(stream) }
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (failure: Throwable) {
                // A row left pending is invisible to the gallery AND unreachable
                // by the user, so failing without this would silently consume
                // storage they cannot reclaim. Delete before rethrowing.
                runCatching { resolver.delete(uri, null, null) }
                    .onFailure { Timber.w("failed to clean up a pending gallery entry after a failed save") }
                // Cleanup first, then let cancellation through untouched. Not
                // reachable today — nothing in this non-suspending body throws
                // CancellationException — but wrapping one into an
                // ImageStorageException would report a storage failure for a
                // cancelled save, so the guard travels with the catch.
                if (failure is CancellationException) throw failure
                throw if (failure is ImageStorageException) {
                    failure
                } else {
                    ImageStorageException("failed writing the image into the gallery", failure)
                }
            }
            return uri
        }

        /** Reads just enough of [source] to classify its format. */
        private fun sniffFormat(source: File): SavedImageFormat =
            try {
                source.inputStream().use { input ->
                    val header = ByteArray(SIGNATURE_LENGTH)
                    val read = input.read(header)
                    if (read <= 0) SavedImageFormat.Unknown else sniffImageFormat(header.copyOf(read))
                }
            } catch (failure: IOException) {
                throw ImageStorageException("could not read the cached image", failure)
            }

        private fun java.io.InputStream.copyToStream(out: OutputStream) {
            copyTo(out, DEFAULT_BUFFER_SIZE)
            out.flush()
        }

        private companion object {
            /**
             * Trailing slash is the canonical persisted form — `RELATIVE_PATH`'s
             * javadoc renders a file in `DCIM/Vacation` as `DCIM/Vacation/`. Writing
             * it this way makes the value read back identical to the value written,
             * so the instrumented test can assert equality rather than prefix-match.
             */
            val ALBUM_RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/Nubecita/"
        }
    }
