package net.kikin.nubecita.feature.moderation.impl

/**
 * Submits `com.atproto.moderation.createReport` reports for posts and
 * accounts.
 *
 * Wire contract:
 * - `reasonToken` is passed verbatim as the `reasonType` field — any of
 *   the [ReportReasons] constants (granular `tools.ozone.report.defs`
 *   or legacy `com.atproto.moderation.defs`). Unknown reasons round-trip
 *   through state and reach the server; the server is the only
 *   authority on "valid reason".
 * - `details` is truncated to 2000 graphemes (lexicon max) before
 *   being sent; the UI's 300-grapheme cap is enforced elsewhere.
 *   Null or blank `details` is sent as an absent field (omitted from
 *   the wire payload).
 * - The implementation attaches a `modTool` of `name =
 *   "nubecita/android"` on every request. `meta` is intentionally
 *   absent in V1.
 *
 * Both methods return [Result.success] on a 2xx response and
 * [Result.failure] on any thrown exception. The exception is preserved
 * (not unwrapped) so callers can introspect the failure mode.
 */
interface ModerationRepository {
    /**
     * Reports a specific post by its strong reference.
     *
     * @param uri the post's AT URI (plain `String`, wire format).
     * @param cid the post record's CID (plain `String`, wire format).
     * @param reasonToken one of [ReportReasons]'s `REASON_*` constants
     *   (or any other lexicon-known token).
     * @param details optional free-text context, up to 300 graphemes
     *   from the UI (truncated to 2000 at the repository before send).
     *   Null or blank elides the field entirely from the wire payload.
     */
    suspend fun reportPost(
        uri: String,
        cid: String,
        reasonToken: String,
        details: String?,
    ): Result<Unit>

    /**
     * Reports an account by its DID.
     *
     * @param did the account's DID (plain `String`, wire format).
     * @param reasonToken see [reportPost].
     * @param details see [reportPost].
     */
    suspend fun reportAccount(
        did: String,
        reasonToken: String,
        details: String?,
    ): Result<Unit>
}
