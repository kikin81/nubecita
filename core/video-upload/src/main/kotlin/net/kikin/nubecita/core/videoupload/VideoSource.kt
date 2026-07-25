package net.kikin.nubecita.core.videoupload

import android.net.Uri
import io.github.kikin81.atproto.app.bsky.embed.AspectRatio

/**
 * What the pipeline needs to know about a clip before touching it.
 *
 * Every field is nullable because `MediaMetadataRetriever` returns null for a
 * corrupt or unusual container, and the pipeline has to behave sanely rather
 * than assume the happy path.
 */
internal data class VideoSourceMetadata(
    val widthPx: Int?,
    val heightPx: Int?,
    val rotationDegrees: Int?,
    val durationMs: Long?,
)

/** Reads [VideoSourceMetadata] from a local content URI. */
internal interface VideoSourceProbe {
    suspend fun probe(uri: Uri): VideoSourceMetadata
}

/**
 * Derive the `aspectRatio` for `app.bsky.embed.video`, or `null` when the
 * source dimensions are unusable.
 *
 * **Rotation.** A portrait phone recording is commonly stored as landscape
 * frames plus a 90° rotation flag, so width and height are swapped for 90 and
 * 270. This value is published data that every AT Protocol client renders
 * from — not a local hint — so getting it wrong letterboxes the video
 * everywhere, not just in this app.
 *
 * **Null over a placeholder.** When a dimension is missing or non-positive the
 * result is `null` and the field is omitted from the record. `aspectRatio` is
 * optional in the lexicon, so omission is representable, and a substituted 1:1
 * would be a silent lie every client renders — the same defect the rotation
 * handling exists to prevent. An absent ratio lets each client measure for
 * itself; a wrong one does not.
 */
internal fun deriveAspectRatio(
    widthPx: Int?,
    heightPx: Int?,
    rotationDegrees: Int?,
): AspectRatio? {
    if (widthPx == null || heightPx == null) return null
    if (widthPx <= 0 || heightPx <= 0) return null

    // Normalize so -90 and 450 behave like 270 and 90.
    val rotation = ((rotationDegrees ?: 0) % 360 + 360) % 360
    val swapped = rotation == 90 || rotation == 270

    return if (swapped) {
        AspectRatio(width = heightPx.toLong(), height = widthPx.toLong())
    } else {
        AspectRatio(width = widthPx.toLong(), height = heightPx.toLong())
    }
}
