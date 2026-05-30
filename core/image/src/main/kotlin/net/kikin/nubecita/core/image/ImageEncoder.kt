package net.kikin.nubecita.core.image

/**
 * Compresses an image's raw bytes to fit Bluesky's per-blob upload
 * limit before they reach `RepoService.uploadBlob(...)`.
 *
 * Bluesky enforces a 1,000,000-byte (decimal MB) cap on blobs uploaded
 * for `app.bsky.embed.images` and the avatar/banner blobs on
 * `app.bsky.actor.profile`. Modern phone cameras shoot photos
 * comfortably above that — JPEGs from a 12 MP sensor land at 3-6 MB,
 * HEIC at 1-3 MB. Without this seam the caller would upload any
 * normal-sized photo and see a silent upload failure with no in-app
 * remedy.
 *
 * Contract:
 *
 * - Pass-through when [bytes] is already `<= maxBytes`. The contract
 *   is "fit under the cap with the smallest visual-quality loss"; if
 *   the input fits, the bytes are returned verbatim and the MIME type
 *   echoes [sourceMimeType].
 * - When compression is required, the returned [EncodedImage]
 *   carries fresh bytes and a new MIME type — typically `image/webp`
 *   regardless of the input format because lossy WebP yields ~25-35%
 *   smaller output than JPEG at equivalent visual quality, fewer
 *   quality-step iterations to converge under the cap, and Bluesky's
 *   `app.bsky.embed.images` accepts it.
 * - Throws if the input bytes can't be decoded as an image at all
 *   (corrupt file, unsupported format on the platform). The caller
 *   wraps that with its own upload-failure error type.
 *
 * The default impl is `BitmapImageEncoder`, backed by
 * `android.graphics.ImageDecoder` + `Bitmap.compress(WEBP_LOSSY, …)`.
 * No third-party deps — both APIs are in `android.graphics` and
 * available at our minSdk 28 floor.
 *
 * Test seam: this is a small, mockable interface. Repository unit
 * tests inject a fake that just echoes the input bytes; the real
 * `BitmapImageEncoder` requires an Android runtime and is exercised
 * by instrumented tests.
 */
interface ImageEncoder {
    suspend fun encodeForUpload(
        bytes: ByteArray,
        sourceMimeType: String,
        maxBytes: Long = BLUESKY_BLOB_LIMIT_BYTES,
    ): EncodedImage
}

/**
 * Result of an [ImageEncoder.encodeForUpload] call. Either the
 * original bytes (pass-through) or a freshly-encoded variant under
 * the byte cap.
 */
data class EncodedImage(
    val bytes: ByteArray,
    val mimeType: String,
) {
    // data classes with ByteArray fields don't auto-generate value-equal
    // equals/hashCode (the default would compare by reference). Override
    // both so tests can use `assertEquals` against a constructed
    // expected value without surprises.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedImage) return false
        return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
}

/**
 * Hard upload-size cap enforced by Bluesky for blobs referenced by
 * `app.bsky.embed.images` and `app.bsky.actor.profile`. Decimal MB
 * (1,000,000), not binary (1,048,576) — the lexicon uses base-10.
 * `BitmapImageEncoder` targets a slightly-lower threshold internally
 * so quality-step rounding doesn't push the result over the wire-level
 * limit.
 */
const val BLUESKY_BLOB_LIMIT_BYTES: Long = 1_000_000L
