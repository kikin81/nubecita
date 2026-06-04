package net.kikin.nubecita.data.models

/**
 * The [QuotedPostUi] this embed carries, if any — single source of truth
 * for the question "given an [EmbedUi], where (if anywhere) is a quoted
 * post?"
 *
 * Returns:
 * - The `quotedPost` for [EmbedUi.Record].
 * - The inner `record.quotedPost` for [EmbedUi.RecordWithMedia] when its
 *   `record` slot is a resolved [EmbedUi.Record].
 * - `null` for everything else (including [EmbedUi.RecordWithMedia] whose
 *   `record` is [EmbedUi.RecordUnavailable] — there's no resolved
 *   quoted post in that case).
 *
 * Both feature-feed's `FeedScreen` slot wiring and feature-feed-video's
 * `videoBindingFor` resolver consume this property rather than re-deriving
 * the chained-cast pattern at each call site. When future lexicon
 * evolution introduces another composite embed type that contains a
 * quoted post, this extension property is the single point of update.
 */
public val EmbedUi.quotedRecord: QuotedPostUi?
    get() =
        when (this) {
            is EmbedUi.Record -> quotedPost
            is EmbedUi.RecordWithMedia -> (record as? EmbedUi.Record)?.quotedPost
            else -> null
        }

/**
 * Returns a copy of this media embed with [warning] applied (pass `null` to
 * clear). Exhaustive over the four [EmbedUi.MediaEmbed] variants, so a new
 * media kind becomes a compile error here.
 */
public fun EmbedUi.MediaEmbed.withContentWarning(warning: MediaContentWarning?): EmbedUi.MediaEmbed =
    when (this) {
        is EmbedUi.Images -> copy(contentWarning = warning)
        is EmbedUi.Video -> copy(contentWarning = warning)
        is EmbedUi.External -> copy(contentWarning = warning)
        is EmbedUi.Gif -> copy(contentWarning = warning)
    }

/**
 * Returns a copy of this embed with [warning] applied to its media slot.
 *
 * - A direct media embed (images / video / gif / external) is covered.
 * - A [EmbedUi.RecordWithMedia] covers only its `media` half — the quoted
 *   record carries its own separate labels and is left untouched.
 * - Every non-media embed ([EmbedUi.Empty], [EmbedUi.Record],
 *   [EmbedUi.RecordUnavailable], [EmbedUi.Unsupported]) is returned unchanged.
 *
 * This is the single seam the moderation layer (`:core:feed-mapping`) uses to
 * stamp a precomputed decision onto a post; it does no resolving itself.
 */
public fun EmbedUi.withMediaContentWarning(warning: MediaContentWarning?): EmbedUi =
    when (this) {
        is EmbedUi.MediaEmbed -> withContentWarning(warning)
        is EmbedUi.RecordWithMedia -> copy(media = media.withContentWarning(warning))
        else -> this
    }
