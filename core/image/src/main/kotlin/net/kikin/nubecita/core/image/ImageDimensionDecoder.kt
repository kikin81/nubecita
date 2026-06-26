package net.kikin.nubecita.core.image

/** Intrinsic pixel dimensions of an image. */
data class ImageDimensions(
    val width: Int,
    val height: Int,
)

/**
 * Reads an image's intrinsic pixel dimensions from its raw bytes **without**
 * allocating the full bitmap (a bounds-only decode that reads just the header).
 *
 * Used to populate the per-image `aspectRatio` on `app.bsky.embed.images` /
 * `app.bsky.embed.gallery` records at upload time. Aspect ratio is
 * scale-invariant, so reading the source bytes is correct regardless of any
 * later re-encode/downscale the [ImageEncoder] performs — and decoding here
 * (rather than relying on the encoder, which is pass-through for already-small
 * images) means the dimensions are always available.
 *
 * Test seam: the default impl ([internal.BitmapImageDimensionDecoder]) needs an
 * Android runtime; repository unit tests inject a fake returning canned
 * dimensions.
 */
interface ImageDimensionDecoder {
    /** Returns the image's dimensions, or `null` if the bytes can't be decoded. */
    fun decode(bytes: ByteArray): ImageDimensions?
}
