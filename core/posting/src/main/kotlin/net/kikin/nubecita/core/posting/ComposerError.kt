package net.kikin.nubecita.core.posting

/**
 * Typed failure modes surfaced by [PostingRepository.createPost]. Each
 * variant carries enough context for the consumer (the composer
 * `ViewModel`) to map directly to a `ComposerSubmitStatus.Error(cause)`
 * state without re-classifying generic exceptions.
 *
 * Inherits [Throwable] so failures can travel through `kotlin.Result`
 * (the codebase's standard error channel — see [PostThreadRepository]
 * for the same pattern). Consumers unwrap with `result.exceptionOrNull()
 * as? ComposerError` and `when`-match exhaustively.
 */
sealed class ComposerError(
    message: String?,
    cause: Throwable? = null,
) : Throwable(message, cause) {
    /**
     * Network or transport failure — couldn't reach the PDS at all.
     * Distinct from server-side rejection (which surfaces as
     * [RecordCreationFailed]) so the UI can offer a "retry" affordance
     * without re-validating input.
     */
    data class Network(
        override val cause: Throwable,
    ) : ComposerError("Network failure during post submission", cause)

    /**
     * One of the parallel `uploadBlob` calls failed. The `attachmentIndex`
     * identifies which attachment (caller-side ordering) failed so the
     * UI can highlight it. The whole submit aborts on the first such
     * failure — by design, no partial-record creation.
     */
    data class UploadFailed(
        val attachmentIndex: Int,
        override val cause: Throwable,
    ) : ComposerError("Image upload failed for attachment #$attachmentIndex", cause)

    /**
     * The `app.bsky.feed.post` record creation rejected after blobs
     * uploaded successfully. Typically a server-side validation error
     * (rate-limit, content-policy, malformed record).
     */
    data class RecordCreationFailed(
        override val cause: Throwable,
    ) : ComposerError("Server rejected post-record creation", cause)

    /**
     * Reply mode targeted a parent post that the PDS no longer returns
     * (deleted, blocked, account suspended). Distinct from a generic
     * record-creation failure so the UI can show a "this post is no
     * longer available" message rather than a generic error.
     */
    data object ParentNotFound : ComposerError("Reply parent is no longer available")

    /**
     * No active session, or the session's tokens couldn't be refreshed.
     * The UI routes this to the sign-in flow; retrying without re-auth
     * is futile.
     */
    data object Unauthorized : ComposerError("No authenticated session for posting")
}
