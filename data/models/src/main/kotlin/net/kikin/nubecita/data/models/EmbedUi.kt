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
 * Currently supported variants:
 *
 * - [Empty] — post has no embed
 * - [Images] — `app.bsky.embed.images`, 1–4 images
 * - [Video] — `app.bsky.embed.video#view`, HLS-backed video post
 * - [External] — `app.bsky.embed.external#view`, native link-preview card
 * - [Unsupported] — any embed type outside the current scope (`#record`,
 *   `#recordWithMedia`); rendered as a deliberate "Unsupported embed"
 *   chip, NOT an error
 *
 * Future variants (one per follow-on bd ticket):
 * - `Record` (nubecita-6vq)
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
     * Bluesky `app.bsky.embed.video#view`.
     *
     * `posterUrl` is null when the lexicon's optional `thumbnail` field is
     * absent; the render layer falls back to a gradient placeholder.
     *
     * `playlistUrl` is the HLS .m3u8 URL — required by the lexicon `view`
     * form; the mapper falls through to [Unsupported] when absent.
     *
     * `aspectRatio` is `width / height` (e.g. `1.777f` for 16:9). The
     * mapper supplies a 16:9 fallback when the lexicon's optional
     * `aspectRatio` field is absent; the render layer needs a stable
     * measurement before the poster loads, so a non-null contract is
     * worth the small loss of fidelity in the rare missing case.
     *
     * `durationSeconds` is `null` for v1 — the `app.bsky.embed.video#view`
     * lexicon does not currently expose a duration field. Reserved for a
     * future phase that sources duration either from a lexicon evolution
     * or from the HLS manifest after the player loads. The render layer
     * renders the duration chip ONLY when this field is non-null.
     *
     * `altText` is the optional accessibility description.
     */
    public data class Video(
        val posterUrl: String?,
        val playlistUrl: String,
        val aspectRatio: Float,
        val durationSeconds: Int?,
        val altText: String?,
    ) : EmbedUi

    /**
     * Bluesky `app.bsky.embed.external#view`.
     *
     * Native link-preview card. The atproto-kotlin lib already produces
     * fetchable URLs via `Uri.raw` for both `external.uri` and
     * `external.thumb`; no blob-ref → CDN URL construction is required
     * on the nubecita side.
     *
     * - [uri] is the linked URL (full, raw).
     * - [domain] is the precomputed display host (`uri` host with a leading
     *   `www.` stripped; falls back to the full URI when the URI is opaque
     *   or malformed). Computed once at mapping time so the render layer
     *   can display it without per-recomposition `Uri.parse` cost in
     *   scrolling lists (120 Hz target).
     * - [title] / [description] are non-null per the lexicon but Bluesky
     *   permits empty strings — the render layer skips empty rows.
     * - [thumbUrl] is null when the optional `thumb` field is absent;
     *   the render layer omits the thumb section entirely (text-only
     *   card, no placeholder).
     */
    public data class External(
        val uri: String,
        val domain: String,
        val title: String,
        val description: String,
        val thumbUrl: String?,
    ) : EmbedUi

    /**
     * Embed type not supported by the current PostCard build. The lexicon
     * URI (e.g. `"app.bsky.embed.record"`) is carried for debug labeling.
     */
    public data class Unsupported(
        val typeUri: String,
    ) : EmbedUi
}
