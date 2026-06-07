package net.kikin.nubecita.core.posting.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.Blob

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
 * - [blobs] — uploaded image blobs (already compressed + uploaded), in composer order.
 *   Empty for a post with no images.
 * - [quote] — the quoted post's strong ref (`uri` + `cid`), or `null` when not quoting.
 */
internal data class ComposerEmbedIntent(
    val blobs: List<Blob>,
    val quote: StrongRef?,
)
