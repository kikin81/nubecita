package net.kikin.nubecita.feature.profile.impl

/**
 * Boundary validation for actor identifiers arriving from untrusted
 * sources — today, the `{handle}` placeholder extracted by the Profile
 * deep-link matcher (see `feature.profile.impl.di.ProfileDeepLinkModule`).
 *
 * The Profile NavKey's `handle` field carries either an AT Protocol
 * handle (`alice.bsky.social`) or a DID (`did:plc:abc...`) — both forms
 * flow opaquely through to `ProfileRepository.fetchHeader(actor)` and
 * its XRPC call (see kdoc on `feature.profile.api.Profile`). The deep
 * link's path placeholder accepts any non-`/` substring, so without
 * validation an attacker-crafted `https://bsky.app/profile/%2e%2e/x`
 * would smuggle arbitrary content into the actor slot.
 *
 * Per the kf6k.5 security checklist, every actor coming through the
 * matcher MUST pass [isValidActor] before the NavKey is published to
 * the inner back stack. Failed validation returns null from the
 * matcher (filtered upstream — see the `accept` parameter on
 * `uriDeepLinkMatcher`), and `MainActivity.handleIntent` logs the
 * unmatched URI at `Timber.d`.
 *
 * The grammars accepted here are intentionally lenient — false-positive
 * acceptance of a syntactically-valid-but-nonexistent actor surfaces
 * downstream as a `getProfile` XRPC error and the existing Profile
 * error UX takes over. The point of validation is to reject obviously
 * malformed input (control characters, slashes, empty strings) before
 * any network call, NOT to prove the actor exists.
 */
internal fun isValidActor(actor: String?): Boolean {
    if (actor == null) return false
    return isValidHandle(actor) || isValidDid(actor)
}

/**
 * AT Protocol handle grammar — see https://atproto.com/specs/handle.
 *
 * Constraints enforced:
 * - 3..253 characters total (inclusive; the spec caps at 253).
 * - At least two labels separated by `.`.
 * - Each label: 1..63 chars, ASCII alphanumeric plus internal hyphens,
 *   first and last char are alphanumeric (no leading/trailing hyphens).
 * - The final label (TLD-like) starts with a letter.
 *
 * Not enforced (out of scope for syntactic acceptance):
 * - Reserved-name checks (RFC 6761).
 * - DNS resolution / atproto handle-resolution round trip.
 */
private fun isValidHandle(value: String): Boolean {
    if (value.length !in MIN_HANDLE_LENGTH..MAX_HANDLE_LENGTH) return false
    return HANDLE_REGEX.matches(value)
}

/**
 * W3C DID syntax with the atproto subset in mind (`did:plc:...`,
 * `did:web:...`) — see https://www.w3.org/TR/did-core/#did-syntax.
 *
 * Constraints enforced:
 * - Starts with `did:`.
 * - Method is one or more lowercase letters/digits.
 * - Method-specific id is one or more idchars (ALPHA / DIGIT / `.` /
 *   `-` / `_` / `:`), no percent-encoding (we never construct DIDs that
 *   need it).
 */
private fun isValidDid(value: String): Boolean {
    if (value.length !in MIN_DID_LENGTH..MAX_DID_LENGTH) return false
    return DID_REGEX.matches(value)
}

/**
 * Boundary validation for AT Protocol record keys (TID grammar) arriving
 * from untrusted sources — today, the `{rkey}` placeholder extracted by
 * the post-detail deep-link matcher (see `ProfileDeepLinkModule`).
 *
 * `app.bsky.feed.post` records use the TID record-key format
 * (https://atproto.com/specs/tid):
 *
 * - 13 ASCII characters.
 * - Alphabet: base32-sortable, `[2-7a-z]` (digits 0/1/8/9 and
 *   uppercase letters are excluded by spec).
 *
 * Per the kf6k.5 security checklist, every rkey coming through the
 * matcher MUST pass [isValidRkey] before the NavKey is published.
 * Failed validation returns null from the matcher (via the `accept`
 * lambda) and `MainActivity.handleIntent` logs the unmatched URI at
 * `Timber.d`.
 *
 * The grammar is purely syntactic: a syntactically-valid TID that
 * doesn't resolve to a post surfaces downstream as a `getPostThread`
 * `NotFound`, which the existing `PostDetail` error UX handles. The
 * spec's "first-character high-bit-clear" sub-constraint
 * (`[234567a-h]` for the first char) is intentionally NOT enforced —
 * the appview ignores that bit at URL parse time, and we don't want to
 * reject otherwise-valid third-party TIDs over an over-precise check.
 */
internal fun isValidRkey(value: String?): Boolean {
    if (value == null) return false
    return RKEY_TID_REGEX.matches(value)
}

private const val MIN_HANDLE_LENGTH = 3
private const val MAX_HANDLE_LENGTH = 253
private const val MIN_DID_LENGTH = 7
private const val MAX_DID_LENGTH = 2048

private val HANDLE_REGEX =
    Regex(
        "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+" +
            "[a-zA-Z]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$",
    )

private val DID_REGEX = Regex("^did:[a-z0-9]+:[a-zA-Z0-9._:-]+$")

private val RKEY_TID_REGEX = Regex("^[2-7a-z]{13}$")
