package net.kikin.nubecita.core.posting.internal

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.MediaCopy
import net.kikin.nubecita.core.posting.SharedMediaStore
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android-backed [SharedMediaStore]. All the security-critical logic (the hard
 * byte ceiling, the magic-byte image sniff, the age-based sweep) lives in the
 * pure, unit-tested [MediaCopy]; this class is the thin `Context` /
 * `ContentResolver` / `Uri` glue around it, covered by instrumented tests.
 *
 * Every method runs on [dispatcher] and wraps its IO in `runCatching`, rethrowing
 * `CancellationException` so structured cancellation isn't swallowed (matching
 * `DefaultAuthRepository`); any other failure fails closed.
 */
@Singleton
internal class DefaultSharedMediaStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SharedMediaStore {
        private val dir: File
            get() = File(context.filesDir, SHARES_DIR)

        override suspend fun copyIn(source: Uri): Uri? =
            withContext(dispatcher) {
                runCatching {
                    // Confused-deputy guard: never read a content:// that points
                    // back at one of our own providers.
                    if (isOwnAuthority(source)) return@runCatching null
                    val resolver = context.contentResolver
                    // Cheap early reject on the declared type; the byte sniff below
                    // is the authoritative check (the sender can lie about the type).
                    val declared = resolver.getType(source)
                    if (declared != null && !declared.startsWith("image/")) return@runCatching null

                    dir.mkdirs()
                    val id = UUID.randomUUID().toString()
                    val temp = File(dir, "tmp_$id")
                    var renamed = false
                    try {
                        val copied =
                            resolver.openInputStream(source)?.use { input ->
                                MediaCopy.copyBounded(input, temp, MAX_IMAGE_BYTES)
                            } ?: false
                        if (!copied) return@runCatching null
                        val type = MediaCopy.sniff(readHeader(temp)) ?: return@runCatching null
                        val dest = File(dir, "$id.${type.extension}")
                        if (!temp.renameTo(dest)) return@runCatching null
                        renamed = true
                        Uri.fromFile(dest)
                    } finally {
                        // Always clean up the temp copy unless it was renamed into
                        // place — covers the reject paths AND copyBounded/readHeader/
                        // renameTo throwing (disk full, write error), which
                        // runCatching would otherwise swallow, leaking tmp_* files.
                        if (!renamed) temp.delete()
                    }
                }.getOrElse { if (it is CancellationException) throw it else null }
            }

        override suspend fun attachmentFor(sharedImageUri: String): ComposerAttachment? =
            withContext(dispatcher) {
                runCatching {
                    val uri = Uri.parse(sharedImageUri)
                    val file = uri.path?.let(::File) ?: return@runCatching null
                    if (!isOwnedCopy(file) || !file.exists()) return@runCatching null
                    val type =
                        MediaCopy.ImageType.entries
                            .firstOrNull { it.extension == file.extension.lowercase(Locale.ROOT) }
                            ?: return@runCatching null
                    ComposerAttachment(uri = uri, mimeType = type.mimeType)
                }.getOrElse { if (it is CancellationException) throw it else null }
            }

        override suspend fun delete(uri: Uri) {
            withContext(dispatcher) {
                runCatching {
                    val file = uri.path?.let(::File) ?: return@runCatching
                    // Only ever delete inside our own directory.
                    if (isOwnedCopy(file)) file.delete()
                }.getOrElse { if (it is CancellationException) throw it else Unit }
            }
        }

        override suspend fun sweepOrphans() {
            withContext(dispatcher) {
                runCatching {
                    MediaCopy.sweep(dir, now = System.currentTimeMillis(), maxAgeMillis = MAX_AGE_MILLIS)
                }.getOrElse { if (it is CancellationException) throw it else 0 }
            }
        }

        private fun isOwnAuthority(uri: Uri): Boolean {
            val authority = uri.authority ?: return false
            return authority == context.packageName || authority.startsWith("${context.packageName}.")
        }

        private fun isOwnedCopy(file: File): Boolean = file.parentFile?.canonicalPath == dir.canonicalPath

        private fun readHeader(file: File): ByteArray {
            val buffer = ByteArray(HEADER_SIZE)
            var offset = 0
            file.inputStream().use { input ->
                while (offset < HEADER_SIZE) {
                    val read = input.read(buffer, offset, HEADER_SIZE - offset)
                    if (read == -1) break
                    offset += read
                }
            }
            return if (offset == HEADER_SIZE) buffer else buffer.copyOf(offset)
        }

        private companion object {
            const val SHARES_DIR = "composer_shares"

            // Largest shared image we copy. Generous enough for an un-downscaled
            // phone photo (the upload path compresses afterwards), bounded so a
            // malicious/endless stream can't exhaust storage.
            const val MAX_IMAGE_BYTES = 25L * 1024 * 1024

            // Orphan retention: a copy older than this with no live composer is
            // swept at startup.
            const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

            // Enough leading bytes for every magic-byte signature (WEBP needs 12).
            const val HEADER_SIZE = 16
        }
    }
