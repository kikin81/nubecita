package net.kikin.nubecita.core.image

/**
 * The image formats [ImageSaver] recognises when deciding what MIME type to
 * record against a saved gallery entry.
 *
 * [Unknown] is a real outcome, not an error: the bytes still save, they are
 * just recorded under a generic image type.
 *
 * Animated GIF is deliberately absent. The only surface that can save is the
 * fullscreen media viewer, which resolves its images through
 * `EmbedUi.imageContainer`; `EmbedUi.Gif` is not an `ImageContainerEmbed`, so
 * a GIF post resolves to no images and never reaches a save button. Extend
 * this set at the point a surface that *can* carry a GIF gains a save action.
 */
enum class SavedImageFormat(
    val mimeType: String,
    val fileExtension: String,
) {
    Jpeg("image/jpeg", "jpg"),
    Png("image/png", "png"),
    WebP("image/webp", "webp"),

    /** Bytes matched no known signature; saved under a generic image type. */
    Unknown("image/*", "img"),
}

/**
 * Identifies [bytes]' image format from its leading signature bytes.
 *
 * Sniffing rather than trusting the URL is deliberate. A Bluesky fullsize URL
 * ends in `@jpeg`, but that suffix is a *request parameter* — it states what
 * was asked for, not what came back. The content type recorded in the user's
 * gallery should describe the bytes actually written.
 *
 * Only the first [SIGNATURE_LENGTH] bytes are examined, so this is safe to
 * call on a prefix of a large image.
 */
fun sniffImageFormat(bytes: ByteArray): SavedImageFormat =
    when {
        bytes.startsWith(JPEG_SIGNATURE) -> SavedImageFormat.Jpeg
        bytes.startsWith(PNG_SIGNATURE) -> SavedImageFormat.Png
        // WebP is a RIFF container: "RIFF" <4-byte little-endian length> "WEBP".
        // The length field sits between the two markers, so both halves must be
        // checked and the second is offset past it.
        bytes.startsWith(RIFF_SIGNATURE) && bytes.matchesAt(WEBP_MARKER_OFFSET, WEBP_SIGNATURE) ->
            SavedImageFormat.WebP
        else -> SavedImageFormat.Unknown
    }

/**
 * Builds the display name for a saved gallery entry, e.g.
 * `nubecita_1723678901234.jpg`.
 *
 * [uniqueSuffix] is supplied by the caller (a timestamp in production) rather
 * than read from the clock here, so the function stays pure and testable.
 *
 * This deliberately makes **no** attempt to guarantee uniqueness. `MediaStore`
 * already de-duplicates colliding display names by appending a counter, and a
 * second uniqueness scheme layered on top would only produce uglier filenames
 * while still not being authoritative.
 */
fun savedImageDisplayName(
    format: SavedImageFormat,
    uniqueSuffix: Long,
): String = "${FILENAME_PREFIX}_$uniqueSuffix.${format.fileExtension}"

private const val FILENAME_PREFIX = "nubecita"

/** Bytes needed to classify every format above — the WebP check reaches furthest. */
const val SIGNATURE_LENGTH: Int = 12

private const val WEBP_MARKER_OFFSET = 8

private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

private val PNG_SIGNATURE =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

private val RIFF_SIGNATURE = "RIFF".toByteArray(Charsets.US_ASCII)

private val WEBP_SIGNATURE = "WEBP".toByteArray(Charsets.US_ASCII)

private fun ByteArray.startsWith(signature: ByteArray): Boolean = matchesAt(0, signature)

private fun ByteArray.matchesAt(
    offset: Int,
    signature: ByteArray,
): Boolean {
    if (size < offset + signature.size) return false
    return signature.indices.all { this[offset + it] == signature[it] }
}
