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
