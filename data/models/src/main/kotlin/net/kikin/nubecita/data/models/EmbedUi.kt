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
 * - [Record] — `app.bsky.embed.record#viewRecord`, resolved quoted post
 * - [RecordUnavailable] — `app.bsky.embed.record#view{NotFound,Blocked,Detached}`
 *   plus the open-union `Unknown` fallback; rendered as a "Quoted post
 *   unavailable" chip
 * - [Unsupported] — any embed type outside the current scope
 *   (`#recordWithMedia`); rendered as a deliberate "Unsupported embed"
 *   chip, NOT an error
 *
 * Future variants (one per follow-on bd ticket):
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
     * Bluesky `app.bsky.embed.record#viewRecord`.
     *
     * A quoted post that resolved successfully — the lexicon
     * carried the full quoted-post payload (author + record value
     * + optional inner embeds). The render layer renders this at
     * near-parent density via `PostCardQuotedPost`.
     *
     * The [quotedPost] type ([QuotedPostUi]) carries an embed of
     * type [QuotedEmbedUi] (NOT [EmbedUi]) — the recursion bound
     * is enforced at the type system, see [QuotedEmbedUi].
     */
    public data class Record(
        val quotedPost: QuotedPostUi,
    ) : EmbedUi

    /**
     * Bluesky `app.bsky.embed.record#view{NotFound,Blocked,Detached}`
     * and the open-union `Unknown` fallback.
     *
     * The render layer renders a single-stub chip ("Quoted post
     * unavailable") regardless of [reason]. The reason is carried for
     * forward compat (per-variant copy upgrade) and for telemetry /
     * debug consumers; v1 does not vary copy by reason.
     */
    public data class RecordUnavailable(
        val reason: Reason,
    ) : EmbedUi {
        public enum class Reason {
            /** Wire shape: `app.bsky.embed.record#viewNotFound` (post deleted or never existed). */
            NotFound,

            /** Wire shape: `app.bsky.embed.record#viewBlocked` (block relationship). */
            Blocked,

            /** Wire shape: `app.bsky.embed.record#viewDetached` (author detached the quote). */
            Detached,

            /**
             * Open-union `Unknown` member, OR a `viewRecord` whose
             * record `value` failed to decode as a valid
             * `app.bsky.feed.post`, OR whose `createdAt` failed to
             * parse as RFC3339.
             */
            Unknown,
        }
    }

    /**
     * Embed type not supported by the current PostCard build. The lexicon
     * URI (e.g. `"app.bsky.embed.recordWithMedia"`) is carried for debug
     * labeling.
     */
    public data class Unsupported(
        val typeUri: String,
    ) : EmbedUi
}
