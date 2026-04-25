package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * The embed slot of a Bluesky post.
 *
 * Sealed interface — exhaustive `when` dispatch in [PostCard]'s embed slot
 * is enforced at compile time. New embed types land alongside their
 * respective bd tickets; adding a variant becomes a compile error at every
 * dispatch site, surfacing the work needed.
 *
 * v1 (per the embed-scope decision in
 * `docs/superpowers/specs/2026-04-25-postcard-embed-scope-v1.md`):
 *
 * - [Empty] — post has no embed
 * - [Images] — `app.bsky.embed.images`, 1–4 images
 * - [Unsupported] — any embed type outside the v1 scope (`#external`,
 *   `#record`, `#video`, `#recordWithMedia`); rendered as a deliberate
 *   "Unsupported embed" chip, NOT an error
 *
 * Future variants (one per follow-on bd ticket):
 * - `External` (nubecita-aku)
 * - `Record` (nubecita-6vq)
 * - `Video` (nubecita-xsu)
 * - `RecordWithMedia` (nubecita-umn)
 */
@Immutable
public sealed interface EmbedUi {
    /** No embed on this post. */
    public data object Empty : EmbedUi

    /** 1–4 images. */
    public data class Images(
        val items: ImmutableList<ImageUi>,
    ) : EmbedUi

    /**
     * Embed type not supported by the current PostCard build. The lexicon
     * URI (e.g. `"app.bsky.embed.video"`) is carried for debug labeling.
     */
    public data class Unsupported(
        val typeUri: String,
    ) : EmbedUi
}
