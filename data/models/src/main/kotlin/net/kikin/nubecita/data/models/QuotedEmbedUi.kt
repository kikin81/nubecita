package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * Inner embed slot for a [QuotedPostUi].
 *
 * Sealed interface — exhaustive `when` dispatch in
 * `PostCardQuotedPost`'s embed slot is enforced at compile time. The
 * deliberate absence of a `Record` variant here (compare [EmbedUi])
 * is what makes the recursion bound a structural property of the
 * type, not a runtime guard: a quoted post that itself quotes
 * another post is mapped to [QuotedThreadChip] at the mapper
 * boundary; thereafter the type system carries the bound.
 *
 * Wrapper-type duplication with [EmbedUi] (e.g. [Images] vs
 * `EmbedUi.Images`) is intentional — the underlying payloads
 * (`ImmutableList<ImageUi>`, the Video field-set, the External
 * field-set) are shared, only the wrapper variant is duplicated to
 * keep the recursion bound expressible.
 */
@Immutable
public sealed interface QuotedEmbedUi {
    /**
     * Marker sealed interface — the set of variants that can occupy
     * [RecordWithMedia]'s `media` slot. Implemented by [Images],
     * [Video], and [External] only; matches the lexicon's
     * `RecordWithMediaViewMediaUnion` known members exactly.
     * Mirrors [EmbedUi.MediaEmbed]'s role at the outer level.
     */
    public sealed interface MediaEmbed : QuotedEmbedUi

    /** The quoted post has no embed. */
    public data object Empty : QuotedEmbedUi

    /** 1–4 images. Same payload as [EmbedUi.Images]. */
    public data class Images(
        val items: ImmutableList<ImageUi>,
    ) : MediaEmbed

    /**
     * Bluesky `app.bsky.embed.video#view` inside a quoted post.
     * Same field-set as [EmbedUi.Video] — see that variant's KDoc
     * for the per-field semantics.
     */
    public data class Video(
        val posterUrl: String?,
        val playlistUrl: String,
        val aspectRatio: Float,
        val durationSeconds: Int?,
        val altText: String?,
    ) : MediaEmbed

    /**
     * Bluesky `app.bsky.embed.external#view` inside a quoted post.
     * Same field-set as [EmbedUi.External] — see that variant's
     * KDoc for the precomputed-domain rationale.
     */
    public data class External(
        val uri: String,
        val domain: String,
        val title: String,
        val description: String,
        val thumbUrl: String?,
    ) : MediaEmbed

    /**
     * A GIF external embed inside a quoted post. Same field-set as
     * [EmbedUi.Gif] — see that variant's KDoc.
     */
    public data class Gif(
        val gifUrl: String,
        val thumbUrl: String?,
        val aspectRatio: Float?,
        val alt: String?,
    ) : MediaEmbed

    /**
     * Recursion-bound sentinel — the quoted post itself quotes
     * another post. The render layer renders a "View thread"
     * placeholder; the mapper does NOT recurse into the
     * doubly-quoted post.
     */
    public data object QuotedThreadChip : QuotedEmbedUi

    /**
     * Bluesky `app.bsky.embed.recordWithMedia#view` inside a quoted
     * post. Recursion-bounded: the inner sub-quote is dropped
     * (collapsed to a "View thread" chip at render time) and only
     * the media slot is carried through — same one-level bound as
     * [QuotedThreadChip]. Carrying the full sub-quote would require
     * recursing back into a `QuotedEmbedUi.Record` variant, which is
     * deliberately absent from this sealed interface.
     *
     * [media] is constrained to [MediaEmbed], mirroring how
     * [EmbedUi.RecordWithMedia]'s media slot is constrained to
     * [EmbedUi.MediaEmbed] at the outer level.
     */
    public data class RecordWithMedia(
        val media: MediaEmbed,
    ) : QuotedEmbedUi

    /**
     * Embed type not supported inside a quoted post. The lexicon
     * URI is carried for debug labeling; the render layer reuses
     * `PostCardUnsupportedEmbed` and its friendly-name mapping.
     */
    public data class Unsupported(
        val typeUri: String,
    ) : QuotedEmbedUi
}
