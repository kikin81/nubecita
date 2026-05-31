package net.kikin.nubecita.feature.profile.impl.data

/**
 * A requested change to one of the two image fields on an
 * `app.bsky.actor.profile` record (`avatar` / `banner`) when calling
 * [ProfileRepository.updateProfile].
 *
 * The three states map directly onto the write path's merge semantics:
 *
 *  - [Unchanged] — keep whatever blob ref the fetched record already
 *    holds for this key (or leave the key absent if there was none).
 *    No `uploadBlob` happens; the existing blob ref is reused verbatim.
 *  - [Replaced] — the user picked a new image. The repository runs the
 *    [bytes] through `:core:image`'s `ImageEncoder` (≈1 MB blob cap),
 *    uploads the encoded bytes via `uploadBlob`, and writes the
 *    returned `Blob` ref into the record.
 *  - [Removed] — the user cleared the image. The key is dropped from
 *    the record so the profile no longer references any blob.
 */
internal sealed interface ImageChange {
    /** Keep the existing blob ref (or absence) untouched — no upload. */
    data object Unchanged : ImageChange

    /** Drop the image key from the record entirely. */
    data object Removed : ImageChange

    /**
     * Replace the image with freshly-picked [bytes] of [mimeType]. The
     * bytes are encoded under Bluesky's blob cap and uploaded before
     * the record is written.
     */
    data class Replaced(
        val bytes: ByteArray,
        val mimeType: String,
    ) : ImageChange {
        // data classes with a ByteArray field don't get value-based
        // equals/hashCode for free (the default compares the array by
        // reference). Override both so tests and dirty-state checks can
        // compare two Replaced values by content — mirrors
        // `:core:image`'s EncodedImage.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Replaced) return false
            return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
    }
}
