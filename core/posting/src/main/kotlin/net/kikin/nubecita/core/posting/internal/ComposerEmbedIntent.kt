package net.kikin.nubecita.core.posting.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtField
import io.github.kikin81.atproto.runtime.Blob
import net.kikin.nubecita.core.image.ImageDimensions

/**
 * One uploaded image plus the per-image metadata the embed record needs:
 * its [alt] text and intrinsic [dimensions] (for `aspectRatio`). [dimensions]
 * is `null` only when the source bytes couldn't be decoded.
 */
internal data class UploadedImage(
    val blob: Blob,
    val alt: String,
    val dimensions: ImageDimensions?,
)

/**
 * The post-upload inputs that determine a post's `embed` field, decoupled from the
 * embed-union variants themselves.
 *
 * The repository populates this after the (parallel) blob-upload phase, then hands it
 * to the single embed resolver. Keeping the embed inputs in one value — rather than a
 * widening list of [DefaultPostingRepository.resolveEmbed] parameters — means a new
 * embed type (e.g. a GIF or an external-link card) is added as one more field here plus
 * one branch in the resolver, with no second composer and no parallel write path.
 *
 * - [images] — uploaded images (already compressed + uploaded), in composer order,
 *   each carrying its blob + alt + dimensions. Empty for a post with no images. The
 *   resolver picks `app.bsky.embed.images` for 1–4 and `app.bsky.embed.gallery` for 5+.
 * - [quote] — the quoted post's strong ref (`uri` + `cid`), or `null` when not quoting.
 * - [external] — a resolved external link card (thumbnail already uploaded), or `null`.
 *   Mutually exclusive with [images] (images win the media slot); may coexist with
 *   [quote] (emitted as `recordWithMedia`).
 */
internal data class ComposerEmbedIntent(
    val images: List<UploadedImage>,
    val quote: StrongRef?,
    val external: PreparedExternal? = null,
)

/**
 * A resolved external link card ready to embed: its display fields plus the
 * already-uploaded [thumb] blob (or [AtField.Missing] when there is no thumbnail
 * or its upload failed best-effort). The blob upload is a suspend op that happens
 * before the synchronous embed resolver, so this carries the finished [thumb].
 */
internal data class PreparedExternal(
    val uri: String,
    val title: String,
    val description: String,
    val thumb: AtField<Blob>,
)
