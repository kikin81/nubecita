package net.kikin.nubecita.feature.profile.impl.data

/**
 * Typed failure surface for [ProfileRepository.updateProfile].
 *
 * Carried through `kotlin.Result`'s exception channel — the caller
 * (the EditProfile ViewModel, landing in nubecita-qr1q.5) unwraps with
 * `result.exceptionOrNull() as? ProfileUpdateError` and routes each
 * variant to the matching UI message. Mirrors `:core:posting`'s
 * `ComposerError` pattern: a sealed `RuntimeException` hierarchy so the
 * failure is both a `Throwable` (fits `Result.failure`) and an
 * exhaustively-matchable sum at the call site.
 *
 * The one variant that MUST stay distinct is [SwapConflict]: a stale
 * compare-and-swap means the profile changed elsewhere between our
 * `getRecord` and `putRecord`, and silently overwriting would clobber
 * the concurrent edit. The UI surfaces it as "profile changed
 * elsewhere; reload and retry" rather than a generic error.
 */
internal sealed class ProfileUpdateError(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** No signed-in session, or the session's tokens couldn't refresh. */
    data object Unauthorized : ProfileUpdateError()

    /**
     * `putRecord`'s `swapRecord` CID was stale — the profile record was
     * modified by another client (another device, the official app)
     * after we read it. Surface distinctly so the caller can prompt a
     * reload-and-retry instead of blindly re-writing over the change.
     */
    data object SwapConflict : ProfileUpdateError("profile changed elsewhere; reload and retry")

    /**
     * Uploading a replaced avatar/banner blob failed. Keeps the form
     * populated upstream so the user doesn't lose their edits.
     */
    data class BlobUploadFailed(
        override val cause: Throwable,
    ) : ProfileUpdateError(cause = cause)

    /**
     * Any other failure writing the record (network, rate limit,
     * lexicon validation, an unexpected XRPC error). [cause] carries the
     * underlying throwable for logging; the message is intentionally
     * left to the caller's generic-error copy.
     */
    data class WriteFailed(
        override val cause: Throwable,
    ) : ProfileUpdateError(cause = cause)
}
