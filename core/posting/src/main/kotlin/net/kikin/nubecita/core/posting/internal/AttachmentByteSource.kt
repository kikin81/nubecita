package net.kikin.nubecita.core.posting.internal

import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads bytes for a [Uri]-backed [net.kikin.nubecita.core.posting.ComposerAttachment]
 * at upload time. Exists as an interface (not a direct
 * `ContentResolver` injection in [DefaultPostingRepository]) because:
 *
 * 1. `ContentResolver` is awkward to mock cleanly in unit tests
 *    (requires a stubbed `Context` or `mockk` of system internals).
 * 2. Hiding the read behind a small interface gives the test a
 *    trivial fake (a `Map<Uri, ByteArray>`-backed implementation) and
 *    keeps the repository's tests focused on the orchestration logic
 *    (parallel uploads → record creation) rather than I/O plumbing.
 *
 * Single method, no streaming variant — full-byte reads are fine for
 * images (typically tens of KB to a few MB). Video uploads, when they
 * land in a future epic, will introduce a streaming overload here
 * alongside the SDK's `ByteReadChannel` overload (tracked upstream as
 * `kikinlex-177`).
 */
internal interface AttachmentByteSource {
    /**
     * @throws java.io.FileNotFoundException if the URI cannot be opened
     *   (e.g., revoked permission grant, deleted source). Callers map
     *   this to `ComposerError.UploadFailed`.
     */
    suspend fun read(uri: Uri): ByteArray
}

/**
 * Production implementation backed by the application
 * [ContentResolver]. Used for `content://` URIs returned by
 * `ActivityResultContracts.PickVisualMedia`.
 */
internal class ContentResolverAttachmentByteSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: android.content.Context,
    ) : AttachmentByteSource {
        override suspend fun read(uri: Uri): ByteArray =
            requireNotNull(context.contentResolver.openInputStream(uri)) {
                // Redacted message — content URIs (e.g. `content://media/external/.../12345`)
                // can carry user-identifying segments and may end up in
                // Crashlytics or logcat if this exception escapes. The
                // URI scheme alone is sufficient diagnostic signal; the
                // attachment index is added by the caller in
                // ComposerError.UploadFailed for traceability.
                "ContentResolver returned null InputStream for ${uri.scheme}://[redacted]"
            }.use { it.readBytes() }
    }
