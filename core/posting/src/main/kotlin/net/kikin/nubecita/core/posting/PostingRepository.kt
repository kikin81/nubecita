package net.kikin.nubecita.core.posting

import io.github.kikin81.atproto.runtime.AtUri

/**
 * Authoring surface for AT Protocol `app.bsky.feed.post` records.
 *
 * Implementations are responsible for:
 *
 * 1. Reading bytes for each [ComposerAttachment] (typically via
 *    [android.content.ContentResolver]).
 * 2. Uploading those bytes **in parallel** via the SDK's
 *    `RepoService.uploadBlob(...)` and collecting the returned
 *    `Blob` references.
 * 3. Constructing the `app.bsky.feed.post` record with optional
 *    `reply` (when [ReplyRefs] is non-null) and optional `embed.images`
 *    (when attachments are non-empty).
 * 4. Submitting the record via `RepoService.createRecord(...)` and
 *    returning the resulting AT URI.
 *
 * The contract is "all-or-nothing": if any blob upload fails, the
 * record is **not** created and the call returns
 * `Result.failure(ComposerError.UploadFailed(index, cause))`. There is
 * no partial-success state for the composer to recover from.
 *
 * Failures are typed via [ComposerError], a [Throwable] hierarchy
 * carried through `kotlin.Result`'s exception channel — consumers
 * unwrap with `result.exceptionOrNull() as? ComposerError` and
 * `when`-match exhaustively.
 */
interface PostingRepository {
    /**
     * Submit a single `app.bsky.feed.post` record.
     *
     * @param text The record's `text` field. The caller is expected to
     *   have validated against the AT Protocol 300-grapheme limit; the
     *   repository does not re-validate.
     * @param attachments Up to 4 images (lexicon cap). Empty list for a
     *   text-only post. Order is preserved into the record's
     *   `embed.images` array.
     * @param replyTo Non-null in reply mode, carries both the parent
     *   and root [io.github.kikin81.atproto.com.atproto.repo.StrongRef]s.
     *   Null for a top-level new post.
     * @param langs BCP-47 language tags to set on the post's `langs`
     *   field. `null` means "let the repository derive a default from
     *   the device's primary locale" — the typical V1 case. An explicit
     *   non-null list overrides that default and lets a future per-post
     *   override UI plumb caller-chosen tags through. Invalid tags
     *   (those that don't round-trip through `Locale.forLanguageTag`)
     *   are dropped silently. Empty lists — explicit or after dropping
     *   invalid entries — produce a record with no `langs` field at all
     *   (`AtField.Missing`), letting custom feeds fall back to whatever
     *   server-side detection they prefer.
     * @return `Result.success(uri)` on a successful submission carrying
     *   the new record's AT URI; `Result.failure(ComposerError)` on any
     *   typed failure mode.
     */
    suspend fun createPost(
        text: String,
        attachments: List<ComposerAttachment>,
        replyTo: ReplyRefs?,
        langs: List<String>? = null,
    ): Result<AtUri>
}
