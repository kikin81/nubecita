package net.kikin.nubecita.core.image

import android.net.Uri

/**
 * Reads bytes for a [Uri]-backed [PickedImage] at upload time. Exists
 * as an interface (not a direct `ContentResolver` injection in the
 * consuming repository) because:
 *
 * 1. `ContentResolver` is awkward to mock cleanly in unit tests
 *    (requires a stubbed `Context` or `mockk` of system internals).
 * 2. Hiding the read behind a small interface gives the test a
 *    trivial fake (a `Map<Uri, ByteArray>`-backed implementation) and
 *    keeps repository tests focused on orchestration logic (parallel
 *    uploads → record creation) rather than I/O plumbing.
 *
 * Single method, no streaming variant — full-byte reads are fine for
 * images (typically tens of KB to a few MB). Video uploads, when they
 * land in a future epic, will introduce a streaming overload here
 * alongside the SDK's `ByteReadChannel` overload (tracked upstream as
 * `kikinlex-177`).
 */
interface ImageByteSource {
    /**
     * @throws java.io.FileNotFoundException if the URI cannot be opened
     *   (e.g., revoked permission grant, deleted source). Callers map
     *   this to their own upload-failure error type.
     */
    suspend fun read(uri: Uri): ByteArray
}
