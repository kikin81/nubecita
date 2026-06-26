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
 * - [Gallery] — `app.bsky.embed.gallery#view`, multi-image (up to 10);
 *   shares [ImageContainerEmbed] with [Images] (same `ImmutableList<ImageUi>`
 *   payload), rendered through the same carousel
 * - [Video] — `app.bsky.embed.video#view`, HLS-backed video post
 * - [External] — `app.bsky.embed.external#view`, native link-preview card
 * - [Record] — `app.bsky.embed.record#viewRecord`, resolved quoted post
 * - [RecordUnavailable] — `app.bsky.embed.record#view{NotFound,Blocked,Detached}`
 *   plus the open-union `Unknown` fallback; rendered as a "Quoted post
 *   unavailable" chip
 * - [RecordWithMedia] — `app.bsky.embed.recordWithMedia#view`, composition
 *   of a quoted post (resolved or unavailable) and a media embed (images,
 *   video, or external)
 * - [Unsupported] — any embed type outside the current scope; rendered as
 *   a deliberate "Unsupported embed" chip, NOT an error
 *
 * The marker sealed interfaces [RecordOrUnavailable] and [MediaEmbed]
 * exist purely to constrain [RecordWithMedia]'s slots at the type system —
 * they add no behavior, only type-discriminating membership.
 */
@Immutable
public sealed interface EmbedUi {
    /**
     * Marker sealed interface — the set of variants that can occupy
     * [RecordWithMedia]'s `record` slot. Implemented by [Record] and
     * [RecordUnavailable] only; no other variant SHOULD declare this
     * marker. Existing solely to express the recursion-bound role at
     * compile time.
     */
    public sealed interface RecordOrUnavailable : EmbedUi

    /**
     * Marker sealed interface — the set of variants that can occupy
     * [RecordWithMedia]'s `media` slot. Implemented by [Images],
     * [Gallery], [Video], [External], and [Gif] only; matches the
     * lexicon's `RecordWithMediaViewMediaUnion` known members exactly
     * (which carries `gallery#view` as well as `images#view`).
     *
     * Carries the precomputed [contentWarning]: the moderation layer
     * (`:core:feed-mapping`) stamps a cover onto the media slot off the
     * render path, so a `null` here means "render the media normally".
     */
    public sealed interface MediaEmbed : EmbedUi {
        public val contentWarning: MediaContentWarning?
    }

    /**
     * Marker sealed interface — media embeds that are a flat list of
     * [ImageUi]: [Images] (`app.bsky.embed.images`, ≤4) and [Gallery]
     * (`app.bsky.embed.gallery`, ≤10). The two stay distinct concrete
     * types (wire fidelity — a gallery is not an images embed), but a
     * dispatch site that only needs the image list can match
     * `is ImageContainerEmbed` in a single arm instead of duplicating
     * an [Images] and a [Gallery] case. Both render through the same
     * carousel.
     */
    public sealed interface ImageContainerEmbed : MediaEmbed {
        public val items: ImmutableList<ImageUi>
    }

    /** No embed on this post. */
    public data object Empty : EmbedUi

    /** `app.bsky.embed.images`, 1–4 images. */
    public data class Images(
        override val items: ImmutableList<ImageUi>,
        override val contentWarning: MediaContentWarning? = null,
    ) : ImageContainerEmbed

    /**
     * `app.bsky.embed.gallery#view`, a multi-image embed of up to 10
     * images. Shares the [ImageContainerEmbed] payload with [Images]
     * and renders through the same carousel; the distinct type
     * preserves the wire kind so authoring can round-trip it back to
     * `app.bsky.embed.gallery`.
     */
    public data class Gallery(
        override val items: ImmutableList<ImageUi>,
        override val contentWarning: MediaContentWarning? = null,
    ) : ImageContainerEmbed

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
        override val contentWarning: MediaContentWarning? = null,
    ) : MediaEmbed

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
        override val contentWarning: MediaContentWarning? = null,
    ) : MediaEmbed

    /**
     * A GIF posted as an `app.bsky.embed.external` (Klipy/Tenor/Giphy, or any
     * `.gif` URL). Rendered inline as a looping animated image via Coil's
     * `AnimatedImageDecoder` — NOT the video pipeline: the shared single
     * ExoPlayer can only drive one video, so N GIFs in a thread would freeze.
     * Coil gives each GIF an independent drawable; only on-screen ones animate
     * (the LazyColumn item disposes the request when it scrolls off).
     *
     * - [gifUrl] the animated source (the external `uri`, an `image/gif`).
     * - [thumbUrl] the static poster (the external `thumb`), null if absent.
     * - [aspectRatio] width/height when derivable (Klipy/Tenor carry `ww`/`hh`
     *   query params); null when unknown — the render caps height rather than
     *   guess, to avoid a layout jump.
     * - [alt] alt text / title for accessibility; null when the source had none.
     */
    public data class Gif(
        val gifUrl: String,
        val thumbUrl: String?,
        val aspectRatio: Float?,
        val alt: String?,
        override val contentWarning: MediaContentWarning? = null,
    ) : MediaEmbed

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
    ) : RecordOrUnavailable

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
    ) : RecordOrUnavailable {
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
     * Bluesky `app.bsky.embed.recordWithMedia#view`.
     *
     * Composition of a quoted post (or its unavailable stub) plus a
     * media embed (images, video, or external link card). The render
     * layer lays media above the quoted card — matches the official
     * Bluesky Android client; visual grouping comes from adjacency,
     * not a bounding container.
     *
     * Recursion bounds enforced at the type system:
     * - [record] is [RecordOrUnavailable] — never `Images`/`Video`/`External`,
     *   never another `RecordWithMedia`, never `Empty`/`Unsupported`.
     * - [media] is [MediaEmbed] — never any record-shaped variant,
     *   never `RecordWithMedia` itself.
     */
    public data class RecordWithMedia(
        val record: RecordOrUnavailable,
        val media: MediaEmbed,
    ) : EmbedUi

    /**
     * Embed type not supported by the current PostCard build. The lexicon
     * URI is carried for debug labeling.
     */
    public data class Unsupported(
        val typeUri: String,
    ) : EmbedUi
}
