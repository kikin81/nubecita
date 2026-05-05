package net.kikin.nubecita.core.posting.internal

/**
 * Compresses an attachment's raw bytes to fit Bluesky's per-blob upload
 * limit before they reach `RepoService.uploadBlob(...)`.
 *
 * Bluesky enforces a 1,000,000-byte (decimal MB) cap on blobs uploaded
 * for `app.bsky.embed.images`. Modern phone cameras shoot photos
 * comfortably above that — JPEGs from a 12 MP sensor land at 3-6 MB,
 * HEIC at 1-3 MB. Without this seam the user would tap Post on any
 * normal-sized photo and see a silent `ComposerError.UploadFailed`
 * with no in-app remedy.
 *
 * Contract:
 *
 * - Pass-through when [bytes] is already `<= maxBytes`. The contract
 *   is "fit under the cap with the smallest visual-quality loss"; if
 *   the input fits, the bytes are returned verbatim and the MIME type
 *   echoes [sourceMimeType].
 * - When compression is required, the returned [EncodedAttachment]
 *   carries fresh bytes and a new MIME type — typically `image/webp`
 *   regardless of the input format because lossy WebP yields ~25-35%
 *   smaller output than JPEG at equivalent visual quality, fewer
 *   quality-step iterations to converge under the cap, and Bluesky's
 *   `app.bsky.embed.images` accepts it.
 * - Throws if the input bytes can't be decoded as an image at all
 *   (corrupt file, unsupported format on the platform). The caller
 *   wraps that with `ComposerError.UploadFailed(attachmentIndex, cause)`.
 *
 * The default impl is `BitmapAttachmentEncoder`, backed by
 * `android.graphics.ImageDecoder` + `Bitmap.compress(WEBP_LOSSY, …)`.
 * No third-party deps — both APIs are in `android.graphics` and
 * available at our minSdk 28 floor.
 *
 * Test seam: this is a small, mockable interface. Repository unit
 * tests inject a fake that just echoes the input bytes; the real
 * `BitmapAttachmentEncoder` requires an Android runtime and is
 * exercised by instrumented tests that land alongside the
 * `nubecita-9tw` harness epic.
 */
internal interface AttachmentEncoder {
    suspend fun encodeForUpload(
        bytes: ByteArray,
        sourceMimeType: String,
        maxBytes: Long = BLUESKY_BLOB_LIMIT_BYTES,
    ): EncodedAttachment
}

/**
 * Result of an [AttachmentEncoder.encodeForUpload] call. Either the
 * original bytes (pass-through) or a freshly-encoded variant under
 * the byte cap.
 */
internal data class EncodedAttachment(
    val bytes: ByteArray,
    val mimeType: String,
) {
    // data classes with ByteArray fields don't auto-generate value-equal
    // equals/hashCode (the default would compare by reference). Override
    // both so tests can use `assertEquals` against a constructed
    // expected value without surprises.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedAttachment) return false
        return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
}

/**
 * Hard upload-size cap enforced by Bluesky for blobs referenced by
 * `app.bsky.embed.images`. Decimal MB (1,000,000), not binary
 * (1,048,576) — the lexicon uses base-10. `BitmapAttachmentEncoder`
 * targets a slightly-lower threshold internally so quality-step
 * rounding doesn't push the result over the wire-level limit.
 */
internal const val BLUESKY_BLOB_LIMIT_BYTES: Long = 1_000_000L
